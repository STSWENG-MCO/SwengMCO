package com.enil.logez.core.domain.calc

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M23b: what the home-screen widget renders. */
class WidgetSnapshotCalculatorTest {
    // 2026-08-26 is a Wednesday, which makes the Monday/Sunday week-start distinction easy to read
    // and leaves days on both sides of today inside the same week.
    private val today = LocalDate.of(2026, 8, 26)

    private fun snapshot(
        dates: List<LocalDate> = emptyList(),
        today: LocalDate = this.today,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        target: Int = 4,
        steps: Long? = null,
        stepsUpdatedAt: LocalDateTime? = null,
    ) = WidgetSnapshotCalculator.snapshot(dates, today, firstDayOfWeek, target, steps, stepsUpdatedAt)

    @Test
    fun `two workouts on the same day count as one active day`() {
        // The whole reason GoalProgressCalculator's WORKOUT_COUNT is not reused here.
        val twice = LocalDate.of(2026, 8, 25)
        assertEquals(1, snapshot(listOf(twice, twice)).activeDaysThisWeek)
    }

    @Test
    fun `active days counts distinct trained days in the current week`() {
        val dates = listOf(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 26))
        assertEquals(2, snapshot(dates).activeDaysThisWeek)
    }

    @Test
    fun `a workout in the previous week is not counted`() {
        // Sunday 2026-08-23 is the last day of the previous Monday-start week.
        assertEquals(0, snapshot(listOf(LocalDate.of(2026, 8, 23))).activeDaysThisWeek)
    }

    @Test
    fun `with a Sunday week start that same workout is inside the current week`() {
        assertEquals(1, snapshot(listOf(LocalDate.of(2026, 8, 23)), firstDayOfWeek = DayOfWeek.SUNDAY).activeDaysThisWeek)
    }

    @Test
    fun `week day states are ordered from the first day of week`() {
        // Monday 2026-08-24 and Wednesday 2026-08-26.
        val states = snapshot(listOf(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 26))).weekDayStates
        assertEquals(listOf(true, false, true, false, false, false, false), states)
    }

    @Test
    fun `week day states re-order when the week starts on Sunday`() {
        val states = snapshot(
            listOf(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 26)),
            firstDayOfWeek = DayOfWeek.SUNDAY,
        ).weekDayStates
        // Sunday, Monday, Tuesday, Wednesday, ...
        assertEquals(listOf(false, true, false, true, false, false, false), states)
    }

    @Test
    fun `today index points at today's slot for both week starts`() {
        assertEquals(2, snapshot().todayIndexInWeek) // Mon-start: Wed is index 2
        assertEquals(3, snapshot(firstDayOfWeek = DayOfWeek.SUNDAY).todayIndexInWeek)
    }

    @Test
    fun `week streak delegates to StreakCalculator`() {
        val dates = listOf(
            LocalDate.of(2026, 8, 25),
            LocalDate.of(2026, 8, 18),
            LocalDate.of(2026, 8, 11),
        )
        assertEquals(3, snapshot(dates).weekStreakWeeks)
    }

    @Test
    fun `an empty history is all zeros rather than a crash`() {
        val s = snapshot()
        assertEquals(0, s.activeDaysThisWeek)
        assertEquals(0, s.weekStreakWeeks)
        assertEquals(listOf(false, false, false, false, false, false, false), s.weekDayStates)
    }

    @Test
    fun `steps read earlier today are shown`() {
        val s = snapshot(steps = 8_412L, stepsUpdatedAt = today.atTime(7, 4))
        assertEquals(8_412L, s.steps)
        assertEquals(today.atTime(7, 4), s.stepsAsOf)
    }

    @Test
    fun `steps cached yesterday are dropped rather than shown as today's`() {
        val s = snapshot(steps = 8_412L, stepsUpdatedAt = today.minusDays(1).atTime(23, 59))
        assertNull(s.steps)
        assertNull(s.stepsAsOf)
    }

    @Test
    fun `steps read at the very end of today still count as today's`() {
        assertEquals(8_412L, snapshot(steps = 8_412L, stepsUpdatedAt = today.atTime(23, 59)).steps)
    }

    @Test
    fun `no cached steps at all reads as absent`() {
        val s = snapshot(steps = null, stepsUpdatedAt = today.atTime(7, 4))
        assertNull(s.steps)
        assertNull(s.stepsAsOf)
    }

    @Test
    fun `a zero target is carried through rather than rejected`() {
        // The renderer decides how to present this; the calculator must not divide by it.
        assertEquals(0, snapshot(target = 0).targetDaysThisWeek)
    }
}
