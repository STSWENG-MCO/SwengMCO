package com.enil.logez.feature.widget

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The only arithmetic in the midnight worker, and the only part of it that is testable. */
class MidnightWidgetWorkerTest {
    private val utc = ZoneId.of("UTC")

    @Test
    fun `mid-afternoon waits until just past the next midnight`() {
        val now = ZonedDateTime.of(2026, 8, 26, 15, 0, 0, 0, utc)
        // 9 hours to midnight, plus the 1-minute margin.
        assertEquals((9 * 60 + 1) * 60_000L, nextMidnightDelayMillis(now))
    }

    @Test
    fun `one minute before midnight waits about two minutes`() {
        val now = ZonedDateTime.of(2026, 8, 26, 23, 59, 0, 0, utc)
        assertEquals(2 * 60_000L, nextMidnightDelayMillis(now))
    }

    @Test
    fun `just after midnight waits nearly a full day rather than firing immediately`() {
        val now = ZonedDateTime.of(2026, 8, 26, 0, 0, 30, 0, utc)
        val delay = nextMidnightDelayMillis(now)
        assertTrue("expected nearly a day, was $delay", delay > 23 * 60 * 60_000L)
    }

    @Test
    fun `a zone that skips midnight wakes on the next day's first real instant`() {
        // Santiago springs forward at 00:00 on 2026-09-06: the clock jumps 23:59:59 -> 01:00:00,
        // so that local midnight never happens and start-of-day resolves forward past the gap.
        val santiago = ZoneId.of("America/Santiago")
        val now = ZonedDateTime.of(2026, 9, 5, 22, 0, 0, 0, santiago)

        val fired = now.plus(java.time.Duration.ofMillis(nextMidnightDelayMillis(now)))

        assertEquals(java.time.LocalDate.of(2026, 9, 6), fired.toLocalDate())
        assertEquals(1, fired.hour) // 01:00, not a midnight that does not exist
    }

    @Test
    fun `the delay is never negative`() {
        val now = ZonedDateTime.of(2026, 8, 26, 23, 59, 59, 999_000_000, utc)
        assertTrue(nextMidnightDelayMillis(now) >= 0)
    }
}
