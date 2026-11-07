package com.enil.logez.feature.activity

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.enil.logez.R
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.launch
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.designsystem.rememberClockTimeFormatter

@HiltViewModel
class InterruptedTrackingViewModel @Inject constructor(
    private val recovery: InterruptedTrackingRecovery,
) : ViewModel() {
    suspend fun keepElapsedTime(workoutId: String): Boolean = recovery.keepElapsedTime(workoutId)

    suspend fun discard(workoutId: String) = recovery.discard(workoutId)
}

/**
 * Shown when a GPS run's process died mid-track. Two real choices and no plain cancel, so this is
 * a direct [AlertDialog] rather than `ConfirmDialog` — whose own contract excludes this shape —
 * matching the Resume/Discard dialog on the Workout tab.
 *
 * Not dismissible by tapping outside: the row is in a state nothing else in the app can resolve,
 * so leaving it untouched would strand an IN_PROGRESS workout the user keeps being asked about.
 */
@Composable
fun InterruptedTrackingDialog(
    workoutId: String,
    startedAt: Long,
    onKeptTime: (String) -> Unit,
    onDiscarded: () -> Unit,
    viewModel: InterruptedTrackingViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val zone = ZoneId.systemDefault()
    val started = Instant.ofEpochMilli(startedAt).atZone(zone)
    val startedLabel = started.format(rememberClockTimeFormatter())
    val elapsedLabel = formatCoarseElapsed(System.currentTimeMillis() - startedAt)

    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.activity_tracking_interrupted_title)) },
        text = {
            Text(stringResource(R.string.activity_tracking_interrupted_body, startedLabel, elapsedLabel))
        },
        confirmButton = {
            TextButton(onClick = {
                scope.launch {
                    if (viewModel.keepElapsedTime(workoutId)) onKeptTime(workoutId) else onDiscarded()
                }
            }) {
                Text(stringResource(R.string.activity_tracking_interrupted_keep_time))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                scope.launch {
                    viewModel.discard(workoutId)
                    onDiscarded()
                }
            }) {
                Text(
                    text = stringResource(R.string.activity_tracking_interrupted_discard),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
    )
}

/** "1h 12m" / "8m" — enough for the user to recognise the session, not a precise readout. */
private fun formatCoarseElapsed(millis: Long): String {
    val totalMinutes = (millis / 60_000).coerceAtLeast(0)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
