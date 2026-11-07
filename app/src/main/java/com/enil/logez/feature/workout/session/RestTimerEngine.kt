package com.enil.logez.feature.workout.session

/**
 * PHASE2_PLAN.md §9.4: pure deadline math for the rest countdown. Deliberately stateless — the
 * deadline itself (elapsedRealtime-anchored) lives in [WorkoutSessionController]/the active-
 * session store; this object only computes new deadlines and remaining time from explicit
 * elapsedRealtime inputs, so it's directly unit-testable under virtual time with no Android
 * framework dependency (§10.6 `RestTimerEngineTest`).
 */
object RestTimerEngine {
    /** §9.4 step 1: a fresh deadline `durationSeconds` from now. */
    fun startDeadline(nowElapsedRealtimeMillis: Long, durationSeconds: Int): Long =
        nowElapsedRealtimeMillis + durationSeconds * 1000L

    /**
     * §5.1.4: "−15 below 0:15 ends the timer" — an adjustment that would leave the deadline at
     * or before now ends the timer (returns null) rather than going negative. +15 has no such
     * floor and simply extends.
     */
    fun adjustDeadline(deadlineElapsedRealtimeMillis: Long, deltaSeconds: Int, nowElapsedRealtimeMillis: Long): Long? {
        val adjusted = deadlineElapsedRealtimeMillis + deltaSeconds * 1000L
        return if (adjusted <= nowElapsedRealtimeMillis) null else adjusted
    }

    fun remainingMillis(deadlineElapsedRealtimeMillis: Long, nowElapsedRealtimeMillis: Long): Long =
        (deadlineElapsedRealtimeMillis - nowElapsedRealtimeMillis).coerceAtLeast(0)

    fun isExpired(deadlineElapsedRealtimeMillis: Long, nowElapsedRealtimeMillis: Long): Boolean =
        nowElapsedRealtimeMillis >= deadlineElapsedRealtimeMillis
}
