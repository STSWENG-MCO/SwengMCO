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

/**
 * PHASE2_PLAN.md §5.2 "Delete Workout": "hard delete cascades `workout_exercises`/`workout_sets`,
 * then PR rebuild for every exercise that appeared in it."
 */
class WorkoutDeleterTest {
    @Test
    fun `deleting the workout that held a record hands it to the next-best remaining session`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(
                completedWorkout("w-best", startedAt = 2_000L),
                completedWorkout("w-second", startedAt = 1_000L),
            ),
            exercises = listOf(
                workoutExercise("we-best", "w-best"),
                workoutExercise("we-second", "w-second"),
            ),
            sets = listOf(
                set("s-best", "we-best", weightKg = 120.0, reps = 5),
                set("s-second", "we-second", weightKg = 100.0, reps = 5),
            ),
        )
        val recordsRepo = FakePersonalRecordsRepository()
        val (deleter, updater) = deleter(workoutRepo, recordsRepo)

        // Seed the cache the way a real save would have — w-best currently holds the record.
        updater.rebuildForExercises(setOf("ex-1"), "unused", includeWarmupsInStats = false)
        assertEquals(120.0, recordsRepo.getForExercise("ex-1").first { it.prType == PrType.HEAVIEST_WEIGHT }.value, 1e-9)

        deleter.delete("w-best")

        val remaining = recordsRepo.getForExercise("ex-1").first { it.prType == PrType.HEAVIEST_WEIGHT }
        assertEquals(100.0, remaining.value, 1e-9)
        assertEquals("w-second", remaining.workoutId)
    }

    @Test
    fun `deleting a workout that held no record leaves other exercises' records untouched`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(completedWorkout("w-empty", startedAt = 1_000L)),
            exercises = listOf(workoutExercise("we-empty", "w-empty", exerciseId = "ex-2")),
            sets = listOf(set("s-empty", "we-empty", weightKg = 20.0, reps = 10)),
        )
        val recordsRepo = FakePersonalRecordsRepository()
        val (deleter, updater) = deleter(workoutRepo, recordsRepo)
        updater.rebuildForExercises(setOf("ex-2"), "unused", includeWarmupsInStats = false)
        assertTrue(recordsRepo.getForExercise("ex-2").isNotEmpty())

        deleter.delete("w-empty")

        assertTrue("the deleted workout's own record must be gone once nothing remains to hold it", recordsRepo.getForExercise("ex-2").isEmpty())
    }

    @Test
    fun `the whole delete runs inside one transaction`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(completedWorkout("w1", startedAt = 1_000L)),
            exercises = listOf(workoutExercise("we1", "w1")),
            sets = listOf(set("s1", "we1", weightKg = 50.0, reps = 5)),
        )
        val runner = FakeTransactionRunner()
        val (deleter, _) = deleter(workoutRepo, FakePersonalRecordsRepository(), runner)

        deleter.delete("w1")

        assertEquals(1, runner.transactionCount)
    }

    @Test
    fun `deleting a workout with no sets at all does not throw`() = runTest {
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(completedWorkout("w-bare", startedAt = 1_000L)))
        val (deleter, _) = deleter(workoutRepo, FakePersonalRecordsRepository())

        deleter.delete("w-bare")

        assertNull(workoutRepo.getById("w-bare"))
    }

    // --- fixture ---

    private fun deleter(
        workoutRepo: FakeWorkoutRepository,
        recordsRepo: FakePersonalRecordsRepository,
        runner: FakeTransactionRunner = FakeTransactionRunner(),
    ): Pair<WorkoutDeleter, PersonalRecordsUpdater> {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1"), exercise("ex-2")))
        val updater = PersonalRecordsUpdater(workoutRepo, exerciseRepo, recordsRepo, FakeMeasurementRepository(), FakeSettingsRepository())
        return WorkoutDeleter(workoutRepo, updater, runner) to updater
    }

    private fun completedWorkout(id: String, startedAt: Long) = WorkoutEntity(
        id = id, routineId = null, title = "Session", notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = startedAt, endedAt = startedAt + 1000L, durationSeconds = 1000, createdAt = startedAt, updatedAt = startedAt,
    )

    private fun workoutExercise(id: String, workoutId: String, exerciseId: String = "ex-1") = WorkoutExerciseEntity(
        id = id, workoutId = workoutId, exerciseId = exerciseId, orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null,
    )

    private fun set(id: String, workoutExerciseId: String, weightKg: Double, reps: Int) = WorkoutSetEntity(
        id = id, workoutExerciseId = workoutExerciseId, orderIndex = 0, setType = SetType.NORMAL,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null, rpe = null,
        customMetric = null, isCompleted = true, completedAt = 1L,
    )

    private fun exercise(id: String) = Exercise(
        id = id, name = "Ex $id", exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )
}
