package com.enil.logez.feature.measurements

import android.Manifest
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
import com.enil.logez.core.common.PermissionDenial
import com.enil.logez.core.common.classifyDenial
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * M22a. A progress photo genuinely cannot be captured without camera access, so a denial calls
 * [onDenied] instead of proceeding anyway — same "hard requirement" shape as
 * [com.enil.logez.feature.activity.rememberRequestLocationForTracking], not the notification
 * permission's "proceed either way" precedent. Only one permission string is involved (unlike
 * location's FINE+COARSE pair), so this uses [ActivityResultContracts.RequestPermission] rather
 * than the multi-permission contract.
 */
@Composable
fun rememberRequestCameraPermission(onGranted: () -> Unit, onDenied: (PermissionDenial) -> Unit): () -> Unit {
    val context = LocalContext.current
    var showRationale by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) onGranted() else onDenied(classifyDenial(context, Manifest.permission.CAMERA))
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false; onDenied(PermissionDenial.Declined) },
            title = { Text(stringResource(R.string.measurements_camera_rationale_title)) },
            text = { Text(stringResource(R.string.measurements_camera_rationale_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }) { Text(stringResource(R.string.measurements_camera_rationale_allow)) }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false; onDenied(PermissionDenial.Declined) }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    return {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            showRationale = true
        }
    }
}
