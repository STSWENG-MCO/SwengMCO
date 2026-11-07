package com.enil.logez.feature.routines

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.GoalMetric
import com.enil.logez.core.domain.model.GoalPeriod
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeGoalRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    // 2026-08-24 is a Monday, matching StreakCalculatorTest's anchor.
    private fun millisOn(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 3_600_000L
    private val nowMillis = millisOn("2026-08-24")

    private fun workoutRepoWithOneWorkout(): FakeWorkoutRepository = FakeWorkoutRepository(
        workouts = listOf(
            WorkoutEntity(
                id = "w1", routineId = null, title = "Push", notes = null, status = WorkoutStatus.COMPLETED,
                startedAt = nowMillis, endedAt = nowMillis + 1800_000L, durationSeconds = 1800,
                createdAt = nowMillis, updatedAt = nowMillis,
            ),
        ),
        exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-bench", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
        sets = listOf(
            WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 50.0, reps = 10, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L),
            WorkoutSetEntity(id = "s2", workoutExerciseId = "we1", orderIndex = 1, setType = SetType.NORMAL, weightKg = 50.0, reps = 10, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L),
        ),
    )

    private fun exerciseRepo(): FakeExerciseRepository = FakeExerciseRepository(
        listOf(
            Exercise(
                id = "ex-bench", name = "Bench Press", exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
                secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
                isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
            ),
        ),
    )

    private fun newViewModel(
        goalRepo: FakeGoalRepository = FakeGoalRepository(),
        workoutRepo: FakeWorkoutRepository = workoutRepoWithOneWorkout(),
    ): GoalsViewModel = GoalsViewModel(
        goalRepo, workoutRepo, exerciseRepo(), FakeSettingsRepository(), FakeClock(currentMillis = nowMillis),
    ).also { it.refresh() } // mirrors the screen's RefreshOnResume, which drives the first load

    @Test
    fun `a weekly volume goal reports progress against the current week's logged volume`() = runTest {
        val vm = newViewModel()
        vm.createGoal(GoalMetric.VOLUME, GoalPeriod.WEEKLY, 2000.0)

        val row = vm.uiState.value.goals.single()
        assertEquals(1000.0, row.progress.current, 0.0) // 2 sets x 10 reps x 50kg
        assertEquals(2000.0, row.progress.target, 0.0)
    }

    @Test
    fun `deleting a goal removes it from the list`() = runTest {
        val vm = newViewModel()
        vm.createGoal(GoalMetric.WORKOUT_COUNT, GoalPeriod.WEEKLY, 3.0)
        val id = vm.uiState.value.goals.single().goal.id

        vm.deleteGoal(id)

        assertTrue(vm.uiState.value.goals.isEmpty())
    }

    @Test
    fun `creating a goal with a non-positive target is rejected`() = runTest {
        val vm = newViewModel()
        vm.createGoal(GoalMetric.REPS, GoalPeriod.DAILY, 0.0)
        assertTrue(vm.uiState.value.goals.isEmpty())
    }
}
