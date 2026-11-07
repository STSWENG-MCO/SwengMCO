package com.enil.logez.feature.workout.finish

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.StatSet
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
import com.enil.logez.fakes.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** §8.4 point 1's live banner, and point 2's "never on an exercise's first-ever log" rule. */
class LivePrDetectorTest {
    @Test
    fun `a warm-up-only history still counts as the first-ever log`() = runTest {
        // Regression: the gate tested raw history rows, but the stat query filters only on
        // COMPLETED status — so a single past warm-up made history look non-empty while the PR
        // rebuild had (correctly) stored nothing. Every candidate then beat -infinity and the
        // user's first working set flashed a triple-PR banner.
        val detector = detector(
            history = listOf(statSet("old-warmup", SetType.WARMUP, 40.0, 10)),
            cached = emptyList(),
        )

        val banners = detector.detect("w1", "we1", "s1", includeWarmupsInStats = false)

        assertEquals(emptyList<PrType>(), banners)
    }

    @Test
    fun `that same warm-up history does count once warm-ups are included in stats`() = runTest {
        // With the setting on, the warm-up is real history — so this is not a first-ever log, and
        // a heavier working set legitimately beats it.
        val detector = detector(
            history = listOf(statSet("old-warmup", SetType.WARMUP, 40.0, 10)),
            cached = emptyList(),
        )

        val banners = detector.detect("w1", "we1", "s1", includeWarmupsInStats = true)

        assertTrue(PrType.HEAVIEST_WEIGHT in banners)
    }

    @Test
    fun `a genuine first-ever log never banners`() = runTest {
        val detector = detector(history = emptyList(), cached = emptyList())

        assertEquals(emptyList<PrType>(), detector.detect("w1", "we1", "s1", includeWarmupsInStats = false))
    }

    // --- fixture ---

    private fun detector(history: List<StatSet>, cached: List<com.enil.logez.core.data.entity.PersonalRecordEntity>): LivePrDetector {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(
                WorkoutEntity(
                    id = "w1", routineId = null, title = "Push", notes = null,
                    status = WorkoutStatus.IN_PROGRESS, startedAt = 5_000L, endedAt = null,
                    durationSeconds = 0, createdAt = 5_000L, updatedAt = 5_000L,
                ),
            ),
            exercises = listOf(
                WorkoutExerciseEntity(
                    id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0,
                    supersetGroup = null, restTimerSeconds = null, notes = null,
                ),
            ),
            // The set just completed: a 60kg working set.
            sets = listOf(
                WorkoutSetEntity(
                    id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL,
                    weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null,
                    customMetric = null, isCompleted = true, completedAt = 1L,
                ),
            ),
            statSetsByExercise = mapOf("ex-1" to history),
        )
        return LivePrDetector(
            workoutRepo,
            FakeExerciseRepository(listOf(exercise())),
            FakePersonalRecordsRepository(cached),
            FakeMeasurementRepository(),
            FakeClock(currentMillis = 10_000L),
        )
    }

    private fun statSet(id: String, setType: SetType, weightKg: Double, reps: Int) = StatSet(
        setId = id, workoutId = "w-old", workoutStartedAt = 1_000L, orderIndex = 0, setType = setType,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null,
        customMetric = null, isCompleted = true,
    )

    private fun exercise() = Exercise(
        id = "ex-1", name = "Cable Fly", exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = MuscleGroup.CHEST, secondaryMuscleGroups = emptyList(),
        equipment = Equipment.NONE, instructions = "", mediaPath = null, isCustom = false,
        isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )
}
