package com.enil.logez.feature.workout.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RestTimerEngineTest {
    @Test
    fun `startDeadline is now plus the duration`() {
        val deadline = RestTimerEngine.startDeadline(nowElapsedRealtimeMillis = 10_000L, durationSeconds = 90)
        assertEquals(100_000L, deadline)
    }

    @Test
    fun `adjustDeadline plus15 extends with no floor`() {
        val deadline = RestTimerEngine.adjustDeadline(deadlineElapsedRealtimeMillis = 10_000L, deltaSeconds = 15, nowElapsedRealtimeMillis = 5_000L)
        assertEquals(25_000L, deadline)
    }

    @Test
    fun `adjustDeadline minus15 shortens when well above the 0-15 floor`() {
        // 30s remaining -> minus 15 leaves 15s, still positive.
        val deadline = RestTimerEngine.adjustDeadline(deadlineElapsedRealtimeMillis = 30_000L, deltaSeconds = -15, nowElapsedRealtimeMillis = 0L)
        assertEquals(15_000L, deadline)
    }

    @Test
    fun `adjustDeadline minus15 below the 0-15 floor ends the timer`() {
        // §5.1.4: "-15 below 0:15 ends the timer" -- 10s remaining, minus 15 would go negative.
        val deadline = RestTimerEngine.adjustDeadline(deadlineElapsedRealtimeMillis = 10_000L, deltaSeconds = -15, nowElapsedRealtimeMillis = 0L)
        assertNull(deadline)
    }

    @Test
    fun `adjustDeadline minus15 exactly at the floor ends the timer`() {
        // Exactly 15s remaining minus 15 lands exactly on now -> ends (adjusted <= now).
        val deadline = RestTimerEngine.adjustDeadline(deadlineElapsedRealtimeMillis = 15_000L, deltaSeconds = -15, nowElapsedRealtimeMillis = 0L)
        assertNull(deadline)
    }

    @Test
    fun `remainingMillis never goes negative once the deadline has passed`() {
        assertEquals(0L, RestTimerEngine.remainingMillis(deadlineElapsedRealtimeMillis = 1_000L, nowElapsedRealtimeMillis = 5_000L))
        assertEquals(4_000L, RestTimerEngine.remainingMillis(deadlineElapsedRealtimeMillis = 5_000L, nowElapsedRealtimeMillis = 1_000L))
    }

    @Test
    fun `isExpired is true at and after the deadline, false before it`() {
        assertTrue(RestTimerEngine.isExpired(deadlineElapsedRealtimeMillis = 1_000L, nowElapsedRealtimeMillis = 1_000L))
        assertTrue(RestTimerEngine.isExpired(deadlineElapsedRealtimeMillis = 1_000L, nowElapsedRealtimeMillis = 1_001L))
        assertTrue(!RestTimerEngine.isExpired(deadlineElapsedRealtimeMillis = 1_000L, nowElapsedRealtimeMillis = 999L))
    }
}
