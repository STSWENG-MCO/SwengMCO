package com.enil.logez.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.enil.logez.feature.workout.finish.HeartRateCard
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.CircuitChip
import com.enil.logez.core.designsystem.ConfirmDialog
import com.enil.logez.core.designsystem.Danger500
import com.enil.logez.core.designsystem.Gold500
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.StatCell
import com.enil.logez.core.designsystem.SupersetPalette
import com.enil.logez.core.designsystem.Warning500
import com.enil.logez.core.designsystem.formatTwoDecimals
import com.enil.logez.core.designsystem.formatWeight
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.feature.activity.map.RouteMapView
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.finish.labelRes
import com.enil.logez.feature.activity.InterruptedTrackingDialog
import com.enil.logez.feature.workout.InProgressWorkout
import com.enil.logez.feature.workout.rememberStartWorkoutSession
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import com.enil.logez.core.designsystem.rememberClockTimeFormatter
import androidx.compose.ui.platform.LocalResources
import android.content.res.Resources

/** PHASE2_PLAN.md §5.2 "Workout Detail": read-only record of one completed workout. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailScreen(
    onBack: () -> Unit,
    onEdit: (workoutId: String) -> Unit,
    onSavedAsRoutine: (routineId: String) -> Unit,
    onNavigateToLogger: (workoutId: String) -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    onNavigateToActivityTracking: () -> Unit,
    onNavigateToFinish: (workoutId: String) -> Unit,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val startSession = rememberStartWorkoutSession(onNavigateToLogger)

    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showResumeDialog by remember { mutableStateOf(false) }
    var conflictingWorkoutId by remember { mutableStateOf<String?>(null) }
    var interruptedRun by remember { mutableStateOf<Pair<String, Long>?>(null) }

    LaunchedEffect(uiState.isMissing) { if (uiState.isMissing) onBack() }

    // Edit mode saves in place and pops straight back here, so the data this screen loaded in
    // init is stale the moment it returns. Re-read on every RESUME rather than on first
    // composition only.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun copyWorkout() = scope.launch {
        menuExpanded = false
        when (val result = viewModel.startCopy()) {
            is StartResult.Started -> startSession(result.workoutId)
            is StartResult.AlreadyInProgress -> { showResumeDialog = true; conflictingWorkoutId = result.workoutId }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { Text(uiState.workout?.title.orEmpty(), color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val loadedWorkoutId = uiState.workout?.id
                    if (loadedWorkoutId != null) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_detail_edit)) },
                                onClick = { menuExpanded = false; onEdit(loadedWorkoutId) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_detail_copy)) },
                                onClick = { copyWorkout() },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history_detail_save_as_routine)) },
                                onClick = {
                                    menuExpanded = false
                                    scope.launch { viewModel.saveAsRoutine()?.let(onSavedAsRoutine) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = { menuExpanded = false; showDeleteConfirm = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val workout = uiState.workout
        if (workout == null || uiState.isLoading) return@Scaffold
        val isCircuit = workout.structure == WorkoutStructure.CIRCUIT
        // M11: post-purge round count — the largest surviving round number; unequal blocks (a
        // skipped exercise in some round) simply produce rounds with fewer entries.
        val detailRounds = if (isCircuit) buildDetailRounds(uiState.exerciseBlocks) else emptyList()

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.md)) {
            item {
                Column(modifier = Modifier.padding(top = Spacing.md)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val started = Instant.ofEpochMilli(workout.startedAt).atZone(ZoneId.systemDefault())
                        Text(
                            stringResource(
                                R.string.history_card_date_time,
                                started.format(DateTimeFormatter.ofPattern("d MMM yyyy", currentLocale())),
                                started.format(rememberClockTimeFormatter()),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (isCircuit) CircuitChip()
                    }
                    if (!workout.notes.isNullOrBlank()) {
                        Text(workout.notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.sm))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        DetailStatCell(stringResource(R.string.summary_duration), formatDetailDuration(uiState.durationSeconds))
                        // A GPS-tracked walk/run never logged weight -- "0kg Volume" would be noise
                        // next to its real distance, so the cell is gated on whether it was tracked.
                        if (uiState.hasVolume) DetailStatCell(stringResource(R.string.summary_volume), formatDetailVolume(uiState.volumeKg, uiState.weightUnit))
                        DetailStatCell(stringResource(R.string.summary_sets), uiState.completedSetCount.toString())
                        if (uiState.hasDistance) DetailStatCell(stringResource(R.string.summary_distance), formatDetailDistance(uiState.distanceMeters, uiState.distanceUnit))
                        if (isCircuit) DetailStatCell(stringResource(R.string.routine_rounds_label), detailRounds.size.toString())
                        if (uiState.hasRecords) DetailStatCell(stringResource(R.string.summary_prs_header), "", icon = Icons.Outlined.EmojiEvents)
                    }
                }
            }

            // M21b/c: the offline map with the recorded GPS route drawn on it, fit to the route's
            // bounds -- only rendered for a GPS-tracked workout (uiState.hasRoute), never a strength one.
            if (uiState.hasRoute) {
                item {
                    RouteCard(routePoints = uiState.routePoints)
                }
            }

            // Heart rate saved for this workout, the same card the walk/run summary shows. It can
            // appear on a later visit: opening the workout asks Health Connect for readings the
            // watch synced after Save (2026-09-26).
            uiState.heartRateSummary?.let { summary ->
                item {
                    HeartRateCard(summary, workout.startedAt, Modifier.padding(vertical = Spacing.xs))
                }
            }

            if (isCircuit) {
                // M11: round-grouped record — mirrors the circuit logger's view of the same rows.
                items(items = detailRounds, key = { it.roundNumber }) { round ->
                    DetailRoundCard(round, uiState.weightUnit, onExerciseClick)
                }
            } else {
                items(items = uiState.exerciseBlocks, key = { it.workoutExercise.id }) { block ->
                    ExerciseBlockCard(block, uiState.weightUnit, onExerciseClick)
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
                TextButton(onClick = {
                    showResumeDialog = false
                    scope.launch { startSession(viewModel.discardInProgressAndStartCopy()) }
                }) { Text(stringResource(R.string.workout_resume_discard_action)) }
            },
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = stringResource(R.string.history_detail_delete_title),
            body = stringResource(R.string.history_detail_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(onBack) },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

@Composable
private fun DetailStatCell(label: String, value: String, icon: ImageVector? = null) {
    StatCell(
        value = value,
        label = label,
        horizontalAlignment = Alignment.CenterHorizontally,
        valueStyle = LogEzMono.dataLarge,
        labelStyle = MaterialTheme.typography.labelSmall,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        icon = icon,
        iconTint = Gold500,
    )
}

/** M11: one round of a completed circuit — the exercises' rows at the same orderIndex, sequence order. */
private data class DetailRound(val roundNumber: Int, val entries: List<DetailRoundEntry>)
private data class DetailRoundEntry(val block: DetailExerciseBlock, val set: DetailSetRow?)

/**
 * Groups a circuit workout's post-purge blocks round-first, keyed by each row's `orderIndex`
 * (== its round), NOT its list position: the finish flow's uncompleted-set purge deletes skipped
 * rows without re-indexing, so a skipped MIDDLE round leaves a gap ({0,2}) that positional
 * grouping would silently shift — attributing round 3's performance to "ROUND 2" forever.
 * Defensive by construction: a block with no surviving row at a round contributes a null
 * (rendered "—") entry — gaps and unequal counts must render, never crash.
 */
private fun buildDetailRounds(blocks: List<DetailExerciseBlock>): List<DetailRound> {
    val rounds = blocks.maxOfOrNull { block -> block.sets.maxOfOrNull { it.orderIndex + 1 } ?: 0 } ?: 0
    return (0 until rounds).map { roundIndex ->
        DetailRound(
            roundNumber = roundIndex + 1,
            entries = blocks.map { block -> DetailRoundEntry(block, block.sets.find { it.orderIndex == roundIndex }) },
        )
    }
}

@Composable
private fun DetailRoundCard(round: DetailRound, weightUnit: WeightUnit, onExerciseClick: (String) -> Unit) {
    LogEzCard(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.workout_round_header, round.roundNumber).uppercase(currentLocale()),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
            }
            round.entries.forEach { entry ->
                val exercise = entry.block.exercise
                Text(
                    exercise?.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = (if (exercise != null) Modifier.clickable { onExerciseClick(exercise.id) } else Modifier)
                        .padding(top = Spacing.sm),
                )
                val set = entry.set
                if (set == null) {
                    // Defensive slot: no surviving row for this exercise in this round.
                    Text("—", style = LogEzMono.dataMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    DetailSetRowView(round.roundNumber, set, exercise?.exerciseType, weightUnit, positionLabel = false)
                }
            }
        }
    }
}

/** M21b/c: the offline Metro Manila map with the recorded GPS route drawn on it, camera fit to the route's bounds. */
@Composable
private fun RouteCard(routePoints: List<Pair<Double, Double>>) {
    LogEzCard(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Text(stringResource(R.string.workout_detail_route_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            RouteMapView(
                routePoints = routePoints,
                followLatest = false,
                modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = Spacing.sm).clip(RoundedCornerShape(Radius.sm)),
            )
        }
    }
}

@Composable
private fun ExerciseBlockCard(block: DetailExerciseBlock, weightUnit: WeightUnit, onExerciseClick: (String) -> Unit) {
    val supersetColor = block.workoutExercise.supersetGroup?.let { SupersetPalette[it % SupersetPalette.size] }
    LogEzCard(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            if (supersetColor != null) {
                Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(supersetColor))
            }
            Column(modifier = Modifier.padding(Spacing.md)) {
                val exercise = block.exercise
                Text(
                    exercise?.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = if (exercise != null) Modifier.clickable { onExerciseClick(exercise.id) } else Modifier,
                )
                if (!block.workoutExercise.notes.isNullOrBlank()) {
                    Text(
                        block.workoutExercise.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                }
                block.sets.forEachIndexed { index, set ->
                    DetailSetRowView(index + 1, set, block.exercise?.exerciseType, weightUnit)
                }
            }
        }
    }
}

@Composable
private fun DetailSetRowView(position: Int, set: DetailSetRow, exerciseType: ExerciseType?, weightUnit: WeightUnit, positionLabel: Boolean = true) {
    val (badgeLabel, badgeColor) = when (set.setType) {
        SetType.NORMAL -> position.toString() to MaterialTheme.colorScheme.onSurface
        SetType.WARMUP -> "W" to Warning500
        SetType.FAILURE -> "F" to Danger500
        SetType.DROPSET -> "D" to SupersetPalette[4]
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (set.setType == SetType.NORMAL) Color.Transparent else badgeColor.copy(alpha = 0.15f),
        ) {
            Box(modifier = Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Text(badgeLabel, color = badgeColor, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            formatDetailSetValue(LocalResources.current, position, set, exerciseType, weightUnit, positionLabel),
            style = LogEzMono.dataMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = Spacing.sm).weight(1f),
        )
        if (set.pr != null) {
            Icon(
                Icons.Outlined.EmojiEvents,
                contentDescription = stringResource(set.pr.prType.labelRes()),
                tint = Gold500,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun formatDetailSetValue(res: Resources, position: Int, set: DetailSetRow, exerciseType: ExerciseType?, weightUnit: WeightUnit, positionLabel: Boolean = true): String {
    // Plurals, not "$it reps": a one-rep set used to read "1 reps".
    fun reps(n: Int) = res.getQuantityString(R.plurals.set_reps, n, n)
    if (!set.isCompleted) return "—"
    val parts = mutableListOf<String>()
    when (exerciseType) {
        ExerciseType.WEIGHT_REPS, ExerciseType.BODYWEIGHT_WEIGHTED, ExerciseType.BODYWEIGHT_ASSISTED -> {
            set.weightKg?.let { parts.add(formatWeight(it, weightUnit)) }
            set.reps?.let { parts.add(reps(it)) }
        }
        ExerciseType.REPS_ONLY -> set.reps?.let { parts.add(reps(it)) }
        ExerciseType.DURATION, ExerciseType.FLOORS_DURATION, ExerciseType.STEPS_DURATION -> {
            set.durationSeconds?.let { parts.add(formatDetailMmSs(it)) }
            set.customMetric?.let { parts.add(formatDetailNum(it)) }
        }
        ExerciseType.WEIGHT_DURATION -> {
            set.weightKg?.let { parts.add(formatWeight(it, weightUnit)) }
            set.durationSeconds?.let { parts.add(formatDetailMmSs(it)) }
        }
        ExerciseType.DISTANCE_DURATION -> {
            set.distanceMeters?.let { parts.add("${formatDetailNum(it)}m") }
            set.durationSeconds?.let { parts.add(formatDetailMmSs(it)) }
        }
        ExerciseType.WEIGHT_DISTANCE -> {
            set.weightKg?.let { parts.add(formatWeight(it, weightUnit)) }
            set.distanceMeters?.let { parts.add("${formatDetailNum(it)}m") }
        }
        null -> Unit
    }
    set.rpe?.let { parts.add("@$it") }
    if (parts.isEmpty()) return "—"
    // M11 circuit rounds carry the round number in the card header, so their rows skip the prefix.
    return if (positionLabel) res.getString(R.string.detail_set_line, position, parts.joinToString(" · ")) else parts.joinToString(" · ")
}

// Used to fall back to the raw Double.toString() for a non-whole value -- harmless for a set's
// typed-in customMetric, but a GPS-accumulated distanceMeters sum routinely carries a long
// floating-point tail (e.g. "3247.8921336m"), which shipped straight to this per-set row. Capped
// at two decimals to match the walk/run precision convention (Owner request, 2026-09-23).
private fun formatDetailNum(value: Double): String = formatTwoDecimals(value)

private fun formatDetailMmSs(totalSeconds: Int): String = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

private fun formatDetailDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatDetailVolume(kg: Double, unit: WeightUnit): String = formatWeight(kg, unit)

private fun formatDetailDistance(meters: Double, unit: DistanceUnit): String = com.enil.logez.core.designsystem.formatDistance(meters, unit)
