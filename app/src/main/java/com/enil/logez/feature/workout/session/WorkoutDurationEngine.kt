package com.enil.logez.feature.workout.session

/**
 * PHASE2_PLAN.md §5.1.3/§9.5: pure pause-aware elapsed-duration math, wall-clock anchored (not
 * elapsedRealtime — must stay meaningful across a device reboot, since a workout can legitimately
 * span one). `lastResumedAtMillis` is the wall-clock timestamp of the most recent resume/start;
 * `accumulatedActiveSeconds` is the running total from every completed active span before that.
 * Stateless and Android-framework-free by design (§10.6 elapsed/pause duration tests under
 * virtual time).
 */
object WorkoutDurationEngine {
    fun elapsedSeconds(accumulatedActiveSeconds: Long, isPaused: Boolean, lastResumedAtMillis: Long?, nowMillis: Long): Long =
        if (isPaused || lastResumedAtMillis == null) {
            accumulatedActiveSeconds
        } else {
            accumulatedActiveSeconds + ((nowMillis - lastResumedAtMillis) / 1000).coerceAtLeast(0)
        }

    /** Folds the just-finished active span into the accumulator; caller clears `lastResumedAtMillis` and sets `isPaused = true`. */
    fun accumulateOnPause(accumulatedActiveSeconds: Long, lastResumedAtMillis: Long?, nowMillis: Long): Long =
        accumulatedActiveSeconds + ((nowMillis - (lastResumedAtMillis ?: nowMillis)) / 1000).coerceAtLeast(0)
}
