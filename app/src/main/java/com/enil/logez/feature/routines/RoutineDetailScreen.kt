package com.enil.logez.feature.routines

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.pluralStringResource
import com.enil.logez.R
import com.enil.logez.core.designsystem.CircuitChip
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.activity.InterruptedTrackingDialog
import com.enil.logez.feature.workout.InProgressWorkout
import com.enil.logez.feature.workout.rememberStartWorkoutSession
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalResources
import android.content.res.Resources

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineDetailScreen(
    onBack: () -> Unit,
    onEdit: (routineId: String) -> Unit,
    onNavigateToLogger: (workoutId: String) -> Unit,
    onNavigateToActivityTracking: () -> Unit,
    onNavigateToFinish: (workoutId: String) -> Unit,
    viewModel: RoutineDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showResumeDialog by remember { mutableStateOf(false) }
    var inProgressWorkoutId by remember { mutableStateOf<String?>(null) }
    var interruptedRun by remember { mutableStateOf<Pair<String, Long>?>(null) }
    val startSession = rememberStartWorkoutSession(onNavigateToLogger)

    fun start() = scope.launch {
        when (val result = viewModel.startRoutine()) {
            is StartResult.Started -> startSession(result.workoutId)
            is StartResult.AlreadyInProgress -> { showResumeDialog = true; inProgressWorkoutId = result.workoutId }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { Text(uiState.routine?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    uiState.routine?.let { routine ->
                        Button(onClick = { onEdit(routine.id) }) { Text(stringResource(R.string.action_edit)) }
                    }
                },
            )
        },
    ) { padding ->
        val isCircuit = uiState.routine?.structure == WorkoutStructure.CIRCUIT
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // M11: circuit routines identify themselves before the exercise list — a chip plus the
            // "N rounds · M exercises" preview line, matching the routine card.
            if (isCircuit) {
                val rounds = (uiState.exercises.maxOfOrNull { it.sets.size } ?: 1).coerceAtLeast(1)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = Spacing.md).padding(top = Spacing.sm),
                ) {
                    CircuitChip()
                    Text(
                        pluralStringResource(R.plurals.routine_rounds_count, rounds, rounds) +
                            " · " +
                            pluralStringResource(R.plurals.routine_exercises_count, uiState.exercises.size, uiState.exercises.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
            Button(
                onClick = { start() },
                modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            ) {
                Text(stringResource(R.string.workout_start_routine))
            }

            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
                items(items = uiState.exercises, key = { it.routineExercise.id }) { row ->
                    LogEzCard(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
                        Column(modifier = Modifier.padding(Spacing.md)) {
                            Text(
                                row.exercise?.name.orEmpty(),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            row.sets.forEachIndexed { index, set ->
                                Text(
                                    stringResource(
                                        if (isCircuit) R.string.routine_detail_round_line else R.string.routine_detail_set_line,
                                        index + 1,
                                        formatDetailSetTargets(LocalResources.current, set, uiState.weightUnit),
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = Spacing.xxs),
                                )
                            }
                        }
                    }
                }
            }
        }
    }


    interruptedRun?.let { (workoutId, startedAt) ->
        InterruptedTrackingDialog(
            workoutId = workoutId,
            startedAt = startedAt,
            onKeptTime = { id -> interruptedRun = null; onNavigateToFinish(id) },
            onDiscarded = { interruptedRun = null },
        )
    }

    if (showResumeDialog) {
        AlertDialog(
            onDismissRequest = { showResumeDialog = false },
            title = { Text(stringResource(R.string.workout_resume_title)) },
            text = { Text(stringResource(R.string.workout_resume_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showResumeDialog = false
                    scope.launch {
                        // Only the strength branch may go through startSession: it starts
                        // WorkoutSessionService, which for a GPS run means a second foreground
                        // service next to the location one, and the wrong screen.
                        when (val inProgress = viewModel.inProgressWorkout()) {
                            null -> Unit
                            is InProgressWorkout.Strength -> startSession(inProgress.id)
                            is InProgressWorkout.LiveGpsRun -> onNavigateToActivityTracking()
                            is InProgressWorkout.InterruptedGpsRun ->
                                interruptedRun = inProgress.id to inProgress.startedAt
                        }
                    }
                }) {
                    Text(stringResource(R.string.workout_resume_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResumeDialog = false; scope.launch { startSession(viewModel.discardInProgressAndStart()) } }) {
                    Text(stringResource(R.string.workout_resume_discard_action))
                }
            },
        )
    }
}

private fun formatDetailSetTargets(res: Resources, set: com.enil.logez.core.data.entity.RoutineSetEntity, unit: com.enil.logez.core.domain.model.WeightUnit): String {
    val parts = mutableListOf<String>()
    set.targetWeightKg?.let { parts.add(com.enil.logez.core.designsystem.formatWeight(it, unit)) }
    if (set.targetRepRangeMin != null) {
        parts.add(res.getString(R.string.routine_detail_rep_range, set.targetRepRangeMin, set.targetRepRangeMax))
    } else {
        set.targetReps?.let { parts.add(res.getQuantityString(R.plurals.set_reps, it, it)) }
    }
    set.targetDurationSeconds?.let { parts.add(formatMmSs(it)) }
    set.targetDistanceMeters?.let { parts.add("${formatNum(it)}m") }
    return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
}

private fun formatNum(value: Double): String = com.enil.logez.core.designsystem.formatTargetNumber(value)
