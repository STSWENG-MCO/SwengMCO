package com.enil.logez.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Runs [onResume] on every ON_RESUME the screen sees — including the replayed one a newly added
 * observer receives — making it the screen's *single* load trigger. Screens using this must NOT
 * also load in the ViewModel's `init`: with init-loading, the replayed first ON_RESUME double-runs
 * the full load on entry; but skipping the first event instead is worse — forward navigation
 * disposes the composition, so returning from a child screen re-creates the observer (first
 * again) while the surviving ViewModel's init does NOT re-run, and the re-entry refresh this
 * composable exists for is silently swallowed. One trigger, owned here, covers every case exactly
 * once: first entry, back from a child screen, tab return, process-death restore, foregrounding.
 */
@Composable
fun RefreshOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
