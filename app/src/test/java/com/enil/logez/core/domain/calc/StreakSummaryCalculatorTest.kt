package com.enil.logez.core.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StreakSummaryCalculatorTest {
    // Fri 9 Oct 2026; its week starts Mon 5 Oct, the one before Mon 28 Sep, then Mon 21 Sep.
    private val today = LocalDate.of(2026, 10, 9)
    private val current = StreakWorkout("Push Day", today)

    private fun w(
        month: Int,
        day: Int,
        title: String = "Push Day",
    ) = StreakWorkout(title, LocalDate.of(2026, month, day))

    private fun summarize(
        vararg others: StreakWorkout,
        target: Int = 2,
    ) = StreakSummaryCalculator.summarize(others.toList() + current, current, DayOfWeek.MONDAY, target)

    @Test
    fun `consecutive weeks that met the target make the week streak`() {
        val s = summarize(w(10, 5), w(9, 28), w(9, 30), w(9, 21), w(9, 23))
        assertEquals(3, s.weekStreak)
    }

    @Test
    fun `a current week that has not met the target yet does not break the streak`() {
        val s = summarize(w(9, 28), w(9, 30), w(9, 21), w(9, 23))
        assertEquals(2, s.weekStreak)
    }

    @Test
    fun `a week below the target ends the streak`() {
        val s = summarize(w(10, 5), w(9, 21), w(9, 23))
        assertEquals(1, s.weekStreak)
    }

    @Test
    fun `week day counts cover twelve weeks ending with this one`() {
        val s = summarize(w(9, 28), w(9, 30), w(9, 21), w(9, 23))
        assertEquals(12, s.weekDayCounts.size)
        assertEquals(listOf(2, 2, 1), s.weekDayCounts.takeLast(3))
        assertEquals(List(9) { 0 }, s.weekDayCounts.take(9))
    }

    @Test
    fun `several workouts on one day count as one day`() {
        val s = summarize(w(10, 9, "Pull Day"), target = 1)
        assertEquals(1, s.weekDayCounts.last())
    }

    @Test
    fun `day streak counts consecutive days ending on the workout`() {
        assertEquals(3, summarize(w(10, 7), w(10, 8)).dayStreak)
        assertEquals(1, summarize(w(10, 7)).dayStreak)
    }

    @Test
    fun `nth this month counts same-titled workouts up to this one`() {
        val s = summarize(w(10, 2), w(10, 5), w(10, 6, "Pull Day"), w(9, 30))
        assertEquals(3, s.nthThisMonth)
    }

    @Test
    fun `a workout after this one is ignored`() {
        val s = summarize(w(10, 12), w(10, 13))
        assertEquals(1, s.weekDayCounts.last())
        assertEquals(1, s.nthThisMonth)
    }

    @Test
    fun `a target below one is treated as one`() {
        assertEquals(1, summarize(target = 0).weeklyTarget)
    }
}
