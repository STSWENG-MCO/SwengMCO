package com.enil.logez.core.data.seed

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeTransactionRunner
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.history.WorkoutDeleter
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDataSeederTest {
    private fun exercise(id: String, name: String, type: ExerciseType, bwEligible: Boolean = false) = Exercise(
        id = id, name = name, exerciseType = type, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "",
        mediaPath = null, isCustom = false, isBodyweightVolumeEligible = bwEligible, isDeleted = false,
        createdAt = 0L, updatedAt = 0L,
    )

    /** The 7 names DemoDataSeeder looks for, matching the real seed file exactly. */
    private fun seedExercises() = listOf(
        exercise("e-bench", "Bench Press (Barbell)", ExerciseType.WEIGHT_REPS),
        exercise("e-ohp", "Overhead Press (Barbell)", ExerciseType.WEIGHT_REPS),
        exercise("e-plank", "Plank", ExerciseType.DURATION),
        exercise("e-row", "Bent Over Row (Barbell)", ExerciseType.WEIGHT_REPS),
        exercise("e-pullup", "Pull Up", ExerciseType.REPS_ONLY, bwEligible = true),
        exercise("e-squat", "Back Squat (Barbell)", ExerciseType.WEIGHT_REPS),
        exercise("e-deadlift", "Deadlift (Barbell)", ExerciseType.WEIGHT_REPS),
    )

    private fun newSeeder(exerciseRepo: FakeExerciseRepository, workoutRepo: FakeWorkoutRepository): DemoDataSeeder {
        val settingsRepo = FakeSettingsRepository()
        val personalRecordsUpdater = PersonalRecordsUpdater(
            workoutRepo, exerciseRepo, FakePersonalRecordsRepository(), FakeMeasurementRepository(), settingsRepo,
        )
        return DemoDataSeeder(
            exerciseRepo, workoutRepo, personalRecordsUpdater,
            WorkoutDeleter(workoutRepo, personalRecordsUpdater, FakeTransactionRunner()),
            FakeClock(currentMillis = 1_800_000_000_000L),
        )
    }

    @Test
    fun `seed does nothing when the exercise library hasn't been seeded yet`() = runTest {
        val exerciseRepo = FakeExerciseRepository() // empty -- library not loaded
        val workoutRepo = FakeWorkoutRepository()
        val seeder = newSeeder(exerciseRepo, workoutRepo)

        seeder.seed()

        assertTrue(workoutRepo.getCompletedWorkouts().isEmpty())
    }

    @Test
    fun `seed creates a variable number of marked workouts within the periodized range`() = runTest {
        val exerciseRepo = FakeExerciseRepository(seedExercises())
        val workoutRepo = FakeWorkoutRepository()
        val seeder = newSeeder(exerciseRepo, workoutRepo)

        seeder.seed()

        val created = workoutRepo.getCompletedWorkouts()
        // 104 weeks (2 years) x 1-5 sessions/week (3 build weeks ramping 3-5, 1 deload week 1-2,
        // per 4-week cycle) -- 26 cycles x 10..17 sessions/cycle.
        assertTrue("expected 260..442 sessions, got ${created.size}", created.size in 260..442)
        assertTrue(created.all { it.notes == DemoDataSeeder.DEMO_MARKER })
        assertTrue(created.all { it.status == WorkoutStatus.COMPLETED })
    }

    @Test
    fun `session count varies week to week -- periodization, not a flat N-times-a-week grid`() = runTest {
        val exerciseRepo = FakeExerciseRepository(seedExercises())
        val workoutRepo = FakeWorkoutRepository()
        val seeder = newSeeder(exerciseRepo, workoutRepo)
        val nowMillis = 1_800_000_000_000L

        seeder.seed()

        val weeklySessionCounts = workoutRepo.getCompletedWorkouts()
            .groupingBy { (nowMillis - it.startedAt) / (7 * 24 * 60 * 60 * 1000L) } // which week-ago bucket
            .eachCount()

        // A real periodization pattern has more than one distinct weekly session count -- a flat
        // N-per-week grid (what shipped before this fix) would collapse every week to the same value.
        assertTrue("expected varied weekly counts, got $weeklySessionCounts", weeklySessionCounts.values.toSet().size > 1)
        assertTrue("expected at least one deload-shaped week (<=2 sessions)", weeklySessionCounts.values.any { it <= 2 })
        assertTrue("expected at least one build-shaped week (>=4 sessions)", weeklySessionCounts.values.any { it >= 4 })
    }

    @Test
    fun `clear removes only demo-marked workouts, never a real logged one`() = runTest {
        val exerciseRepo = FakeExerciseRepository(seedExercises())
        val realWorkout = WorkoutEntity(
            id = "real-1", routineId = null, title = "My real session", notes = "felt great today",
            status = WorkoutStatus.COMPLETED, startedAt = 0L, endedAt = 1_000L, durationSeconds = 1000,
            createdAt = 0L, updatedAt = 0L,
        )
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(realWorkout))
        val seeder = newSeeder(exerciseRepo, workoutRepo)

        seeder.seed()
        val afterSeed = workoutRepo.getCompletedWorkouts()
        assertTrue(afterSeed.size > 1)
        assertTrue(afterSeed.any { it.id == "real-1" })

        seeder.clear()

        val remaining = workoutRepo.getCompletedWorkouts()
        assertEquals(listOf("real-1"), remaining.map { it.id })
    }

    @Test
    fun `every generated set on a WEIGHT_REPS exercise carries a positive weight and reps`() = runTest {
        val exerciseRepo = FakeExerciseRepository(seedExercises())
        val workoutRepo = FakeWorkoutRepository()
        val seeder = newSeeder(exerciseRepo, workoutRepo)

        seeder.seed()

        val benchSets = workoutRepo.getSetsWithExerciseForCompletedWorkouts().filter { it.exerciseId == "e-bench" }
        assertTrue(benchSets.isNotEmpty())
        benchSets.forEach { row ->
            assertTrue("weight must be positive: ${row.set}", (row.set.weightKg ?: 0.0) > 0.0)
            assertTrue("reps must be positive: ${row.set}", (row.set.reps ?: 0) > 0)
        }
    }
}
