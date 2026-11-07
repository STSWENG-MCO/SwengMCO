package com.enil.logez.core.domain.calc

import com.enil.logez.core.data.entity.GoalDefinitionEntity
import com.enil.logez.core.domain.model.GoalMetric
import com.enil.logez.core.domain.model.GoalPeriod
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * M8d — progress for one [GoalDefinitionEntity] over its *current* daily/weekly/monthly window.
 * Deliberately computed live from already-queried data on every call, never persisted: this app
 * has no goal-history feature (no "did you hit last week's goal" screen), so there is nothing a
 * cache would serve that a live computation doesn't already give for free. Reuses
 * [DashboardAggregator.periodTotals] for volume/duration/workout-count exactly as the Analytics
 * dashboard does — the one metric it doesn't expose, reps, is computed here with the same
 * window/workoutId/[isIncluded] filtering [DashboardAggregator.periodTotals] itself uses, so
 * warm-up-inclusion semantics can never drift between the dashboard and Goals.
 */
object GoalProgressCalculator {
    data class Progress(val current: Double, val target: Double, val periodStart: LocalDate, val periodEnd: LocalDate)

    fun currentPeriodWindow(period: GoalPeriod, today: LocalDate, firstDayOfWeek: DayOfWeek): ClosedRange<LocalDate> =
        when (period) {
            GoalPeriod.DAILY -> today..today
            GoalPeriod.WEEKLY -> {
                val start = StreakCalculator.weekStart(today, firstDayOfWeek)
                start..start.plusDays(6)
            }
            GoalPeriod.MONTHLY -> DashboardAggregator.monthWindow(YearMonth.from(today))
        }

    fun progress(
        goal: GoalDefinitionEntity,
        workouts: List<DashboardAggregator.WorkoutInfo>,
        sets: List<DashboardAggregator.SetWithExercise>,
        today: LocalDate,
        zone: ZoneId,
        firstDayOfWeek: DayOfWeek,
        includeWarmupsInStats: Boolean,
    ): Progress {
        val window = currentPeriodWindow(goal.period, today, firstDayOfWeek)
        val totals = DashboardAggregator.periodTotals(workouts, sets, window, zone, includeWarmupsInStats)
        val current = when (goal.metric) {
            GoalMetric.VOLUME -> totals.volumeKg
            GoalMetric.DURATION -> totals.durationSeconds.toDouble()
            GoalMetric.WORKOUT_COUNT -> totals.workouts.toDouble()
            GoalMetric.REPS -> {
                val workoutIds = workouts
                    .filter { DashboardAggregator.localDate(it.startedAt, zone) in window }
                    .map { it.id }.toHashSet()
                sets
                    .filter { it.set.workoutId in workoutIds && isIncluded(it.set, includeWarmupsInStats) }
                    .sumOf { (it.set.reps ?: 0).toDouble() }
            }
        }
        return Progress(current = current, target = goal.targetValue, periodStart = window.start, periodEnd = window.endInclusive)
    }
}
