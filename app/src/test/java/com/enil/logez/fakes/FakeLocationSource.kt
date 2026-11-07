package com.enil.logez.fakes

import com.enil.logez.feature.activity.location.LocationFix
import com.enil.logez.feature.activity.location.LocationSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2) — `emit` pushes one fix to whatever's collecting `fixes()`. */
class FakeLocationSource : LocationSource {
    private val flow = MutableSharedFlow<LocationFix>(extraBufferCapacity = 64)
    override fun fixes(): Flow<LocationFix> = flow
    fun emit(fix: LocationFix) {
        check(flow.tryEmit(fix)) { "buffer full" }
    }
}
