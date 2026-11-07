package com.enil.logez

import androidx.activity.SystemBarStyle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint

/** Single-Activity Compose host (PHASE2_PLAN.md §2.1). All navigation lives inside [LogEzApp]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always light system-bar icons (2026-09-25): the UI is dark-only, but the default auto
        // style follows the system theme, so a phone in light mode drew dark status-bar icons on
        // the near-black app and they vanished.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            LogEzApp()
        }
    }
}
