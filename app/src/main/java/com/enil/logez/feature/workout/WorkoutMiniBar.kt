package com.enil.logez.feature.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Spacing
import kotlinx.coroutines.flow.Flow

/** PHASE2_PLAN.md §5.1.3 — the collapsed live-workout surface shown on every tab root while a workout is IN_PROGRESS. */
@Composable
fun WorkoutMiniBar(
    onExpand: (workoutId: String) -> Unit,
    onExpandLiveTracking: () -> Unit,
    onInterruptedRun: (workoutId: String, startedAt: Long) -> Unit,
    viewModel: MiniBarViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val workoutId = uiState.workoutId
    if (!uiState.visible || workoutId == null) return

    val expandStrength = rememberStartWorkoutSession(onExpand)
    // Only the strength branch may go through rememberStartWorkoutSession: it starts
    // WorkoutSessionService, which for a GPS run means a second foreground service next to the
    // location one, and the wrong screen.
    val tap: () -> Unit = when {
        uiState.kind == WorkoutKind.STRENGTH -> ({ expandStrength(workoutId) })
        uiState.gpsSessionAlive -> onExpandLiveTracking
        else -> ({ onInterruptedRun(workoutId, uiState.startedAt) })
    }

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = tap),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(uiState.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                MiniBarStatsText(viewModel.elapsedSecondsFlow, viewModel.restRemainingMillisFlow)
            }
            TextButton(onClick = tap) { Text(stringResource(R.string.workout_mini_bar_finish)) }
        }
    }
}

/** Leaf composable (spine rule) — only these digits recompose every second, not the whole bar/Scaffold. */
@Composable
private fun MiniBarStatsText(elapsedSecondsFlow: Flow<Long>, restRemainingMillisFlow: Flow<Long?>) {
    val elapsedSeconds by elapsedSecondsFlow.collectAsStateWithLifecycle(0L)
    val restRemainingMillis by restRemainingMillisFlow.collectAsStateWithLifecycle(null)
    val stats = restRemainingMillis?.let { millis ->
        "${formatMiniBarTime(elapsedSeconds)} · ${stringResource(R.string.workout_rest_timer_label)} ${formatMiniBarTime((millis + 999) / 1000)}"
    } ?: formatMiniBarTime(elapsedSeconds)
    Text(stats, style = LogEzMono.dataSmall, maxLines = 1)
}

private fun formatMiniBarTime(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
