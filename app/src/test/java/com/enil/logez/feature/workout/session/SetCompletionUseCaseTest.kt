package com.enil.logez.feature.workout.session

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetCompletionUseCaseTest {
    private fun aWorkout(id: String = "w1") = WorkoutEntity(
        id = id, routineId = null, title = "Push Day", notes = null, status = WorkoutStatus.IN_PROGRESS,
        startedAt = 0L, endedAt = null, durationSeconds = 0, createdAt = 0L, updatedAt = 0L,
    )

    private fun aSet(id: String, orderIndex: Int, type: SetType = SetType.NORMAL, reps: Int? = 8) = WorkoutSetEntity(
        id = id, workoutExerciseId = "we1", orderIndex = orderIndex, setType = type, weightKg = 60.0, reps = reps,
        durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null,
    )

    private suspend fun newUseCase(
        workoutRepo: FakeWorkoutRepository,
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        clock: FakeClock = FakeClock(currentMillis = 5_000L),
    ): Pair<SetCompletionUseCase, WorkoutSessionController> {
        val controller = WorkoutSessionController(FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        controller.startSession("w1")
        return SetCompletionUseCase(workoutRepo, settingsRepo, controller, clock) to controller
    }

    @Test
    fun `completing a set starts a rest timer using the exercise's explicit restTimerSeconds`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(aWorkout()),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = 45, notes = null)),
            sets = listOf(aSet("s1", 0)),
        )
        val (useCase, controller) = newUseCase(workoutRepo)

        val accepted = useCase.completeSet("w1", "we1", "s1")

        assertTrue(accepted)
        assertTrue(workoutRepo.getSetsForWorkoutExercise("we1").single().isCompleted)
        assertEquals(45, ((controller.state.value.restDeadlineElapsedRealtimeMillis ?: 0L) / 1000).toInt())
    }

    @Test
    fun `null restTimerSeconds falls back to the Default Rest Timer setting`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(aWorkout()),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(aSet("s1", 0)),
        )
        val settingsRepo = FakeSettingsRepository(UserSettings(defaultRestTimerSeconds = 120))
        val (useCase, controller) = newUseCase(workoutRepo, settingsRepo)

        useCase.completeSet("w1", "we1", "s1")

        assertEquals(120, ((controller.state.value.restDeadlineElapsedRealtimeMillis ?: 0L) / 1000).toInt())
    }

    @Test
    fun `explicit restTimerSeconds of zero (off) means no rest timer starts at all`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(aWorkout()),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = 0, notes = null)),
            sets = listOf(aSet("s1", 0)),
        )
        val (useCase, controller) = newUseCase(workoutRepo)

        useCase.completeSet("w1", "we1", "s1")

        assertNull(controller.state.value.restDeadlineElapsedRealtimeMillis)
    }

    @Test
    fun `dropset exception -- no rest timer starts when the next set is a DROPSET`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(aWorkout()),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = 90, notes = null)),
            sets = listOf(aSet("s1", 0), aSet("s2", 1, type = SetType.DROPSET)),
        )
        val (useCase, controller) = newUseCase(workoutRepo)

        useCase.completeSet("w1", "we1", "s1")

        assertTrue(workoutRepo.getSetsForWorkoutExercise("we1").first { it.id == "s1" }.isCompleted)
        assertNull(controller.state.value.restDeadlineElapsedRealtimeMillis) // dropset exception: no timer
    }

    @Test
    fun `completing the last set (no next set) still starts a rest timer -- only a following DROPSET suppresses it`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(aWorkout()),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = 90, notes = null)),
            sets = listOf(aSet("s1", 0)),
        )
        val (useCase, controller) = newUseCase(workoutRepo)

        useCase.completeSet("w1", "we1", "s1")

        assertEquals(90, ((controller.state.value.restDeadlineElapsedRealtimeMillis ?: 0L) / 1000).toInt())
    }

    @Test
    fun `rejects a FAILURE set with zero reps -- does not complete it and starts no timer`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(aWorkout()),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = 90, notes = null)),
            sets = listOf(aSet("s1", 0, type = SetType.FAILURE, reps = 0)),
        )
        val (useCase, controller) = newUseCase(workoutRepo)

        val accepted = useCase.completeSet("w1", "we1", "s1")

        assertFalse(accepted)
        assertFalse(workoutRepo.getSetsForWorkoutExercise("we1").single().isCompleted)
        assertNull(controller.state.value.restDeadlineElapsedRealtimeMillis)
    }

    @Test
    fun `completing a set marks completedAt using the injected clock`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(aWorkout()),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = 0, notes = null)),
            sets = listOf(aSet("s1", 0)),
        )
        val (useCase, _) = newUseCase(workoutRepo, clock = FakeClock(currentMillis = 77_000L))

        useCase.completeSet("w1", "we1", "s1")

        assertEquals(77_000L, workoutRepo.getSetsForWorkoutExercise("we1").single().completedAt)
    }
}
