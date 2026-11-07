package com.enil.logez.core.domain.calc

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** §10.6's M4c row: "StreakCalculatorTest (the summary's streak line)" — §8.7 weekly streak semantics. */
class StreakCalculatorTest {
    // 2026-08-24 is a Monday, which makes the Monday/Sunday week-start distinction easy to read.
    private val monday = LocalDate.of(2026, 8, 24)

    @Test
    fun `weekStart snaps to the configured first day of week`() {
        val wednesday = LocalDate.of(2026, 8, 26)
        assertEquals(monday, StreakCalculator.weekStart(wednesday, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 8, 23), StreakCalculator.weekStart(wednesday, DayOfWeek.SUNDAY))
    }

    @Test
    fun `weekStart on the first day itself stays put`() {
        assertEquals(monday, StreakCalculator.weekStart(monday, DayOfWeek.MONDAY))
    }

    @Test
    fun `consecutive weeks accumulate`() {
        val dates = listOf(monday, monday.minusWeeks(1), monday.minusWeeks(2))
        assertEquals(3, StreakCalculator.weeklyStreak(dates, monday, DayOfWeek.MONDAY))
    }

    @Test
    fun `a gap week ends the streak at the gap`() {
        // This week and last week, then nothing, then two older weeks that must NOT be counted.
        val dates = listOf(monday, monday.minusWeeks(1), monday.minusWeeks(3), monday.minusWeeks(4))
        assertEquals(2, StreakCalculator.weeklyStreak(dates, monday, DayOfWeek.MONDAY))
    }

    @Test
    fun `a still-empty current week does not break an otherwise-active streak`() {
        // §8.7's grace rule: nothing logged yet this week, but last week and the one before count.
        val dates = listOf(monday.minusWeeks(1), monday.minusWeeks(2))
        assertEquals(2, StreakCalculator.weeklyStreak(dates, monday, DayOfWeek.MONDAY))
    }

    @Test
    fun `two consecutive empty weeks end the streak`() {
        val dates = listOf(monday.minusWeeks(2), monday.minusWeeks(3))
        assertEquals(0, StreakCalculator.weeklyStreak(dates, monday, DayOfWeek.MONDAY))
    }

    @Test
    fun `no workouts at all is a zero streak`() {
        assertEquals(0, StreakCalculator.weeklyStreak(emptyList(), monday, DayOfWeek.MONDAY))
    }

    @Test
    fun `several workouts in one week still count as one week`() {
        val dates = listOf(monday, monday.plusDays(1), monday.plusDays(2))
        assertEquals(1, StreakCalculator.weeklyStreak(dates, monday, DayOfWeek.MONDAY))
    }

    @Test
    fun `the first-day-of-week setting can change the answer for the same dates`() {
        // Sunday 2026-08-23 and Monday 2026-08-24 are the SAME week under Sunday-start, but two
        // DIFFERENT weeks under Monday-start.
        val dates = listOf(LocalDate.of(2026, 8, 23), monday)
        assertEquals(2, StreakCalculator.weeklyStreak(dates, monday, DayOfWeek.MONDAY))
        assertEquals(1, StreakCalculator.weeklyStreak(dates, monday, DayOfWeek.SUNDAY))
    }

    @Test
    fun `countsByDate tallies completed workouts per calendar day`() {
        val counts = StreakCalculator.countsByDate(listOf(monday, monday, monday.plusDays(1)))
        assertEquals(2, counts.getValue(monday))
        assertEquals(1, counts.getValue(monday.plusDays(1)))
    }

    @Test
    fun `consecutive days accumulate`() {
        val dates = listOf(monday, monday.minusDays(1), monday.minusDays(2))
        assertEquals(3, StreakCalculator.dailyStreak(dates, monday))
    }

    @Test
    fun `a gap day ends the daily streak at the gap`() {
        val dates = listOf(monday, monday.minusDays(1), monday.minusDays(3), monday.minusDays(4))
        assertEquals(2, StreakCalculator.dailyStreak(dates, monday))
    }

    @Test
    fun `a still-empty today does not break an otherwise-active daily streak`() {
        val dates = listOf(monday.minusDays(1), monday.minusDays(2))
        assertEquals(2, StreakCalculator.dailyStreak(dates, monday))
    }

    @Test
    fun `two consecutive empty days end the daily streak`() {
        val dates = listOf(monday.minusDays(2), monday.minusDays(3))
        assertEquals(0, StreakCalculator.dailyStreak(dates, monday))
    }

    @Test
    fun `no workouts at all is a zero daily streak`() {
        assertEquals(0, StreakCalculator.dailyStreak(emptyList(), monday))
    }

    @Test
    fun `several workouts in one day still count as one day`() {
        val dates = listOf(monday, monday, monday)
        assertEquals(1, StreakCalculator.dailyStreak(dates, monday))
    }
}
