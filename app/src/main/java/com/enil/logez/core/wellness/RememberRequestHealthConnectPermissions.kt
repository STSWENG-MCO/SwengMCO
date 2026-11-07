package com.enil.logez.core.wellness

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.health.connect.client.PermissionController

/**
 * M21e. Unlike [com.enil.logez.feature.activity.rememberRequestLocationForTracking], no separate
 * rationale dialog gates the launch: the Profile card the user taps already is the rationale. It
 * names every type requested (steps, calories and heart rate) and says the data stays on this
 * device, and Health Connect's own screen then lists each type again with its own checkbox.
 *
 * [onResult] reports whether the user granted anything at all. A partial grant is a normal,
 * working outcome (see [HealthMetricsSource.grantedTypes]), so it is not treated as a failure.
 */
@Composable
fun rememberRequestHealthConnectPermissions(
    source: HealthMetricsSource,
    onResult: (anyGranted: Boolean) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        onResult(granted.any { it in source.requiredPermissions })
    }
    return { launcher.launch(source.requiredPermissions) }
}

/**
 * Requests exactly [permissions] and reports the full set Health Connect now grants (2026-09-26).
 * The tracking screen asks for heart rate alone this way, so it can tell whether heart rate
 * itself was allowed: after a type has been refused, Health Connect answers at once without
 * showing anything, and the caller then points to Health Connect's settings instead.
 */
@Composable
fun rememberRequestHealthPermissions(
    permissions: Set<String>,
    onResult: (granted: Set<String>) -> Unit,
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        onResult(granted)
    }
    return { launcher.launch(permissions) }
}
