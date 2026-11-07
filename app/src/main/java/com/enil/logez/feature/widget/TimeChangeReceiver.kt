package com.enil.logez.feature.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Setting the clock or crossing a timezone can change what "today" is, which moves the widget's
 * marker and invalidates the pending midnight wakeup's delay.
 *
 * Both actions are on the implicit-broadcast allowlist, so a manifest receiver genuinely works.
 */
class TimeChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Act only on the two system broadcasts the manifest registers for (lint
        // UnsafeProtectedBroadcastReceiver, 2026-09-25 audit): anything else reaching this
        // receiver is not a clock change and must not trigger a refresh or reschedule.
        if (intent.action != Intent.ACTION_TIME_CHANGED && intent.action != Intent.ACTION_TIMEZONE_CHANGED) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                EntryPointAccessors
                    .fromApplication(appContext, MidnightWorkerEntryPoint::class.java)
                    .widgetRefresher()
                    .refresh()
                MidnightWidgetWorker.schedule(appContext) // the next midnight just moved
            } finally {
                pendingResult.finish()
            }
        }
    }
}
