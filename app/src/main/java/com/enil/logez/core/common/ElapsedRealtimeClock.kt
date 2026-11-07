package com.enil.logez.core.common

import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE2_PLAN.md §9.4: the rest-timer deadline is anchored on `SystemClock.elapsedRealtime()`
 * (monotonic, immune to wall-clock/NTP/timezone changes and CPU sleep) rather than [Clock]'s
 * wall-clock `now()` — a deadline computed this way self-corrects after any process stall.
 * Deliberately not reused for total workout duration: that's wall-clock (`workouts.startedAt`)
 * because it must stay meaningful across a device reboot, which resets elapsedRealtime to 0.
 */
interface ElapsedRealtimeClock {
    fun elapsedRealtimeMillis(): Long
}

@Singleton
class SystemElapsedRealtimeClock @Inject constructor() : ElapsedRealtimeClock {
    override fun elapsedRealtimeMillis(): Long = android.os.SystemClock.elapsedRealtime()
}
