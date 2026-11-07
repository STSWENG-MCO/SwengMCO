package com.enil.logez.fakes

import com.enil.logez.core.common.Clock
import kotlin.time.Instant

/** PHASE2_PLAN.md §10.1 rule 3: inject a settable clock — never real system time in tests. */
class FakeClock(var currentMillis: Long = EPOCH_MILLIS) : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(currentMillis)

    companion object {
        /** 2026-01-05T09:00:00Z — an arbitrary fixed anchor, matching the plan's §10.3 convention. */
        const val EPOCH_MILLIS = 1_767_603_600_000L
    }
}
