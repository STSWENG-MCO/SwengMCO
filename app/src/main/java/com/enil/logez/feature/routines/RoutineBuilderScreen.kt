package com.enil.logez.feature.routines

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.ConfirmDialog
import com.enil.logez.core.designsystem.DragHandle
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.SyncOptimisticList
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.feature.exercises.ExercisePickerMode
import com.enil.logez.feature.exercises.ExercisePickerSheet
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutineBuilderScreen(
    onBack: () -> Unit,
    onSaved: (routineId: String) -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    onCreateExercise: (prefillName: String?) -> Unit,
    viewModel: RoutineBuilderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var pickerMode by remember { mutableStateOf<ExercisePickerMode?>(null) }
    var replaceTargetId by remember { mutableStateOf<String?>(null) }
    var restTimerTargetId by remember { mutableStateOf<String?>(null) }
    var showRemoveRoundConfirm by remember { mutableStateOf(false) }

    fun handleBack() {
        if (uiState.isDirty) showDiscardConfirm = true else onBack()
    }

    // The system back gesture/button bypasses the TopAppBar's arrow entirely (Nav's default pop),
    // so the discard-confirm needs its own interception here too — not just on the on-screen icon.
    BackHandler(onBack = ::handleBack)

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = {
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = viewModel::onTitleChange,
                        placeholder = { Text(stringResource(R.string.routine_builder_title_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { scope.launch { viewModel.save()?.let(onSaved) } }, enabled = uiState.canSave) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.supersetSelectionActive) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.routine_builder_superset_banner), modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::cancelSupersetSelection) { Text(stringResource(R.string.action_cancel)) }
                    }
                }
            }
            // Owner, 2026-09-03: while the structure choice is still live (a brand-new routine,
            // nothing added yet), it renders centered on screen instead of as the LazyColumn's
            // first item — there's nothing to scroll yet, so a plain centered Box reads better
            // than a top-anchored list row. The moment an exercise exists, or when editing a saved
            // routine (structure is immutable after creation — StructureRow's own `enabled` and
            // the ViewModel's setStructure() both already enforce that), it falls back to the
            // original top-of-list placement, unchanged.
            val structureIsLive = !uiState.isEditMode && uiState.exercises.isEmpty()
            if (!uiState.isLoading && structureIsLive) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StructureRow(
                            structure = uiState.structure,
                            enabled = true,
                            onSelect = viewModel::setStructure,
                        )
                        if (uiState.structure == WorkoutStructure.CIRCUIT) {
                            RoundsStepperCard(
                                rounds = uiState.rounds,
                                onAddRound = viewModel::addRound,
                                onRemoveRound = {
                                    if (viewModel.lastRoundHasTargets()) showRemoveRoundConfirm = true else viewModel.removeLastRound()
                                },
                            )
                        }
                    }
                }
            }
            if (!uiState.isLoading && !structureIsLive) {
                // M20a drag reorder. Reorderable's onMove fires on every hover swap and expects the
                // list to already reflect the move when it returns, but the draft round-trips through
                // MutableStateFlow -> combine -> stateIn -- so a screen-level optimistic copy absorbs
                // the swaps synchronously and the ViewModel gets exactly one reorderExercises() on
                // drop. One list instance for the screen's life (see SyncOptimisticList for why).
                val lazyListState = rememberLazyListState()
                val localExercises = remember { mutableStateListOf<RoutineExerciseDraft>().apply { addAll(uiState.exercises) } }
                val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
                    // Header items (structure row, rounds stepper) are unkeyed -> null -> ignored.
                    val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
                    val toKey = to.key as? String ?: return@rememberReorderableLazyListState
                    val fromIndex = localExercises.indexOfFirst { it.id == fromKey }
                    val toIndex = localExercises.indexOfFirst { it.id == toKey }
                    if (fromIndex >= 0 && toIndex >= 0) localExercises.add(toIndex, localExercises.removeAt(fromIndex))
                }
                SyncOptimisticList(localExercises, uiState.exercises, reorderState.isAnyItemDragging)
                val commitOrder = { viewModel.reorderExercises(localExercises.map { it.id }) }
                // Accessibility "Move up/down" (DragHandle custom actions): one slot, then commit.
                fun nudge(id: String, delta: Int) {
                    val from = localExercises.indexOfFirst { it.id == id }
                    val to = from + delta
                    if (from < 0 || to !in localExercises.indices) return
                    localExercises.add(to, localExercises.removeAt(from))
                    commitOrder()
                }
                LazyColumn(state = lazyListState, modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
                    // M11: structure choice near the title — pickable at create, greyed with a
                    // hint when editing (immutable after creation, like an exercise's type).
                    // Rendered centered above instead, once (structureIsLive gates this whole
                    // LazyColumn out until an exercise exists or the routine is being edited).
                    item {
                        StructureRow(
                            structure = uiState.structure,
                            enabled = !uiState.isEditMode,
                            onSelect = viewModel::setStructure,
                        )
                    }
                    if (uiState.structure == WorkoutStructure.CIRCUIT) {
                        item {
                            RoundsStepperCard(
                                rounds = uiState.rounds,
                                onAddRound = viewModel::addRound,
                                onRemoveRound = {
                                    if (viewModel.lastRoundHasTargets()) showRemoveRoundConfirm = true else viewModel.removeLastRound()
                                },
                            )
                        }
                    }
                    items(items = localExercises, key = { it.id }) { exercise ->
                        // animateItemModifier = Modifier: no sibling-slide animation -- the app's
                        // near-zero-motion baseline (BRAND_IDENTITY §7). Declined at the M20a
                        // checkpoint (decisions.md 2026-09-07) -- a settled decision, not an open
                        // question; re-opening it means asking the Owner again, not flipping this.
                        ReorderableItem(reorderState, key = exercise.id, animateItemModifier = Modifier) { isDragging ->
                            RoutineExerciseCard(
                                exercise = exercise,
                                isCircuit = uiState.structure == WorkoutStructure.CIRCUIT,
                                defaultRestTimerSeconds = uiState.defaultRestTimerSeconds,
                                weightUnit = uiState.weightUnit,
                                dragHandle = {
                                    val index = localExercises.indexOfFirst { it.id == exercise.id }
                                    DragHandle(
                                        modifier = Modifier.longPressDraggableHandle(onDragStopped = commitOrder),
                                        onMoveUp = if (index > 0) ({ nudge(exercise.id, -1) }) else null,
                                        onMoveDown = if (index in 0 until localExercises.lastIndex) ({ nudge(exercise.id, +1) }) else null,
                                    )
                                },
                                isDragging = isDragging,
                                supersetSelectionActive = uiState.supersetSelectionActive,
                                isSupersetSource = exercise.id == uiState.supersetSourceExerciseId,
                                viewModel = viewModel,
                                onExerciseClick = { onExerciseClick(exercise.exerciseId) },
                                onOpenReplacePicker = { replaceTargetId = exercise.id; pickerMode = ExercisePickerMode.REPLACE },
                                onRestTimerClick = { restTimerTargetId = exercise.id },
                            )
                        }
                    }
                }
            }
            // Always rendered when not loading, regardless of structureIsLive — the centered
            // empty-state branch above has nothing else on screen to add the first exercise from.
            if (!uiState.isLoading) {
                Button(
                    onClick = { pickerMode = ExercisePickerMode.ADD },
                    modifier = Modifier.fillMaxWidth().padding(Spacing.md),
                ) {
                    Text(stringResource(R.string.routine_builder_add_exercise))
                }
            }
        }
    }

    pickerMode?.let { mode ->
        ExercisePickerSheet(
            mode = mode,
            onDismiss = { pickerMode = null; replaceTargetId = null },
            onAddCommitted = { exercises -> viewModel.addExercises(exercises) },
            onExercisePicked = { exercise -> replaceTargetId?.let { viewModel.replaceExercise(it, exercise) } },
            onCreateExercise = onCreateExercise,
        )
    }

    restTimerTargetId?.let { targetId ->
        val target = uiState.exercises.find { it.id == targetId }
        if (target != null) {
            RestTimerPickerSheet(
                currentSeconds = target.restTimerSeconds,
                defaultSeconds = uiState.defaultRestTimerSeconds,
                onSelect = { viewModel.updateRestTimer(targetId, it) },
                onDismiss = { restTimerTargetId = null },
            )
        }
    }

    if (showDiscardConfirm) {
        ConfirmDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = stringResource(R.string.routine_builder_discard_title),
            body = stringResource(R.string.routine_builder_discard_body),
            confirmLabel = stringResource(R.string.routine_builder_discard_confirm),
            onConfirm = onBack,
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    // M11: stepping the round count down discards the last round's targets on every exercise —
    // confirmed only when any of those targets are actually filled in.
    if (showRemoveRoundConfirm) {
        ConfirmDialog(
            onDismissRequest = { showRemoveRoundConfirm = false },
            title = stringResource(R.string.routine_builder_remove_round_title, uiState.rounds),
            body = stringResource(R.string.routine_builder_remove_round_body),
            confirmLabel = stringResource(R.string.routine_builder_remove_round),
            onConfirm = viewModel::removeLastRound,
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

/**
 * M11 structure choice — square icon cards (Owner, 2026-09-04: replaced the v4.0 pill chips with
 * a hand-drawn mockup's label-plus-diagram cards; label at top, a small original line drawing
 * below — straight lines with a down arrow for Standard, the same lines with a loop arrow for
 * Circuit). Greyed with the immutable hint when editing, same as the chips before them.
 */
@Composable
private fun StructureRow(structure: WorkoutStructure, enabled: Boolean, onSelect: (WorkoutStructure) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Text(
            stringResource(R.string.routine_structure_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            StructureCard(
                labelRes = R.string.routine_structure_regular,
                selected = structure == WorkoutStructure.REGULAR,
                enabled = enabled,
                onClick = { onSelect(WorkoutStructure.REGULAR) },
                modifier = Modifier.weight(1f),
                icon = { tint -> StandardFlowIcon(tint, Modifier.fillMaxSize()) },
            )
            StructureCard(
                labelRes = R.string.routine_structure_circuit,
                selected = structure == WorkoutStructure.CIRCUIT,
                enabled = enabled,
                onClick = { onSelect(WorkoutStructure.CIRCUIT) },
                modifier = Modifier.weight(1f),
                icon = { tint -> CircuitLoopIcon(tint, Modifier.fillMaxSize()) },
            )
        }
        if (!enabled) {
            Text(
                stringResource(R.string.routine_structure_immutable_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/** One structure option card: bold caps label top-left, an original line-diagram icon filling the
 * rest. Selection/disabled fill and border language carried over unchanged from the old chip. */
@Composable
private fun StructureCard(
    @StringRes labelRes: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    icon: @Composable (tint: Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radius.card)
    val primary = MaterialTheme.colorScheme.primary
    val borderColor = when {
        selected && enabled -> primary
        selected -> primary.copy(alpha = 0.38f)
        else -> MaterialTheme.colorScheme.outline
    }
    val contentColor = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .aspectRatio(0.95f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(if (selected) 2.dp else 1.dp, borderColor, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(Spacing.md),
    ) {
        Text(
            stringResource(labelRes).uppercase(currentLocale()),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.02.em),
            color = contentColor,
        )
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f)) {
                icon(contentColor)
            }
        }
    }
}

/** Standard: a straight stack of set rows with a single downward arrow — logged top to bottom, once. */
@Composable
private fun StandardFlowIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.09f
        val lineLeft = w * 0.06f
        val lineRight = w * 0.62f
        val rowCount = 5
        val topPad = h * 0.06f
        val bottomPad = h * 0.06f
        val rowGap = (h - topPad - bottomPad) / (rowCount - 1)
        for (i in 0 until rowCount) {
            val y = topPad + rowGap * i
            drawLine(color = tint, start = Offset(lineLeft, y), end = Offset(lineRight, y), strokeWidth = strokeW, cap = StrokeCap.Round)
        }
        val arrowX = w * 0.84f
        val arrowBottom = h - bottomPad
        drawLine(
            color = tint,
            start = Offset(arrowX, topPad),
            end = Offset(arrowX, arrowBottom - w * 0.08f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round,
        )
        val headHalf = w * 0.11f
        val headPath = Path().apply {
            moveTo(arrowX - headHalf, arrowBottom - w * 0.16f)
            lineTo(arrowX + headHalf, arrowBottom - w * 0.16f)
            lineTo(arrowX, arrowBottom)
            close()
        }
        drawPath(headPath, color = tint)
    }
}

/** Circuit: the same set rows, but the arrow loops back around — the same rows repeat for N rounds. */
@Composable
private fun CircuitLoopIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val strokeW = w * 0.09f
        val lineLeft = w * 0.08f
        val lineRight = w * 0.50f
        val rowCount = 4
        val topPad = h * 0.20f
        val bottomPad = h * 0.20f
        val rowGap = (h - topPad - bottomPad) / (rowCount - 1)
        for (i in 0 until rowCount) {
            val y = topPad + rowGap * i
            drawLine(color = tint, start = Offset(lineLeft, y), end = Offset(lineRight, y), strokeWidth = strokeW, cap = StrokeCap.Round)
        }
        val loopR = w * 0.20f
        val loopCx = w * 0.78f
        val loopCy = h * 0.5f
        val startAngle = -60f
        val sweepAngle = 280f
        drawArc(
            color = tint,
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = Offset(loopCx - loopR, loopCy - loopR),
            size = Size(loopR * 2, loopR * 2),
            style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )
        val endAngleRad = Math.toRadians((startAngle + sweepAngle).toDouble())
        val endX = loopCx + loopR * cos(endAngleRad).toFloat()
        val endY = loopCy + loopR * sin(endAngleRad).toFloat()
        val headSize = w * 0.10f
        val headPath = Path().apply {
            moveTo(endX - headSize, endY - headSize * 0.4f)
            lineTo(endX + headSize * 0.3f, endY - headSize)
            lineTo(endX + headSize * 0.5f, endY + headSize * 0.5f)
            close()
        }
        drawPath(headPath, color = tint)
    }
}

/** M11: routine-level round count for circuits — the one structural control a circuit template has. */
@Composable
private fun RoundsStepperCard(rounds: Int, onAddRound: () -> Unit, onRemoveRound: () -> Unit) {
    LogEzCard(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.routine_rounds_label),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemoveRound, enabled = rounds > 1) {
                Icon(Icons.Outlined.Remove, contentDescription = stringResource(R.string.routine_builder_remove_round))
            }
            Text(rounds.toString(), style = LogEzMono.dataLarge, modifier = Modifier.padding(horizontal = Spacing.sm))
            IconButton(onClick = onAddRound) {
                Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.routine_builder_add_round))
            }
        }
    }
}
