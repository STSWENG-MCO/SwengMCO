package com.enil.logez.core.domain.calc

import com.enil.logez.core.data.entity.GoalDefinitionEntity
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.GoalMetric
import com.enil.logez.core.domain.model.GoalPeriod
import com.enil.logez.core.domain.model.SetType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class GoalProgressCalculatorTest {
    private val zone = ZoneId.of("UTC")
    private val monday = LocalDate.of(2026, 8, 24) // matches StreakCalculatorTest's anchor

    private fun millisAt(date: LocalDate): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun statSet(workoutId: String, workoutStartedAt: Long, reps: Int, weightKg: Double, setType: SetType = SetType.NORMAL) = StatSet(
        setId = "s-$workoutId-$reps-$weightKg", workoutId = workoutId, workoutStartedAt = workoutStartedAt, orderIndex = 0,
        setType = setType, weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null,
        customMetric = null, isCompleted = true,
    )

    private fun setWithExercise(set: StatSet) = DashboardAggregator.SetWithExercise(
        exerciseId = "ex-1", exerciseName = "Bench Press", exerciseType = ExerciseType.WEIGHT_REPS,
        isBodyweightVolumeEligible = false, set = set,
    )

    @Test
    fun `currentPeriodWindow DAILY is just today`() {
        val window = GoalProgressCalculator.currentPeriodWindow(GoalPeriod.DAILY, monday, DayOfWeek.MONDAY)
        assertEquals(monday, window.start)
        assertEquals(monday, window.endInclusive)
    }

    @Test
    fun `currentPeriodWindow WEEKLY spans the configured week`() {
        val wednesday = monday.plusDays(2)
        val window = GoalProgressCalculator.currentPeriodWindow(GoalPeriod.WEEKLY, wednesday, DayOfWeek.MONDAY)
        assertEquals(monday, window.start)
        assertEquals(monday.plusDays(6), window.endInclusive)
    }

    @Test
    fun `currentPeriodWindow MONTHLY spans the whole month`() {
        val window = GoalProgressCalculator.currentPeriodWindow(GoalPeriod.MONTHLY, monday, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 8, 1), window.start)
        assertEquals(LocalDate.of(2026, 8, 31), window.endInclusive)
    }

    @Test
    fun `VOLUME progress sums included sets within the window via periodTotals`() {
        val startedAt = millisAt(monday)
        val goal = GoalDefinitionEntity(id = "g1", metric = GoalMetric.VOLUME, period = GoalPeriod.WEEKLY, targetValue = 1000.0, createdAt = 0, updatedAt = 0)
        val workouts = listOf(DashboardAggregator.WorkoutInfo(id = "w1", startedAt = startedAt, durationSeconds = 600))
        val sets = listOf(setWithExercise(statSet("w1", startedAt, reps = 10, weightKg = 50.0))) // 500kg

        val progress = GoalProgressCalculator.progress(goal, workouts, sets, monday, zone, DayOfWeek.MONDAY, includeWarmupsInStats = false)
        assertEquals(500.0, progress.current, 0.0)
        assertEquals(1000.0, progress.target, 0.0)
    }

    @Test
    fun `DURATION and WORKOUT_COUNT progress read from periodTotals`() {
        val startedAt = millisAt(monday)
        val workouts = listOf(
            DashboardAggregator.WorkoutInfo(id = "w1", startedAt = startedAt, durationSeconds = 1800),
            DashboardAggregator.WorkoutInfo(id = "w2", startedAt = millisAt(monday.plusDays(1)), durationSeconds = 900),
        )
        val durationGoal = GoalDefinitionEntity(id = "g1", metric = GoalMetric.DURATION, period = GoalPeriod.WEEKLY, targetValue = 3600.0, createdAt = 0, updatedAt = 0)
        val countGoal = GoalDefinitionEntity(id = "g2", metric = GoalMetric.WORKOUT_COUNT, period = GoalPeriod.WEEKLY, targetValue = 5.0, createdAt = 0, updatedAt = 0)

        val durationProgress = GoalProgressCalculator.progress(durationGoal, workouts, emptyList(), monday, zone, DayOfWeek.MONDAY, includeWarmupsInStats = false)
        val countProgress = GoalProgressCalculator.progress(countGoal, workouts, emptyList(), monday, zone, DayOfWeek.MONDAY, includeWarmupsInStats = false)

        assertEquals(2700.0, durationProgress.current, 0.0)
        assertEquals(2.0, countProgress.current, 0.0)
    }

    @Test
    fun `REPS progress excludes warmups when includeWarmupsInStats is false, matching isIncluded`() {
        val startedAt = millisAt(monday)
        val goal = GoalDefinitionEntity(id = "g1", metric = GoalMetric.REPS, period = GoalPeriod.WEEKLY, targetValue = 100.0, createdAt = 0, updatedAt = 0)
        val workouts = listOf(DashboardAggregator.WorkoutInfo(id = "w1", startedAt = startedAt, durationSeconds = 600))
        val sets = listOf(
            setWithExercise(statSet("w1", startedAt, reps = 10, weightKg = 50.0, setType = SetType.WARMUP)),
            setWithExercise(statSet("w1", startedAt, reps = 8, weightKg = 60.0, setType = SetType.NORMAL)),
        )

        val excluded = GoalProgressCalculator.progress(goal, workouts, sets, monday, zone, DayOfWeek.MONDAY, includeWarmupsInStats = false)
        val included = GoalProgressCalculator.progress(goal, workouts, sets, monday, zone, DayOfWeek.MONDAY, includeWarmupsInStats = true)

        assertEquals(8.0, excluded.current, 0.0)
        assertEquals(18.0, included.current, 0.0)
    }

    @Test
    fun `progress excludes workouts and sets outside the current period window`() {
        val insideWeek = millisAt(monday)
        val lastWeek = millisAt(monday.minusWeeks(1))
        val goal = GoalDefinitionEntity(id = "g1", metric = GoalMetric.VOLUME, period = GoalPeriod.WEEKLY, targetValue = 1000.0, createdAt = 0, updatedAt = 0)
        val workouts = listOf(
            DashboardAggregator.WorkoutInfo(id = "w-this", startedAt = insideWeek, durationSeconds = 600),
            DashboardAggregator.WorkoutInfo(id = "w-last", startedAt = lastWeek, durationSeconds = 600),
        )
        val sets = listOf(
            setWithExercise(statSet("w-this", insideWeek, reps = 10, weightKg = 50.0)), // 500kg, in window
            setWithExercise(statSet("w-last", lastWeek, reps = 10, weightKg = 999.0)), // huge, but out of window
        )

        val progress = GoalProgressCalculator.progress(goal, workouts, sets, monday, zone, DayOfWeek.MONDAY, includeWarmupsInStats = false)
        assertEquals(500.0, progress.current, 0.0)
    }
}
