package com.enil.logez.core.domain.calc

import java.time.DayOfWeek
import java.time.LocalDate

data class StreakWorkout(
    val title: String,
    val date: LocalDate,
)

/**
 * @property weekStreak consecutive weeks with at least [weeklyTarget] distinct workout days, ending
 * with the workout's own week (or the week before, when this one has not met the target yet).
 * @property weekDayCounts distinct workout days in each of the last [StreakSummaryCalculator.WEEKS]
 * weeks, oldest first; the last entry is the workout's own week.
 * @property nthThisMonth this workout's rank among the same-titled workouts of its month.
 */
data class StreakSummary(
    val weekStreak: Int,
    val dayStreak: Int,
    val weeklyTarget: Int,
    val weekDayCounts: List<Int>,
    val nthThisMonth: Int,
)

/** The Streak share template's figures, measured as of the workout's own date so a card never ages. */
object StreakSummaryCalculator {
    const val WEEKS = 12

    fun summarize(
        workouts: List<StreakWorkout>,
        current: StreakWorkout,
        firstDayOfWeek: DayOfWeek,
        weeklyTarget: Int,
    ): StreakSummary {
        val today = current.date
        val target = weeklyTarget.coerceAtLeast(1)
        val dates = workouts.map { it.date }.filter { !it.isAfter(today) }.toSet()
        val daysByWeek = dates.groupingBy { StreakCalculator.weekStart(it, firstDayOfWeek) }.eachCount()
        val thisWeek = StreakCalculator.weekStart(today, firstDayOfWeek)

        val counts = (WEEKS - 1 downTo 0).map { daysByWeek[thisWeek.minusWeeks(it.toLong())] ?: 0 }

        var week = if ((daysByWeek[thisWeek] ?: 0) >= target) thisWeek else thisWeek.minusWeeks(1)
        var weekStreak = 0
        while ((daysByWeek[week] ?: 0) >= target) {
            weekStreak++
            week = week.minusWeeks(1)
        }

        val nth =
            workouts.count {
                it.title == current.title && it.date.year == today.year && it.date.month == today.month && !it.date.isAfter(today)
            }

        return StreakSummary(
            weekStreak = weekStreak,
            dayStreak = StreakCalculator.dailyStreak(dates.toList(), today),
            weeklyTarget = target,
            weekDayCounts = counts,
            nthThisMonth = nth.coerceAtLeast(1),
        )
    }
}
