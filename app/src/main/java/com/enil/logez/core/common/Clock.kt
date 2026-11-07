package com.enil.logez.core.common

import kotlin.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owner testing rule (PHASE2_PLAN.md §10.1 rule 3): raw system time is banned from any code
 * under test. Production binds [SystemClock]; tests bind a fake with a settable [now].
 *
 * Uses `kotlin.time.Instant` (stable since Kotlin 2.3) directly rather than
 * `kotlinx.datetime.Instant` — kotlinx-datetime 0.7.1's typealias to the stdlib type does not
 * reliably resolve `Clock.System` through the alias; the stdlib symbol is unambiguous.
 */
interface Clock {
    fun now(): Instant
}

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun now(): Instant = kotlin.time.Clock.System.now()
}
