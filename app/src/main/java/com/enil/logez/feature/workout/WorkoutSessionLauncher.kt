package com.enil.logez.feature.workout

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import com.enil.logez.feature.workout.service.WorkoutSessionService
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * PHASE2_PLAN.md §9.3 — "requested contextually on the first Start Workout tap". Returns a
 * function that, given a freshly-started workoutId (the caller has already called
 * `WorkoutSessionController.startSession` and `WorkoutStarter`), requests POST_NOTIFICATIONS if
 * needed, starts `WorkoutSessionService`, then navigates — "never blocks logging": navigation
 * happens either way, notification permission only gates whether the ongoing notification is visible.
 */
@Composable
fun rememberStartWorkoutSession(onNavigateToLogger: (workoutId: String) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    // Saveable: the system permission dialog can recreate this screen, and losing the pending id
    // there skipped starting the service for a workout that had already begun.
    var pendingWorkoutId by rememberSaveable { mutableStateOf<String?>(null) }
    var showRationale by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) NotificationPromptMemory.markDeclined(context)
        pendingWorkoutId?.let { launchServiceAndNavigate(context, it, onNavigateToLogger) }
        pendingWorkoutId = null
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = {
                showRationale = false
                pendingWorkoutId?.let { launchServiceAndNavigate(context, it, onNavigateToLogger) }
                pendingWorkoutId = null
            },
            title = { Text(stringResource(R.string.workout_notification_permission_title)) },
            text = { Text(stringResource(R.string.workout_notification_permission_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showRationale = false
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }) { Text(stringResource(R.string.workout_notification_permission_allow)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showRationale = false
                    NotificationPromptMemory.markDeclined(context)
                    pendingWorkoutId?.let { launchServiceAndNavigate(context, it, onNavigateToLogger) }
                    pendingWorkoutId = null
                }) { Text(stringResource(R.string.workout_notification_permission_not_now)) }
            },
        )
    }

    return { workoutId ->
        // Asked once. After "Not now" or a denial the prompt stays away (it used to reappear on
        // every workout start, and after two denials its Allow button silently did nothing);
        // Settings > Workouts > Lock-screen notifications is the way back.
        if (!hasNotificationPermission(context) && !NotificationPromptMemory.wasDeclined(context)) {
            pendingWorkoutId = workoutId
            showRationale = true
        } else {
            launchServiceAndNavigate(context, workoutId, onNavigateToLogger)
        }
    }
}

private fun launchServiceAndNavigate(context: Context, workoutId: String, onNavigateToLogger: (String) -> Unit) {
    val intent = Intent(context, WorkoutSessionService::class.java).setAction(WorkoutSessionService.ACTION_START)
    ContextCompat.startForegroundService(context, intent)
    onNavigateToLogger(workoutId)
}

/** Called from the Logger screen on Finish/Discard — the one explicit, deterministic stop path (research recommendation: don't rely on the Service noticing idle state). */
fun stopWorkoutSessionService(context: Context) {
    val intent = Intent(context, WorkoutSessionService::class.java).setAction(WorkoutSessionService.ACTION_STOP)
    context.startService(intent)
}

/** POST_NOTIFICATIONS is a runtime permission only from Android 13; below that it is always granted. */
internal fun hasNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Remembers that the user said no to the lock-screen notification prompt. A plain
 * SharedPreferences flag rather than a UserSettings field: it is a one-off UI memory, not a
 * preference worth backing up or restoring onto another phone.
 */
internal object NotificationPromptMemory {
    private const val PREFS = "logez_ui_flags"
    private const val KEY_DECLINED = "notification_prompt_declined"

    fun wasDeclined(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DECLINED, false)

    fun markDeclined(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_DECLINED, true).apply()
    }
}

/** Opens this app's page in the system notification settings. */
internal fun openAppNotificationSettings(context: Context) {
    val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
