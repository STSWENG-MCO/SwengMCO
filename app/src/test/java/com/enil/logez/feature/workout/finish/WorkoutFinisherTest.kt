package com.enil.logez.feature.workout.finish

import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.HeartRateSample
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeTransactionRunner
import com.enil.logez.fakes.FakeWorkoutHeartRateSampleRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §5.1.8's save transaction end to end. The milestone's own acceptance criterion is
 * "structural-change prompt fires correctly; PRs detected per matrix; summary matches logged data".
 */
class WorkoutFinisherTest {
    private val startedAt = 10_000L
    private val now = 100_000L

    @Test
    fun `saving purges uncompleted sets and marks the workout COMPLETED`() = runTest {
        val f = fixture(
            sets = listOf(
                workoutSet("done", "we1", orderIndex = 0, weightKg = 100.0, reps = 5, isCompleted = true),
                workoutSet("abandoned", "we1", orderIndex = 1, weightKg = 100.0, reps = null, isCompleted = false),
            ),
        )

        f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = startedAt,
            durationSeconds = 3600, updateRoutineValues = false, structureChoice = null,
        )

        val saved = f.workoutRepo.getById("w1")!!
        assertEquals(WorkoutStatus.COMPLETED, saved.status)
        assertEquals(3600, saved.durationSeconds)
        assertEquals(startedAt + 3_600_000L, saved.endedAt)
        val remaining = f.workoutRepo.getSetsWithExerciseForWorkout("w1")
        assertEquals(1, remaining.size)
        assertEquals("done", remaining.single().set.setId)
    }

    @Test
    fun `a future start date is clamped to now`() = runTest {
        val f = fixture()
        val future = now + 999_999L

        f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = future,
            durationSeconds = 60, updateRoutineValues = false, structureChoice = null,
        )

        assertEquals(now, f.workoutRepo.getById("w1")!!.startedAt)
    }

    @Test
    fun `backdating is preserved and drives endedAt`() = runTest {
        val f = fixture()
        val lastWeek = startedAt - 7 * 24 * 3600 * 1000L

        f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = lastWeek,
            durationSeconds = 1800, updateRoutineValues = false, structureChoice = null,
        )

        val saved = f.workoutRepo.getById("w1")!!
        assertEquals(lastWeek, saved.startedAt)
        assertEquals(lastWeek + 1_800_000L, saved.endedAt)
    }

    @Test
    fun `PRs are rebuilt for every exercise the workout touched`() = runTest {
        val f = fixture()

        val result = f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = false, structureChoice = null,
        )

        val heaviest = result.prs.first { it.prType == PrType.HEAVIEST_WEIGHT }
        assertEquals(100.0, heaviest.value, 1e-9)
        assertEquals("ex-1", heaviest.exerciseId)
        assertEquals("w1", heaviest.workoutId)
    }

    @Test
    fun `the toggle on updates the routine's targets in place without churning ids`() = runTest {
        val f = fixture(withRoutine = true)
        val originalSetId = f.routineRepo.getSetsForRoutineExercise("re1").single().id

        f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = true, structureChoice = null,
        )

        val updated = f.routineRepo.getSetsForRoutineExercise("re1").single()
        assertEquals(originalSetId, updated.id) // in-place, not delete-and-reinsert
        assertEquals(100.0, updated.targetWeightKg!!, 1e-9)
        assertEquals(5, updated.targetReps)
    }

    @Test
    fun `the toggle off leaves the routine's targets alone`() = runTest {
        val f = fixture(withRoutine = true)

        f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = false, structureChoice = null,
        )

        val untouched = f.routineRepo.getSetsForRoutineExercise("re1").single()
        assertEquals(60.0, untouched.targetWeightKg!!, 1e-9)
        assertEquals(8, untouched.targetReps)
    }

    @Test
    fun `a rep-range routine target keeps its range even with the toggle on`() = runTest {
        val f = fixture(withRoutine = true, routineRepRange = true)

        f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = true, structureChoice = null,
        )

        val updated = f.routineRepo.getSetsForRoutineExercise("re1").single()
        assertEquals(100.0, updated.targetWeightKg!!, 1e-9) // weight still refreshes
        assertNull(updated.targetReps)
        assertEquals(6, updated.targetRepRangeMin)
        assertEquals(8, updated.targetRepRangeMax)
    }

    @Test
    fun `choosing Update Routine rewrites the routine to match the session`() = runTest {
        val f = fixture(withRoutine = true)

        f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = false,
            structureChoice = RoutineStructureChoice.UPDATE_ROUTINE,
        )

        val exercises = f.routineRepo.getExercisesForRoutine("r1")
        assertEquals(1, exercises.size)
        assertEquals("ex-1", exercises.single().exerciseId)
        val sets = f.routineRepo.getSetsForRoutineExercise(exercises.single().id)
        assertEquals(1, sets.size)
        assertEquals(100.0, sets.single().targetWeightKg!!, 1e-9)
    }

    @Test
    fun `choosing Keep Original leaves the routine's structure intact`() = runTest {
        val f = fixture(withRoutine = true)
        val originalExerciseId = f.routineRepo.getExercisesForRoutine("r1").single().id

        f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = false,
            structureChoice = RoutineStructureChoice.KEEP_ORIGINAL,
        )

        assertEquals(originalExerciseId, f.routineRepo.getExercisesForRoutine("r1").single().id)
    }

    @Test
    fun `a blank title falls back to the existing one rather than saving an empty workout name`() = runTest {
        val f = fixture()

        f.finisher.finish(
            workout = f.workout, title = "   ", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = false, structureChoice = null,
        )

        assertEquals("Original Title", f.workoutRepo.getById("w1")!!.title)
    }

    @Test
    fun `a workout with no routine saves cleanly even with the toggle on`() = runTest {
        val f = fixture(withRoutine = false)

        f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = true, structureChoice = null,
        )

        assertTrue(f.workoutRepo.getById("w1")!!.status == WorkoutStatus.COMPLETED)
    }

    // --- M21f: heart-rate samples saved outside the transaction, best-effort ---

    @Test
    fun `finish saves Health Connect heart-rate samples for the workout's own time window`() = runTest {
        val samples = listOf(
            HeartRateSample(time = java.time.Instant.ofEpochMilli(startedAt + 10_000L), bpm = 128L),
            HeartRateSample(time = java.time.Instant.ofEpochMilli(startedAt + 20_000L), bpm = 134L),
        )
        val healthMetricsSource = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            heartRateSamples = samples,
        )
        val f = fixture(healthMetricsSource = healthMetricsSource)

        f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = false, structureChoice = null,
        )

        val saved = f.heartRateRepo.getForWorkout("w1")
        assertEquals(2, saved.size)
        assertEquals(128L, saved[0].bpm)
        assertEquals(134L, saved[1].bpm)
        assertTrue(saved.all { it.workoutId == "w1" })
    }

    @Test
    fun `finish saves nothing when Health Connect is unavailable -- never blocks the save`() = runTest {
        val f = fixture(healthMetricsSource = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Unavailable))

        f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = false, structureChoice = null,
        )

        assertTrue(f.heartRateRepo.getForWorkout("w1").isEmpty())
        assertTrue(f.workoutRepo.getById("w1")!!.status == WorkoutStatus.COMPLETED)
    }

    @Test
    fun `finish saves nothing when Health Connect is available but the permission isn't granted yet`() = runTest {
        val healthMetricsSource = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = false,
            heartRateSamples = listOf(HeartRateSample(time = java.time.Instant.ofEpochMilli(startedAt + 5_000L), bpm = 100L)),
        )
        val f = fixture(healthMetricsSource = healthMetricsSource)

        f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = false, structureChoice = null,
        )

        assertTrue(f.heartRateRepo.getForWorkout("w1").isEmpty())
    }

    @Test
    fun `finish still reports success when the post-save heart-rate sync throws`() = runTest {
        val healthMetricsSource = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            throwOnReadHeartRateSamples = IllegalStateException("Health Connect IPC hiccup"),
        )
        val f = fixture(healthMetricsSource = healthMetricsSource)

        // Must not throw: a best-effort post-save side effect failing must never surface as a
        // failed Finish for a workout that already saved successfully.
        val result = f.finisher.finish(
            workout = f.workout, title = "Push Day", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = false, structureChoice = null,
        )

        assertEquals("w1", result.workoutId)
        assertTrue(f.workoutRepo.getById("w1")!!.status == WorkoutStatus.COMPLETED)
        assertTrue(f.heartRateRepo.getForWorkout("w1").isEmpty())
    }

    // --- fixture ---

    private class Fixture(
        val finisher: WorkoutFinisher,
        val workoutRepo: FakeWorkoutRepository,
        val routineRepo: FakeRoutineRepository,
        val recordsRepo: FakePersonalRecordsRepository,
        val heartRateRepo: FakeWorkoutHeartRateSampleRepository,
        val workout: WorkoutEntity,
    )

    private fun fixture(
        withRoutine: Boolean = false,
        routineRepRange: Boolean = false,
        sets: List<WorkoutSetEntity> = listOf(workoutSet("s1", "we1", 0, 100.0, 5, isCompleted = true)),
        healthMetricsSource: FakeHealthMetricsSource = FakeHealthMetricsSource(),
        heartRateRepo: FakeWorkoutHeartRateSampleRepository = FakeWorkoutHeartRateSampleRepository(),
    ): Fixture {
        val routineId = if (withRoutine) "r1" else null
        val workout = WorkoutEntity(
            id = "w1", routineId = routineId, title = "Original Title", notes = null,
            status = WorkoutStatus.IN_PROGRESS, startedAt = startedAt, endedAt = null,
            durationSeconds = 0, createdAt = startedAt, updatedAt = startedAt,
        )
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout),
            exercises = listOf(
                WorkoutExerciseEntity(
                    id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0,
                    supersetGroup = null, restTimerSeconds = null, notes = null,
                ),
            ),
            sets = sets,
        )
        val routineRepo = if (withRoutine) {
            FakeRoutineRepository(
                routines = listOf(RoutineEntity(id = "r1", folderId = null, name = "Push", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0)),
                exercises = listOf(RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
                sets = listOf(
                    RoutineSetEntity(
                        id = "rs1", routineExerciseId = "re1", orderIndex = 0, setType = SetType.NORMAL,
                        targetWeightKg = 60.0,
                        targetReps = if (routineRepRange) null else 8,
                        targetRepRangeMin = if (routineRepRange) 6 else null,
                        targetRepRangeMax = if (routineRepRange) 8 else null,
                        targetDurationSeconds = null, targetDistanceMeters = null,
                    ),
                ),
            )
        } else {
            FakeRoutineRepository()
        }
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val recordsRepo = FakePersonalRecordsRepository()
        val clock = FakeClock(currentMillis = now)
        val updater = PersonalRecordsUpdater(
            workoutRepo, exerciseRepo, recordsRepo, FakeMeasurementRepository(), FakeSettingsRepository(),
        )
        return Fixture(
            finisher = WorkoutFinisher(workoutRepo, routineRepo, updater, FakeTransactionRunner(), healthMetricsSource, heartRateRepo, clock),
            workoutRepo = workoutRepo, routineRepo = routineRepo, recordsRepo = recordsRepo, heartRateRepo = heartRateRepo, workout = workout,
        )
    }

    private fun exercise(id: String, name: String) = Exercise(
        id = id, name = name, exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )
}

private fun workoutSet(
    id: String,
    workoutExerciseId: String,
    orderIndex: Int,
    weightKg: Double?,
    reps: Int?,
    isCompleted: Boolean,
) = WorkoutSetEntity(
    id = id, workoutExerciseId = workoutExerciseId, orderIndex = orderIndex, setType = SetType.NORMAL,
    weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null, rpe = null,
    customMetric = null, isCompleted = isCompleted, completedAt = if (isCompleted) 1L else null,
)
