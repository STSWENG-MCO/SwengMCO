package com.enil.logez.feature.activity

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.enil.logez.R
import com.enil.logez.feature.activity.service.ActivityTrackingService
import com.enil.logez.core.common.PermissionDenial
import com.enil.logez.core.common.classifyDenial
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * M21a. Unlike [com.enil.logez.feature.workout.rememberStartWorkoutSession]'s notification
 * permission (optional — logging still works if denied), GPS tracking genuinely cannot function
 * without location, so a denial calls [onDenied] instead of proceeding anyway (rev. 3 plan §2,
 * "Runtime permission request pattern" — a deliberate deviation from the notification-permission
 * "proceed either way" precedent, not an oversight).
 */
@Composable
fun rememberRequestLocationForTracking(onGranted: () -> Unit, onDenied: (PermissionDenial) -> Unit): () -> Unit {
    val context = LocalContext.current
    var showRationale by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        when {
            grants[Manifest.permission.ACCESS_FINE_LOCATION] == true -> onGranted()
            // Android 12+ lets the user grant only approximate location, which cannot measure a
            // route. It used to read as a plain denial with no hint that "Precise" was the fix.
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true -> onDenied(PermissionDenial.ApproximateOnly)
            else -> onDenied(classifyDenial(context, Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false; onDenied(PermissionDenial.Declined) },
            title = { Text(stringResource(R.string.activity_tracking_location_rationale_title)) },
            text = { Text(stringResource(R.string.activity_tracking_location_rationale_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }) { Text(stringResource(R.string.activity_tracking_location_rationale_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false; onDenied(PermissionDenial.Declined) }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            showRationale = true
        }
    }
}

fun startActivityTrackingService(context: Context) {
    val intent = Intent(context, ActivityTrackingService::class.java).setAction(ActivityTrackingService.ACTION_START)
    ContextCompat.startForegroundService(context, intent)
}

/** Called from the live-tracking screen on Finish/Cancel — the one explicit, deterministic stop path. */
fun stopActivityTrackingService(context: Context) {
    val intent = Intent(context, ActivityTrackingService::class.java).setAction(ActivityTrackingService.ACTION_STOP)
    context.startService(intent)
}
