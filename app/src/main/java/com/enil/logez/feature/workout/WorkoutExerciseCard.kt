package com.enil.logez.feature.workout

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.Danger500
import com.enil.logez.core.designsystem.Elevation
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.boxedFieldColors
import com.enil.logez.core.designsystem.formatTargetNumber
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.SetTable
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.SupersetPalette
import com.enil.logez.core.designsystem.Warning500
import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.RpeScale
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.TargetField
import com.enil.logez.core.domain.model.targetFields
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import com.enil.logez.core.designsystem.parseDecimalInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.enil.logez.core.designsystem.Warning300

/** REPS never needs more than 1-2 digits, so it's the weighted column that shrinks to fund the
 * wider PREVIOUS cell (Owner, 2026-09-03) — KG/TIME/DISTANCE keep the full share so 5-char values
 * like "154.3" stay whole. Shared by the header row and [SetRow] so the grid stays aligned. */
private const val REPS_COLUMN_WEIGHT = 0.7f

/** One `workout_exercises` card (PHASE2_PLAN.md §5.1.3): header, notes, PREVIOUS-aware set table with check-off. */
@Composable
internal fun WorkoutExerciseCard(
    exercise: WorkoutExerciseUiModel,
    /** M20a: the long-press drag handle, built by the caller inside its `ReorderableItem` scope. */
    dragHandle: @Composable () -> Unit,
    isDragging: Boolean,
    supersetSelectionActive: Boolean,
    isSupersetSource: Boolean,
    callbacks: WorkoutCallbacks,
    onExerciseClick: () -> Unit,
    onOpenReplacePicker: () -> Unit,
    onRpeChange: (setId: String, rpe: Double?) -> Unit = { _, _ -> },
    inlineTimerSetId: String? = null,
    inlineTimerSecondsFlow: Flow<Int?> = emptyFlow(),
    onStartInlineTimer: (setId: String) -> Unit = {},
    onStopInlineTimer: (setId: String) -> Unit = {},
    config: WorkoutLoggerDisplayConfig = WorkoutLoggerDisplayConfig(),
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val supersetColor = exercise.supersetGroup?.let { SupersetPalette[it % SupersetPalette.size] }

    LogEzCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.sm)
            .let { m -> if (supersetSelectionActive && !isSupersetSource) m.clickable { callbacks.onConfirmSupersetTarget(exercise.id) } else m },
        elevation = if (isDragging) Elevation.dragging else Elevation.card,
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            if (supersetColor != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = Spacing.xs)) {
                    Box(modifier = Modifier.size(10.dp).background(supersetColor, CircleShape))
                    Text(
                        stringResource(R.string.routine_builder_superset_chip),
                        style = MaterialTheme.typography.labelSmall,
                        color = supersetColor,
                        modifier = Modifier.padding(start = Spacing.xxs),
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                dragHandle()
                Text(
                    exercise.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).let { m -> if (supersetSelectionActive) m else m.clickable(onClick = onExerciseClick) },
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_replace)) }, onClick = { menuExpanded = false; onOpenReplacePicker() })
                        if (exercise.supersetGroup == null) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_add_to_superset)) }, onClick = { menuExpanded = false; callbacks.onStartSupersetSelection(exercise.id) })
                        } else {
                            DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_remove_from_superset)) }, onClick = { menuExpanded = false; callbacks.onRemoveFromSuperset(exercise.id) })
                        }
                        // M18 §5.1.6 "Add Warm Up Sets": only for WEIGHT-field exercises with the
                        // setting on (the gate arrives force-false for circuits — M11 invariant).
                        // Disabled — not hidden — when no working (non-WARMUP) set has a weight
                        // yet: the item teaches the feature exists, the disable teaches why it
                        // can't run. Mirrors the ViewModel's own working-weight guard.
                        // BODYWEIGHT_ASSISTED is excluded even though it carries WEIGHT: there
                        // weightKg means assistance, so the 40/60/80% ladder would generate
                        // warm-ups HARDER than the working set (inverted ramp).
                        if (config.warmupCalculatorEnabled &&
                            exercise.exerciseType != ExerciseType.BODYWEIGHT_ASSISTED &&
                            TargetField.WEIGHT in exercise.exerciseType.targetFields()
                        ) {
                            val hasWorkingWeight = exercise.sets.firstOrNull { it.setType != SetType.WARMUP }?.weightKg != null
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.workout_add_warmup_sets)) },
                                enabled = hasWorkingWeight,
                                onClick = { menuExpanded = false; callbacks.onAddWarmupSets(exercise.id) },
                            )
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_remove_exercise)) }, onClick = { menuExpanded = false; callbacks.onRemoveExercise(exercise.id) })
                    }
                }
            }

            var notesText by remember(exercise.id) { mutableStateOf(exercise.notes) }
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it; callbacks.onUpdateNotes(exercise.id, it) },
                placeholder = { Text(stringResource(R.string.routine_builder_notes_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
            )

            SetTable(
                exercise = exercise,
                callbacks = callbacks,
                inlineTimerSetId = inlineTimerSetId,
                inlineTimerSecondsFlow = inlineTimerSecondsFlow,
                onStartInlineTimer = onStartInlineTimer,
                onStopInlineTimer = onStopInlineTimer,
                onRpeChange = onRpeChange,
                config = config,
            )

            TextButton(onClick = { callbacks.onAddSet(exercise.id) }, modifier = Modifier.padding(top = Spacing.xs)) {
                Text(stringResource(R.string.routine_builder_add_set))
            }
        }
    }
}

/**
 * §5.1.4 — countdown bar below the notes area, inside the triggering exercise's card. Leaf
 * composable (spine rule): only this bar recomposes every second, driven by its own flow
 * collection — the parent Card/Screen never reads a per-second value.
 */
@Composable
internal fun RestTimerBar(remainingMillisFlow: Flow<Long?>, onMinus15: () -> Unit, onPlus15: () -> Unit, onSkip: () -> Unit) {
    val remainingMillis by remainingMillisFlow.collectAsStateWithLifecycle(null)
    val remainingSeconds = remainingMillis?.let { (it + 999) / 1000 } ?: return
    Column(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${stringResource(R.string.workout_rest_timer_label)} ${"%d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)}",
                style = LogEzMono.dataMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onMinus15) { Text(stringResource(R.string.workout_rest_timer_minus_15)) }
            TextButton(onClick = onPlus15) { Text(stringResource(R.string.workout_rest_timer_plus_15)) }
            TextButton(onClick = onSkip) { Text(stringResource(R.string.workout_rest_timer_skip)) }
        }
        val fraction = ((remainingSeconds.coerceAtMost(300)) / 300f).coerceIn(0f, 1f)
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SetTable(
    exercise: WorkoutExerciseUiModel,
    callbacks: WorkoutCallbacks,
    inlineTimerSetId: String?,
    inlineTimerSecondsFlow: Flow<Int?>,
    onStartInlineTimer: (setId: String) -> Unit,
    onStopInlineTimer: (setId: String) -> Unit,
    onRpeChange: (setId: String, rpe: Double?) -> Unit,
    config: WorkoutLoggerDisplayConfig = WorkoutLoggerDisplayConfig(),
) {
    val fields = exercise.exerciseType.targetFields()
    val showInlineTimer = config.inlineTimerEnabled && TargetField.DURATION in fields
    val showCustomMetric = exercise.exerciseType == ExerciseType.FLOORS_DURATION || exercise.exerciseType == ExerciseType.STEPS_DURATION
    // §5.1.7: "the RPE column appears only when the setting is on and only for rep-based types" —
    // never for the routine builder (which shares none of this UI) and never for duration/distance
    // types, where RPE doesn't apply.
    val showRpe = config.rpeTrackingEnabled && TargetField.REPS in fields
    // §5.1.5: the Plate Calculator affordance exists only for BARBELL exercises with the setting
    // on — "assisted/weighted bodyweight exercises never show the button (equipment ≠ BARBELL)".
    val showPlateCalculator = config.plateCalculator.enabled && exercise.equipment == Equipment.BARBELL && TargetField.WEIGHT in fields
    Column(modifier = Modifier.padding(top = Spacing.sm)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            HeaderCell(stringResource(R.string.routine_builder_col_set), width = SetTable.setCell, textAlign = TextAlign.Center)
            HeaderCell(stringResource(R.string.workout_col_previous), width = SetTable.previousCell, textAlign = TextAlign.Center)
            if (showCustomMetric) HeaderCell(stringResource(R.string.workout_col_custom_metric), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            if (TargetField.WEIGHT in fields) HeaderCell(stringResource(weightHeaderRes(config.weightUnit)), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            // Mirrors the row's trailing calculator button so the KG header stays over its cell.
            if (showPlateCalculator) Spacer(modifier = Modifier.width(SetTable.plateCalcCell))
            // REPS gets a smaller weight than KG/TIME/DISTANCE (Owner, 2026-09-03): it only ever
            // needs 1-2 digits, so it's the column that gives up width to the widened PREVIOUS
            // cell, leaving KG room for 5-char values like "154.3" (must stay whole — M18 history).
            if (TargetField.REPS in fields) HeaderCell(stringResource(R.string.routine_builder_col_reps), modifier = Modifier.weight(REPS_COLUMN_WEIGHT), textAlign = TextAlign.Center)
            if (TargetField.DURATION in fields) HeaderCell(stringResource(R.string.routine_builder_col_time), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            if (TargetField.DISTANCE in fields) HeaderCell(stringResource(R.string.routine_builder_col_distance), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            if (showRpe) HeaderCell(stringResource(R.string.workout_col_rpe), width = SetTable.rpeCell, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.width(SetTable.checkCell))
        }
        exercise.sets.forEachIndexed { index, set ->
            Column {
                SetRow(
                    index = index,
                    set = set,
                    fields = fields,
                    showCustomMetric = showCustomMetric,
                    onSetTypeChange = { type -> callbacks.onUpdateSetType(exercise.id, set.id, type) },
                    onRemove = { callbacks.onRemoveSet(exercise.id, set.id) },
                    onWeightChange = { callbacks.onUpdateWeight(exercise.id, set.id, it) },
                    onRepsChange = { callbacks.onUpdateReps(exercise.id, set.id, it) },
                    onDurationChange = { callbacks.onUpdateDuration(exercise.id, set.id, it) },
                    onDistanceChange = { callbacks.onUpdateDistance(exercise.id, set.id, it) },
                    onCustomMetricChange = { callbacks.onUpdateCustomMetric(exercise.id, set.id, it) },
                    onToggleCheck = { callbacks.onToggleCheck(exercise.id, set.id) },
                    showInlineTimer = showInlineTimer,
                    inlineTimerRunning = inlineTimerSetId == set.id,
                    inlineTimerSecondsFlow = inlineTimerSecondsFlow,
                    onStartInlineTimer = { onStartInlineTimer(set.id) },
                    onStopInlineTimer = { onStopInlineTimer(set.id) },
                    showRpe = showRpe,
                    onRpeChange = { rpe -> onRpeChange(set.id, rpe) },
                    showPlateCalculator = showPlateCalculator,
                    onOpenPlateCalculator = { callbacks.onOpenPlateCalculator(exercise.id, set.id, set.weightKg) },
                    config = config,
                )
                if (set.failureError) {
                    Text(
                        stringResource(R.string.workout_failure_error),
                        color = Danger500,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = SetTable.setCell, bottom = Spacing.xxs),
                    )
                }
            }
        }
    }
}

@Composable
internal fun HeaderCell(label: String, modifier: Modifier = Modifier, width: androidx.compose.ui.unit.Dp? = null, textAlign: TextAlign? = null) {
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        // Cells are sized so every label fits whole (SetTable); this is the backstop that turns a
        // future regression into an ellipsis instead of a mid-word break ("PREVIO/US").
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (width != null) modifier.width(width) else modifier,
    )
}

@Composable
internal fun SetRow(
    index: Int,
    set: WorkoutSetUiModel,
    fields: Set<TargetField>,
    showCustomMetric: Boolean,
    onSetTypeChange: (SetType) -> Unit,
    onRemove: () -> Unit,
    onWeightChange: (Double?) -> Unit,
    onRepsChange: (Int?) -> Unit,
    onDurationChange: (Int?) -> Unit,
    onDistanceChange: (Double?) -> Unit,
    onCustomMetricChange: (Double?) -> Unit,
    onToggleCheck: () -> Unit,
    showInlineTimer: Boolean = false,
    inlineTimerRunning: Boolean = false,
    inlineTimerSecondsFlow: Flow<Int?> = emptyFlow(),
    onStartInlineTimer: () -> Unit = {},
    onStopInlineTimer: () -> Unit = {},
    showRpe: Boolean = false,
    onRpeChange: (Double?) -> Unit = {},
    /** M11 circuit rows: WARMUP leaves the badge menu (breaks row-index == round) ... */
    allowWarmup: Boolean = true,
    /** ... and so does per-row Delete (rounds are removed whole via the round header). */
    allowDelete: Boolean = true,
    /** Circuit rows (Owner, 2026-09-03): false hides the redundant round number — the badge stays
     * the Failure/Dropset tap target, it just renders blank for a NORMAL set. */
    showRoundNumber: Boolean = true,
    /** M17 §5.1.5: true only for BARBELL rows with the setting on; the caller's header row adds a matching spacer. */
    showPlateCalculator: Boolean = false,
    /** M20d: opens the screen-hoisted (non-modal) plate calculator sheet for this exact set. Only
     *  called when [showPlateCalculator] is true, so the no-op default is never actually reached. */
    onOpenPlateCalculator: () -> Unit = {},
    /** Only isEditMode and weightUnit are read here -- the other fields exist so a future
     * screen-wide toggle can reach this row without a signature change. */
    config: WorkoutLoggerDisplayConfig = WorkoutLoggerDisplayConfig(),
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var showRpeSheet by remember { mutableStateOf(false) }
    // Live logging locks a set's values once it is checked off — the check is the commit. Editing a
    // PAST workout inverts that: every set in a COMPLETED workout is checked, so the same rule
    // would make the whole point of edit mode (§5.1.10: "All values and structure are editable
    // exactly as in live logging") impossible — every field would be read-only.
    val fieldsEnabled = config.isEditMode || !set.isCompleted
    // M18 (Owner directive): a completed row gets a VISIBLE full-width primary-green band — the
    // old primaryContainer @ 25% was indistinguishable from the plain surface on the dark theme.
    val rowBackground = if (set.isCompleted) MaterialTheme.colorScheme.primary.copy(alpha = COMPLETED_ROW_BAND_ALPHA) else Color.Transparent

    Row(
        // Owner, 2026-09-03: the completed-row band's edges are now softly rounded (clip before
        // background so the fill itself respects the shape, not just the border).
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(rowBackground).padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Same width as the header's ROUND/SET cell — a 36-vs-48 mismatch here shifted every
        // value cell 12dp off its header. The badge box fills the cell minus the same xxs gutter
        // the text fields carry, so the M18 uniform-box grid lines up across every column.
        Box(modifier = Modifier.width(SetTable.setCell).padding(horizontal = Spacing.xxs), contentAlignment = Alignment.Center) {
            SetBadge(setType = set.setType, position = index + 1, onClick = { typeMenuExpanded = true }, modifier = Modifier.fillMaxWidth(), showPosition = showRoundNumber)
            DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.set_type_normal)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.NORMAL) })
                if (allowWarmup) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.set_type_warmup)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.WARMUP) })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.set_type_failure)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.FAILURE) })
                DropdownMenuItem(text = { Text(stringResource(R.string.set_type_dropset)) }, onClick = { typeMenuExpanded = false; onSetTypeChange(SetType.DROPSET) })
                if (allowDelete) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { typeMenuExpanded = false; onRemove() })
                }
            }
        }
        // M18 uniform boxed cells (Owner): PREVIOUS is plain text, but it sits in the same
        // hairline box the editable fields render so the row reads as one grid. No tap behavior
        // existed on this cell and none is added.
        // Owner, 2026-09-03: the value line and the RPE line stack (weight/reps above, RPE below)
        // instead of one "@"-joined line. heightIn(min=...), not a fixed height, so a two-line
        // previous value grows this one cell — the Row has no height of its own, so it naturally
        // grows to match (default Row sizing), and every fixed-height sibling cell in the row still
        // centers within that taller row via the Row's own verticalAlignment, unchanged.
        Box(
            modifier = Modifier
                .width(SetTable.previousCell)
                .padding(horizontal = Spacing.xxs)
                .heightIn(min = SetTable.cellHeight)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), RoundedCornerShape(Radius.sm)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = Spacing.xxs, vertical = Spacing.xxs),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Primary text on a completed row: the muted colour measured 3.4:1 on the green
                // completed band, below WCAG AA (2026-09-25 accessibility pass).
                val previousColor = if (set.isCompleted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    set.previousLabel,
                    style = LogEzMono.dataSmall.copy(color = previousColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                set.previousRpeLabel?.let { rpeLine ->
                    Text(
                        rpeLine,
                        style = LogEzMono.dataSmall.copy(color = previousColor.copy(alpha = 0.8f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (showCustomMetric) {
            NumberCell(value = set.customMetric, onValueChange = onCustomMetricChange, enabled = fieldsEnabled, modifier = Modifier.weight(1f), label = stringResource(R.string.workout_set_field_a11y, index + 1, stringResource(R.string.workout_field_value)))
        }
        if (TargetField.WEIGHT in fields) {
            WeightCell(
                valueKg = set.weightKg, unit = config.weightUnit, onValueChange = onWeightChange, enabled = fieldsEnabled, modifier = Modifier.weight(1f),
                label = stringResource(R.string.workout_set_field_a11y, index + 1, stringResource(if (config.weightUnit == WeightUnit.LB) R.string.workout_field_weight_lb else R.string.workout_field_weight_kg)),
            )
            if (showPlateCalculator) {
                // Trails the KG cell inside the same fixed width the header row spaces over, so
                // the M15 column alignment holds with or without the button.
                IconButton(
                    onClick = onOpenPlateCalculator,
                    enabled = fieldsEnabled,
                    modifier = Modifier.size(SetTable.plateCalcCell),
                ) {
                    Icon(
                        Icons.Outlined.Calculate,
                        contentDescription = stringResource(R.string.workout_plate_calc_open),
                        tint = if (fieldsEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (TargetField.REPS in fields) {
            IntCell(value = set.reps, onValueChange = onRepsChange, enabled = fieldsEnabled, modifier = Modifier.weight(REPS_COLUMN_WEIGHT), label = stringResource(R.string.workout_set_field_a11y, index + 1, stringResource(R.string.workout_field_reps)))
        }
        if (TargetField.DURATION in fields) {
            // Leaf-scoped (spine rule): only collected/ticking while this exact row is the running inline timer.
            val liveInlineSeconds by (if (inlineTimerRunning) inlineTimerSecondsFlow else emptyFlow()).collectAsStateWithLifecycle(null)
            IntCell(
                value = if (inlineTimerRunning) liveInlineSeconds else set.durationSeconds,
                onValueChange = onDurationChange,
                enabled = fieldsEnabled && !inlineTimerRunning,
                modifier = Modifier.weight(1f),
                suffix = stringResource(R.string.workout_unit_suffix_seconds),
                label = stringResource(R.string.workout_set_field_a11y, index + 1, stringResource(R.string.workout_field_seconds)),
            )
            if (showInlineTimer && !set.isCompleted) {
                IconButton(onClick = if (inlineTimerRunning) onStopInlineTimer else onStartInlineTimer, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (inlineTimerRunning) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(if (inlineTimerRunning) R.string.workout_inline_timer_stop else R.string.workout_inline_timer_start),
                    )
                }
            }
        }
        if (TargetField.DISTANCE in fields) {
            NumberCell(
                value = set.distanceMeters,
                onValueChange = onDistanceChange,
                enabled = fieldsEnabled,
                modifier = Modifier.weight(1f),
                suffix = stringResource(R.string.workout_unit_suffix_meters),
                label = stringResource(R.string.workout_set_field_a11y, index + 1, stringResource(R.string.workout_field_meters)),
            )
        }
        if (showRpe) {
            RpeCell(
                value = set.rpe,
                enabled = fieldsEnabled,
                onClick = { showRpeSheet = true },
                modifier = Modifier.width(SetTable.rpeCell).padding(horizontal = Spacing.xxs),
            )
        }
        IconButton(onClick = onToggleCheck, modifier = Modifier.width(SetTable.checkCell)) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = stringResource(R.string.workout_check_set),
                tint = if (set.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showRpeSheet) {
        RpePickerSheet(
            initialRpe = set.rpe,
            onDismiss = { showRpeSheet = false },
            onConfirm = { rpe -> onRpeChange(rpe); showRpeSheet = false },
        )
    }

}

/**
 * M18 uniform boxed cells (Owner): the badge fills the SET/ROUND column as a field-height hairline
 * box — same outlineVariant border, Radius token, and [SetTable.cellHeight] as the value fields —
 * instead of the old free-floating 32dp pill. The set-type tint (W/F/D at 0.15 alpha) and the
 * whole-cell tap target (opens the type menu) are unchanged.
 */
@Composable
internal fun SetBadge(setType: SetType, position: Int, onClick: () -> Unit, modifier: Modifier = Modifier, showPosition: Boolean = true) {
    val (label, color) = when (setType) {
        // Circuit rows (Owner, 2026-09-03): the round number is redundant — each round already has
        // its own "ROUND N" section header — so it's blank here, but the cell stays the tap target
        // for Failure/Dropset, which a circuit row can still be marked as (WARMUP is the only type
        // excluded in a circuit, per the M11 invariant).
        SetType.NORMAL -> (if (showPosition) position.toString() else "") to MaterialTheme.colorScheme.onSurface
        SetType.WARMUP -> "W" to Warning500
        SetType.FAILURE -> "F" to Danger500
        SetType.DROPSET -> "D" to SupersetPalette[4]
    }
    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = if (setType == SetType.NORMAL) Color.Transparent else color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.height(SetTable.cellHeight), contentAlignment = Alignment.Center) {
            Text(label, color = if (setType == SetType.WARMUP) Warning300 else color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * M18 (Owner directive): the completed-row band. `primary.copy(alpha = 0.15f)` over the dark
 * surface reads as an unmistakable green wash while keeping onSurface text and the outlineVariant
 * cell hairlines comfortably legible (0.12–0.18 was the acceptable range; 0.15 is the midpoint).
 */
internal const val COMPLETED_ROW_BAND_ALPHA = 0.15f

/** M18: KG header flips to LBS when the display unit is pounds (shared by regular + circuit tables). */
internal fun weightHeaderRes(unit: WeightUnit): Int =
    if (unit == WeightUnit.LB) R.string.routine_builder_col_weight_lbs else R.string.routine_builder_col_weight

/** M18 uniform boxed cells: the value fields' chrome — outlineVariant hairline at rest (and when
 * disabled, i.e. completed-locked rows) with the Radius token, so the text fields and the fixed
 * boxed cells (SET/PREVIOUS/RPE) render as one grid. Focus keeps the default primary outline.
 * See [com.enil.logez.core.designsystem.boxedFieldColors], shared with RoutineExerciseCard.kt. */
@Composable
internal fun NumberCell(value: Double?, onValueChange: (Double?) -> Unit, enabled: Boolean, modifier: Modifier = Modifier, suffix: String? = null, label: String? = null) {
    var text by remember(value) { mutableStateOf(value?.let { formatTargetNumber(it) }.orEmpty()) }
    CompactBoxedTextField(
        text = text,
        onTextChange = { new -> text = new; onValueChange(parseDecimalInput(new)) },
        enabled = enabled,
        keyboardType = KeyboardType.Decimal,
        modifier = modifier,
        suffix = suffix,
        label = label,
    )
}

/**
 * M18: the weight input cell — the ONE place the logger converts between canonical kg storage and
 * the user's display unit, in both directions ([WeightDisplay]). `valueKg` and `onValueChange`
 * both speak kg, so every caller (write-through updates, the plate sheet's apply, edit mode) stays
 * unit-blind; only the text shown/parsed here is unit-aware. The plate-calculator sheet converts
 * internally too, but its apply path hands back canonical kg directly (never through this parse),
 * so the two conversions cannot stack.
 */
@Composable
internal fun WeightCell(valueKg: Double?, unit: WeightUnit, onValueChange: (Double?) -> Unit, enabled: Boolean, modifier: Modifier = Modifier, label: String? = null) {
    val displayText = valueKg?.let { WeightDisplay.format(WeightDisplay.toDisplay(it, unit)) }.orEmpty()
    // Same remember(value) idiom as NumberCell: the typed string is kept until the derived display
    // text actually changes underneath it (a stored-kg or unit change), so conversion never fights
    // active typing.
    var text by remember(displayText) { mutableStateOf(displayText) }
    CompactBoxedTextField(
        text = text,
        onTextChange = { new -> text = new; onValueChange(parseDecimalInput(new)?.let { WeightDisplay.toKg(it, unit) }) },
        enabled = enabled,
        keyboardType = KeyboardType.Decimal,
        modifier = modifier,
        label = label,
    )
}

@Composable
internal fun IntCell(value: Int?, onValueChange: (Int?) -> Unit, enabled: Boolean, modifier: Modifier = Modifier, suffix: String? = null, label: String? = null) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    CompactBoxedTextField(
        text = text,
        onTextChange = { new -> text = new; onValueChange(new.toIntOrNull()) },
        enabled = enabled,
        keyboardType = KeyboardType.Number,
        modifier = modifier,
        suffix = suffix,
        label = label,
    )
}

/**
 * The uniform boxed input all three value cells share. A plain OutlinedTextField reserves ~16dp
 * of horizontal content padding per side — at the set table's cell widths that leaves room for
 * barely two characters ("32.5" rendering as "3"), so this drops down to BasicTextField +
 * DecorationBox with compact padding at the shared [SetTable.cellHeight].
 *
 * Owner (2026-09-03): center the displayed value. The real, editable [BasicTextField] stays
 * start-aligned and its own text is made transparent while unfocused — start alignment is kept
 * because singleLine BasicTextField auto-scrolls its content to keep the cursor in view, and that
 * scroll math is built around start-aligned text: TextAlign.Center fought it once a value was
 * wider than the box could show unfocused, rendering a 4-5 char value like an LB-converted
 * "71.7"/"154.3" as a single clipped-looking digit (found live, M18). A separate plain [Text] —
 * which has no such scroll behavior — renders on top, centered, whenever the field isn't focused;
 * the moment the user taps in to edit, the overlay disappears and the real field (start-aligned,
 * the already-verified behavior) takes over until they move on. `suffix` (e.g. "s"/"m") is
 * decoration-only chrome from [OutlinedTextFieldDefaults.DecorationBox] and isn't part of the
 * editable text, so it can't interfere with parsing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CompactBoxedTextField(
    text: String,
    onTextChange: (String) -> Unit,
    enabled: Boolean,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    /** What TalkBack announces for the field, e.g. "Set 2, reps". A bare BasicTextField announced only its value. */
    label: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val colors = boxedFieldColors()
    val textColor = MaterialTheme.colorScheme.onSurface

    Box(modifier = modifier.padding(horizontal = 2.dp).height(SetTable.cellHeight), contentAlignment = Alignment.Center) {
        BasicTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            singleLine = true,
            // bodyMedium, not bodyLarge: at these cell widths the difference is "102.5" fitting
            // whole versus clipping to a sliver. Transparent while unfocused — see class doc: the
            // centered overlay Text below is what's actually visible at rest.
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (isFocused) textColor else Color.Transparent,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            interactionSource = interactionSource,
            modifier = Modifier.fillMaxSize().then(if (label != null) Modifier.semantics { contentDescription = label } else Modifier),
        ) { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = text,
                innerTextField = innerTextField,
                enabled = enabled,
                singleLine = true,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                colors = colors,
                suffix = suffix?.let { s -> { Text(s, style = LogEzMono.dataSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) } },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                container = {
                    OutlinedTextFieldDefaults.Container(
                        enabled = enabled,
                        isError = false,
                        interactionSource = interactionSource,
                        colors = colors,
                        shape = RoundedCornerShape(Radius.sm),
                    )
                },
            )
        }
        if (!isFocused && text.isNotEmpty()) {
            // Nudged off dead-center when a suffix docks at the box's trailing edge (DecorationBox
            // shares the same row), so the value centers in the space actually left for it rather
            // than under the suffix.
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = if (suffix != null) Modifier.padding(end = 14.dp) else Modifier,
            )
        }
    }
}

/** §5.1.7 entry point: "tap the RPE cell". A tappable box, not a text field — RPE is never free
 * text. M18 uniform boxed cells: field-height hairline box like every other cell; the filled
 * primaryContainer state (an RPE is logged) keeps its tint inside the same chrome. */
@Composable
private fun RpeCell(value: Double?, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(Radius.sm),
        color = if (value != null) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(modifier = Modifier.height(SetTable.cellHeight), contentAlignment = Alignment.Center) {
            Text(
                value?.let { RpeScale.format(it) } ?: "—",
                style = LogEzMono.dataSmall.copy(
                    color = if (value != null) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

/**
 * §5.1.7 "Log Set RPE": single-select row of the eight allowed values, a label + reserve-reps
 * description that updates with the selection, Clear (writes null — blank is a valid RPE, e.g.
 * warm-ups), and Done. Tapping a value only updates the pending selection so the description is
 * visible before committing; Clear and Done are the only actions that actually write.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RpePickerSheet(initialRpe: Double?, onDismiss: () -> Unit, onConfirm: (Double?) -> Unit) {
    var selected by remember { mutableStateOf(initialRpe) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md)) {
            Text(
                stringResource(R.string.workout_rpe_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            LazyRow(modifier = Modifier.padding(top = Spacing.md)) {
                items(items = RpeScale.VALUES, key = { it }) { rpe ->
                    FilterChip(
                        selected = selected == rpe,
                        onClick = { selected = rpe },
                        label = { Text(RpeScale.format(rpe)) },
                        modifier = Modifier.padding(end = Spacing.xs),
                    )
                }
            }
            Text(
                selected?.let { "${stringResource(R.string.workout_rpe_label_prefix)} ${RpeScale.format(it)} — ${RpeScale.reserveDescription(it)}" }
                    ?: stringResource(R.string.workout_rpe_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { onConfirm(null) }) { Text(stringResource(R.string.workout_rpe_clear)) }
                Button(onClick = { onConfirm(selected) }) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}
