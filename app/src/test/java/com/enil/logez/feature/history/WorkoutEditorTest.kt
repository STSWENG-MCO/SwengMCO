package com.enil.logez.feature.history

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
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeTransactionRunner
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** PHASE2_PLAN.md §5.1.10's save transaction for an edited past workout. */
class WorkoutEditorTest {
    private val startedAt = 10_000L
    private val now = 1_000_000L

    @Test
    fun `the workout stays COMPLETED and takes the edited date and duration`() = runTest {
        val f = fixture()

        f.editor.save(
            workout = f.workout,
            startedAt = 5_000L,
            durationSeconds = 3600,
            exercises = listOf(exercise("we1")),
            sets = listOf(set("s1", "we1", 0, 100.0, 5)),
        )

        val saved = f.workoutRepo.getById("w1")!!
        assertEquals(WorkoutStatus.COMPLETED, saved.status)
        assertEquals(5_000L, saved.startedAt)
        assertEquals(3600, saved.durationSeconds)
        assertEquals(5_000L + 3_600_000L, saved.endedAt)
    }

    @Test
    fun `a future date is clamped to now, exactly as the finish flow clamps it`() = runTest {
        val f = fixture()

        f.editor.save(
            workout = f.workout, startedAt = now + 999_999L, durationSeconds = 60,
            exercises = listOf(exercise("we1")), sets = listOf(set("s1", "we1", 0, 100.0, 5)),
        )

        assertEquals(now, f.workoutRepo.getById("w1")!!.startedAt)
    }

    @Test
    fun `uncompleted sets are dropped and an exercise left empty goes with them`() = runTest {
        val f = fixture()

        f.editor.save(
            workout = f.workout, startedAt = startedAt, durationSeconds = 60,
            exercises = listOf(exercise("we1"), exercise("we2", orderIndex = 1)),
            sets = listOf(
                set("s1", "we1", 0, 100.0, 5),
                set("s2", "we1", 1, 100.0, 5, isCompleted = false),
                set("s3", "we2", 0, 50.0, 10, isCompleted = false), // we2's only set
            ),
        )

        val exercises = f.workoutRepo.getExercisesForWorkout("w1")
        assertEquals(listOf("we1"), exercises.map { it.id })
        assertEquals(listOf("s1"), f.workoutRepo.getSetsForWorkoutExercise("we1").map { it.id })
    }

    @Test
    fun `removing an exercise re-awards the record it was holding to the next-best session`() = runTest {
        // The union rule (§5.1.10): rebuilding only the exercises still present would leave the
        // removed exercise's record standing with no sets behind it.
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(
                workout("w-older", startedAt = 1_000L),
                workout("w1", startedAt = startedAt),
            ),
            exercises = listOf(
                exercise("we-older", workoutId = "w-older"),
                exercise("we1"),
            ),
            sets = listOf(
                set("s-older", "we-older", 0, 90.0, 5),
                set("s1", "we1", 0, 120.0, 5),
            ),
        )
        val recordsRepo = FakePersonalRecordsRepository()
        val f = fixture(workoutRepo, recordsRepo)
        f.updater.rebuildForExercises(setOf("ex-1"), "unused", includeWarmupsInStats = false)
        assertEquals(120.0, recordsRepo.getForExercise("ex-1").first { it.prType == PrType.HEAVIEST_WEIGHT }.value, 1e-9)

        // The edit drops the only exercise the workout had, replacing it with a different one.
        f.editor.save(
            workout = workoutRepo.getById("w1")!!, startedAt = startedAt, durationSeconds = 60,
            exercises = listOf(exercise("we-new", exerciseId = "ex-2")),
            sets = listOf(set("s-new", "we-new", 0, 40.0, 10)),
        )

        val remaining = recordsRepo.getForExercise("ex-1").first { it.prType == PrType.HEAVIEST_WEIGHT }
        assertEquals("the removed exercise's record must fall back to the older session", 90.0, remaining.value, 1e-9)
        assertEquals("w-older", remaining.workoutId)
        // And the newly-added exercise earns its own.
        assertTrue(recordsRepo.getForExercise("ex-2").isNotEmpty())
    }

    @Test
    fun `dropping a superset partner does not leave the survivor alone in its group`() = runTest {
        val f = fixture()

        f.editor.save(
            workout = f.workout, startedAt = startedAt, durationSeconds = 60,
            exercises = listOf(
                exercise("we1", supersetGroup = 1),
                exercise("we2", orderIndex = 1, supersetGroup = 1),
            ),
            sets = listOf(
                set("s1", "we1", 0, 100.0, 5),
                set("s2", "we2", 0, 50.0, 10, isCompleted = false), // purged, taking we2 with it
            ),
        )

        val survivor = f.workoutRepo.getExercisesForWorkout("w1").single()
        assertNull("a one-member superset group is not a valid state", survivor.supersetGroup)
    }

    @Test
    fun `set and exercise order indices are renumbered contiguously after a purge`() = runTest {
        val f = fixture()

        f.editor.save(
            workout = f.workout, startedAt = startedAt, durationSeconds = 60,
            exercises = listOf(exercise("we1"), exercise("we2", orderIndex = 1), exercise("we3", orderIndex = 2)),
            sets = listOf(
                set("s1", "we1", 0, 100.0, 5),
                set("s-gone", "we2", 0, 50.0, 5, isCompleted = false), // we2 purged entirely
                set("s2", "we3", 0, 60.0, 5),
                set("s3", "we3", 2, 60.0, 5), // deliberately non-contiguous input
            ),
        )

        assertEquals(listOf(0, 1), f.workoutRepo.getExercisesForWorkout("w1").map { it.orderIndex })
        assertEquals(listOf(0, 1), f.workoutRepo.getSetsForWorkoutExercise("we3").map { it.orderIndex })
    }

    @Test
    fun `the whole edit runs inside a single transaction`() = runTest {
        val runner = FakeTransactionRunner()
        val f = fixture(runner = runner)

        f.editor.save(
            workout = f.workout, startedAt = startedAt, durationSeconds = 60,
            exercises = listOf(exercise("we1")), sets = listOf(set("s1", "we1", 0, 100.0, 5)),
        )

        assertEquals(1, runner.transactionCount)
    }

    @Test
    fun `a failed transaction leaves the workout untouched`() = runTest {
        val f = fixture(runner = FakeTransactionRunner(failNextCalls = 1))

        runCatching {
            f.editor.save(
                workout = f.workout, startedAt = 5_000L, durationSeconds = 9999,
                exercises = listOf(exercise("we1")), sets = listOf(set("s1", "we1", 0, 100.0, 5)),
            )
        }

        val untouched = f.workoutRepo.getById("w1")!!
        assertEquals(startedAt, untouched.startedAt)
        assertEquals(0, untouched.durationSeconds)
    }

    // --- fixture ---

    private class Fixture(
        val editor: WorkoutEditor,
        val updater: PersonalRecordsUpdater,
        val workoutRepo: FakeWorkoutRepository,
        val workout: WorkoutEntity,
    )

    private suspend fun fixture(
        workoutRepo: FakeWorkoutRepository = FakeWorkoutRepository(
            workouts = listOf(workout("w1", startedAt = startedAt)),
            exercises = listOf(exercise("we1")),
            sets = listOf(set("s1", "we1", 0, 100.0, 5)),
        ),
        recordsRepo: FakePersonalRecordsRepository = FakePersonalRecordsRepository(),
        runner: FakeTransactionRunner = FakeTransactionRunner(),
    ): Fixture {
        val exerciseRepo = FakeExerciseRepository(listOf(exerciseModel("ex-1"), exerciseModel("ex-2")))
        val updater = PersonalRecordsUpdater(workoutRepo, exerciseRepo, recordsRepo, FakeMeasurementRepository(), FakeSettingsRepository())
        return Fixture(
            editor = WorkoutEditor(workoutRepo, updater, runner, FakeClock(currentMillis = now)),
            updater = updater,
            workoutRepo = workoutRepo,
            workout = workoutRepo.getById("w1")!!,
        )
    }

    private fun workout(id: String, startedAt: Long) = WorkoutEntity(
        id = id, routineId = null, title = "Session", notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = startedAt, endedAt = startedAt + 1000L, durationSeconds = 0, createdAt = startedAt, updatedAt = startedAt,
    )

    private fun exercise(
        id: String,
        exerciseId: String = "ex-1",
        orderIndex: Int = 0,
        supersetGroup: Int? = null,
        workoutId: String = "w1",
    ) = WorkoutExerciseEntity(
        id = id, workoutId = workoutId, exerciseId = exerciseId, orderIndex = orderIndex,
        supersetGroup = supersetGroup, restTimerSeconds = null, notes = null,
    )

    private fun set(
        id: String,
        workoutExerciseId: String,
        orderIndex: Int,
        weightKg: Double,
        reps: Int,
        isCompleted: Boolean = true,
    ) = WorkoutSetEntity(
        id = id, workoutExerciseId = workoutExerciseId, orderIndex = orderIndex, setType = SetType.NORMAL,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null, rpe = null,
        customMetric = null, isCompleted = isCompleted, completedAt = if (isCompleted) 1L else null,
    )

    private fun exerciseModel(id: String) = Exercise(
        id = id, name = "Ex $id", exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )
}
