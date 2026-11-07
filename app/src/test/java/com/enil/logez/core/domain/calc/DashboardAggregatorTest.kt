package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.calc.DashboardAggregator.SetWithExercise
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.calc.DashboardAggregator.WorkoutInfo
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardAggregatorTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 8, 22) // Saturday; MONDAY weeks start 08-17

    private fun millis(date: String) = LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli().plus(12 * 3_600_000L)

    private fun workout(id: String, date: String, durationSeconds: Int = 3600) =
        WorkoutInfo(id, millis(date), durationSeconds)

    private fun benchSet(workoutId: String, date: String, weightKg: Double?, reps: Int?, type: SetType = SetType.NORMAL, completed: Boolean = true) =
        SetWithExercise(
            exerciseId = "ex-bench", exerciseName = "Bench Press", exerciseType = ExerciseType.WEIGHT_REPS,
            isBodyweightVolumeEligible = false,
            set = StatSet("s-${workoutId}-${weightKg}-${reps}-${type}", workoutId, millis(date), 0, type, weightKg, reps, null, null, null, completed),
        )

    @Test
    fun `FREQUENCY counts workouts per week including a zero week between active ones`() {
        val bars = DashboardAggregator.weeklyTrainingSeries(
            TrainingMetric.FREQUENCY,
            listOf(workout("w1", "2026-08-04"), workout("w2", "2026-08-05"), workout("w3", "2026-08-18")),
            emptyList(), ChartRange.LAST_30_DAYS, today, zone, DayOfWeek.MONDAY, false,
        )
        assertEquals(
            listOf(
                DashboardAggregator.WeeklyBar(LocalDate.parse("2026-08-03"), 2.0),
                DashboardAggregator.WeeklyBar(LocalDate.parse("2026-08-10"), 0.0),
                DashboardAggregator.WeeklyBar(LocalDate.parse("2026-08-17"), 1.0),
            ),
            bars,
        )
    }

    @Test
    fun `VOLUME sums set volumes per week and respects the warm-up filter`() {
        val workouts = listOf(workout("w1", "2026-08-18"))
        val sets = listOf(
            benchSet("w1", "2026-08-18", 100.0, 5),
            benchSet("w1", "2026-08-18", 60.0, 8, SetType.WARMUP),
            benchSet("w1", "2026-08-18", 200.0, 5, completed = false),
        )
        val off = DashboardAggregator.weeklyTrainingSeries(TrainingMetric.VOLUME, workouts, sets, ChartRange.LAST_30_DAYS, today, zone, DayOfWeek.MONDAY, false)
        assertEquals(500.0, off.last().value, 1e-9)
        val on = DashboardAggregator.weeklyTrainingSeries(TrainingMetric.VOLUME, workouts, sets, ChartRange.LAST_30_DAYS, today, zone, DayOfWeek.MONDAY, true)
        assertEquals(980.0, on.last().value, 1e-9)
    }

    @Test
    fun `DURATION is workout-level and unaffected by the warm-up setting`() {
        val workouts = listOf(workout("w1", "2026-08-18", 1800), workout("w2", "2026-08-19", 600))
        val bars = DashboardAggregator.weeklyTrainingSeries(TrainingMetric.DURATION, workouts, emptyList(), ChartRange.LAST_30_DAYS, today, zone, DayOfWeek.MONDAY, false)
        assertEquals(2400.0, bars.single { it.weekStart == LocalDate.parse("2026-08-17") }.value, 1e-9)
    }

    @Test
    fun `a window with no workouts yields an empty series for the empty-card state`() {
        val bars = DashboardAggregator.weeklyTrainingSeries(
            TrainingMetric.FREQUENCY, listOf(workout("w1", "2025-01-05")), emptyList(),
            ChartRange.LAST_30_DAYS, today, zone, DayOfWeek.MONDAY, false,
        )
        assertTrue(bars.isEmpty())
    }

    @Test
    fun `periodTotals matches the plan's four tiles`() {
        val workouts = listOf(workout("w1", "2026-08-18", 1800), workout("w2", "2026-08-19", 1200))
        val sets = listOf(
            benchSet("w1", "2026-08-18", 100.0, 5),
            benchSet("w2", "2026-08-19", 80.0, 10),
            benchSet("w2", "2026-08-19", 60.0, 8, SetType.WARMUP),
        )
        val totals = DashboardAggregator.periodTotals(workouts, sets, DashboardAggregator.monthWindow(YearMonth.of(2026, 8)), zone, false)
        assertEquals(2, totals.workouts)
        assertEquals(3000L, totals.durationSeconds)
        assertEquals(1300.0, totals.volumeKg, 1e-9)
        assertEquals(2, totals.sets)
    }

    @Test
    fun `mainExercises counts distinct workouts not sets`() {
        val sets = listOf(
            benchSet("w1", "2026-08-18", 100.0, 5),
            benchSet("w1", "2026-08-18", 100.0, 4),
            benchSet("w2", "2026-08-19", 100.0, 5),
            SetWithExercise(
                "ex-squat", "Squat", ExerciseType.WEIGHT_REPS, false,
                StatSet("sq1", "w2", millis("2026-08-19"), 0, SetType.NORMAL, 120.0, 5, null, null, null, true),
            ),
        )
        val result = DashboardAggregator.mainExercises(sets, null, zone, false)
        assertEquals(listOf("Bench Press" to 2, "Squat" to 1), result.map { it.exerciseName to it.workoutCount })
    }

    @Test
    fun `monthlyTotals returns one row per requested month oldest first`() {
        val workouts = listOf(workout("w1", "2026-07-10", 600), workout("w2", "2026-08-18", 1800))
        val months = listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 7), YearMonth.of(2026, 6))
        val rows = DashboardAggregator.monthlyTotals(workouts, emptyList(), months, zone, false)
        assertEquals(listOf(YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8)), rows.map { it.month })
        assertEquals(listOf(0, 1, 1), rows.map { it.totals.workouts })
    }
}
