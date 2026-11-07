package com.enil.logez.feature.workout.session

import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutDurationEngineTest {
    @Test
    fun `elapsedSeconds while running accumulates plus the current active span`() {
        val elapsed = WorkoutDurationEngine.elapsedSeconds(
            accumulatedActiveSeconds = 60L, isPaused = false, lastResumedAtMillis = 10_000L, nowMillis = 40_000L,
        )
        assertEquals(90L, elapsed) // 60 + (40_000-10_000)/1000
    }

    @Test
    fun `elapsedSeconds while paused freezes at the accumulated total, ignoring elapsed wall-clock time`() {
        val elapsed = WorkoutDurationEngine.elapsedSeconds(
            accumulatedActiveSeconds = 90L, isPaused = true, lastResumedAtMillis = null, nowMillis = 999_999L,
        )
        assertEquals(90L, elapsed)
    }

    @Test
    fun `accumulateOnPause folds the just-finished active span into the total`() {
        val accumulated = WorkoutDurationEngine.accumulateOnPause(
            accumulatedActiveSeconds = 60L, lastResumedAtMillis = 10_000L, nowMillis = 25_000L,
        )
        assertEquals(75L, accumulated) // 60 + (25_000-10_000)/1000
    }

    @Test
    fun `pause then resume then pause again accumulates both active spans`() {
        // Simulates: start at t=0, pause at t=30s (30s accumulated), resume at t=50s, pause at t=80s (+30s more).
        val afterFirstPause = WorkoutDurationEngine.accumulateOnPause(accumulatedActiveSeconds = 0L, lastResumedAtMillis = 0L, nowMillis = 30_000L)
        assertEquals(30L, afterFirstPause)

        val afterSecondPause = WorkoutDurationEngine.accumulateOnPause(accumulatedActiveSeconds = afterFirstPause, lastResumedAtMillis = 50_000L, nowMillis = 80_000L)
        assertEquals(60L, afterSecondPause)
    }
}
