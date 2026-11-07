package com.enil.logez.feature.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.enil.logez.core.designsystem.SetTable
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.TargetField
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.targetFields
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * M11: one card per ROUND — the circuit logger's replacement for the per-exercise card. Each row
 * is one exercise's turn in this round, in sequence order: name (tap -> detail, overflow ->
 * replace/remove), then the same type-specific input matrix a regular set row carries (PREVIOUS,
 * value cells, RPE, inline timer, check), reusing [SetRow] so completion behavior — sounds, rest
 * timer, PR rules, FAILURE validation — is literally the same code path.
 *
 * Defensive rendering: an exercise whose set count falls short of this round (edited data,
 * partial adds) contributes an inert "—" slot instead of crashing or re-flowing rows — the
 * grouping in [buildCircuitRounds] never invents or reassigns rows.
 */
@Composable
internal fun CircuitRoundCard(
    round: CircuitRound,
    callbacks: WorkoutCallbacks,
    onExerciseClick: (exerciseId: String) -> Unit,
    onOpenReplacePicker: (workoutExerciseId: String) -> Unit,
    onRemoveRound: () -> Unit,
    canRemoveRound: Boolean = true,
    inlineTimerExerciseId: String?,
    inlineTimerSetId: String?,
    inlineTimerSecondsFlow: Flow<Int?> = emptyFlow(),
    config: WorkoutLoggerDisplayConfig = WorkoutLoggerDisplayConfig(),
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    LogEzCard(modifier = modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.workout_round_header, round.roundNumber)
                        .uppercase(currentLocale()),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.06.em),
                    modifier = Modifier.weight(1f),
                )
                // Hidden (not just inert) for the only remaining round — the ViewModel guards
                // removeRound the same way, so the two can't drift into a dead menu item.
                if (canRemoveRound) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.workout_remove_round)) },
                                onClick = { menuExpanded = false; onRemoveRound() },
                            )
                        }
                    }
                }
            }

            // When every exercise in the round shares one column set (the common case), a single
            // header row under the ROUND title covers all of them; per-entry headers only return
            // when a mixed-type circuit genuinely needs different columns per entry.
            val uniformColumns = round.entries
                .filter { it.set != null }
                .map { columnSignature(it.exercise, config.plateCalculator.enabled) }
                .distinct()
                .singleOrNull()
            if (uniformColumns != null) {
                CircuitColumnsHeader(
                    fields = uniformColumns.fields,
                    showCustomMetric = uniformColumns.customMetric,
                    showRpe = config.rpeTrackingEnabled && TargetField.REPS in uniformColumns.fields,
                    showPlateCalculator = uniformColumns.plateCalculator,
                    weightUnit = config.weightUnit,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
            round.entries.forEachIndexed { entryIndex, entry ->
                if (entryIndex > 0) HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                CircuitEntry(
                    showColumnHeader = uniformColumns == null,
                    roundIndex = round.roundNumber - 1,
                    entry = entry,
                    callbacks = callbacks,
                    onExerciseClick = { onExerciseClick(entry.exercise.exerciseId) },
                    onOpenReplacePicker = { onOpenReplacePicker(entry.exercise.id) },
                    inlineTimerRunning = inlineTimerExerciseId == entry.exercise.id && inlineTimerSetId == entry.set?.id,
                    inlineTimerSecondsFlow = inlineTimerSecondsFlow,
                    config = config,
                )
            }
        }
    }
}

/** One exercise's turn in one round: name row (with per-exercise ops), column headers, input row. */
@Composable
private fun CircuitEntry(
    showColumnHeader: Boolean,
    roundIndex: Int,
    entry: CircuitRoundEntry,
    callbacks: WorkoutCallbacks,
    onExerciseClick: () -> Unit,
    onOpenReplacePicker: () -> Unit,
    inlineTimerRunning: Boolean,
    inlineTimerSecondsFlow: Flow<Int?>,
    config: WorkoutLoggerDisplayConfig,
) {
    val exercise = entry.exercise
    var menuExpanded by remember { mutableStateOf(false) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                exercise.exerciseName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(onClick = onExerciseClick),
            )
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    // M11: replace/remove only — no superset (the circuit IS the sequence) and no
                    // per-exercise reorder affordance in the round-grouped view.
                    DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_replace)) }, onClick = { menuExpanded = false; onOpenReplacePicker() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.routine_builder_menu_remove_exercise)) }, onClick = { menuExpanded = false; callbacks.onRemoveExercise(exercise.id) })
                }
            }
        }

        val set = entry.set
        if (set == null) {
            // Defensive slot: this exercise has no row for this round (unequal set counts).
            Text(
                "—",
                style = LogEzMono.dataMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.md, top = Spacing.xxs),
            )
            return@Column
        }

        val fields = exercise.exerciseType.targetFields()
        val showCustomMetric = exercise.exerciseType == ExerciseType.FLOORS_DURATION || exercise.exerciseType == ExerciseType.STEPS_DURATION
        val showRpe = config.rpeTrackingEnabled && TargetField.REPS in fields
        val showInlineTimer = config.inlineTimerEnabled && TargetField.DURATION in fields
        // Same §5.1.5 gate as the regular table: BARBELL rows only, setting on.
        val showPlateCalculator = config.plateCalculator.enabled && exercise.equipment == Equipment.BARBELL && TargetField.WEIGHT in fields

        if (showColumnHeader) CircuitColumnsHeader(fields = fields, showCustomMetric = showCustomMetric, showRpe = showRpe, showPlateCalculator = showPlateCalculator, weightUnit = config.weightUnit)
        SetRow(
            index = roundIndex,
            set = set,
            fields = fields,
            showCustomMetric = showCustomMetric,
            onSetTypeChange = { type -> callbacks.onUpdateSetType(exercise.id, set.id, type) },
            onRemove = { /* disabled in circuit mode — rounds are removed whole */ },
            onWeightChange = { callbacks.onUpdateWeight(exercise.id, set.id, it) },
            onRepsChange = { callbacks.onUpdateReps(exercise.id, set.id, it) },
            onDurationChange = { callbacks.onUpdateDuration(exercise.id, set.id, it) },
            onDistanceChange = { callbacks.onUpdateDistance(exercise.id, set.id, it) },
            onCustomMetricChange = { callbacks.onUpdateCustomMetric(exercise.id, set.id, it) },
            onToggleCheck = { callbacks.onToggleCheck(exercise.id, set.id) },
            showInlineTimer = showInlineTimer,
            inlineTimerRunning = inlineTimerRunning,
            inlineTimerSecondsFlow = inlineTimerSecondsFlow,
            onStartInlineTimer = { callbacks.onStartInlineTimer(exercise.id, set.id) },
            onStopInlineTimer = { callbacks.onStopInlineTimer(exercise.id, set.id) },
            showRpe = showRpe,
            onRpeChange = { rpe -> callbacks.onUpdateRpe(exercise.id, set.id, rpe) },
            allowWarmup = false,
            allowDelete = false,
            showRoundNumber = false,
            showPlateCalculator = showPlateCalculator,
            onOpenPlateCalculator = { callbacks.onOpenPlateCalculator(exercise.id, set.id, set.weightKg) },
            config = config,
        )
        if (set.failureError) {
            Text(
                stringResource(R.string.workout_failure_error),
                color = com.enil.logez.core.designsystem.Danger500,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(start = SetTable.setCell, bottom = Spacing.xxs),
            )
        }
    }
}

/** The column set a circuit entry's table needs — entries sharing one signature share one header.
 * M17: the plate-calculator affordance is part of the signature (it adds a fixed-width slot), so a
 * mixed barbell/non-barbell circuit correctly falls back to per-entry headers. */
private data class CircuitColumns(val fields: Set<TargetField>, val customMetric: Boolean, val plateCalculator: Boolean)

private fun columnSignature(exercise: WorkoutExerciseUiModel, plateCalculatorEnabled: Boolean): CircuitColumns {
    val type = exercise.exerciseType
    val fields = type.targetFields()
    return CircuitColumns(
        fields = fields,
        customMetric = type == ExerciseType.FLOORS_DURATION || type == ExerciseType.STEPS_DURATION,
        plateCalculator = plateCalculatorEnabled && exercise.equipment == Equipment.BARBELL && TargetField.WEIGHT in fields,
    )
}

@Composable
private fun CircuitColumnsHeader(
    fields: Set<TargetField>,
    showCustomMetric: Boolean,
    showRpe: Boolean,
    showPlateCalculator: Boolean = false,
    weightUnit: WeightUnit = WeightUnit.KG,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Owner, 2026-09-03: "ROUND" is dropped — each round already has its own "ROUND N" section
        // header above this table — but the Spacer keeps the column width so the row's badge cell
        // (still a Failure/Dropset tap target, see SetBadge's showPosition) stays in register.
        Spacer(modifier = Modifier.width(SetTable.setCell))
        HeaderCell(stringResource(R.string.workout_col_previous), width = SetTable.previousCell, textAlign = TextAlign.Center)
        if (showCustomMetric) HeaderCell(stringResource(R.string.workout_col_custom_metric), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        if (TargetField.WEIGHT in fields) HeaderCell(stringResource(weightHeaderRes(weightUnit)), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        // Mirrors the row's trailing calculator button (same width) so KG stays over its cell.
        if (showPlateCalculator) Spacer(modifier = Modifier.width(SetTable.plateCalcCell))
        if (TargetField.REPS in fields) HeaderCell(stringResource(R.string.routine_builder_col_reps), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        if (TargetField.DURATION in fields) HeaderCell(stringResource(R.string.routine_builder_col_time), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        if (TargetField.DISTANCE in fields) HeaderCell(stringResource(R.string.routine_builder_col_distance), modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        if (showRpe) HeaderCell(stringResource(R.string.workout_col_rpe), width = SetTable.rpeCell, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.width(SetTable.checkCell))
    }
}
