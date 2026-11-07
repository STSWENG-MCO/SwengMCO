package com.enil.logez.fakes

import com.enil.logez.core.common.ElapsedRealtimeClock

/** PHASE2_PLAN.md §10.1 rule 3: a settable elapsedRealtime source — never real SystemClock in tests. */
class FakeElapsedRealtimeClock(var currentMillis: Long = 0L) : ElapsedRealtimeClock {
    override fun elapsedRealtimeMillis(): Long = currentMillis
}
