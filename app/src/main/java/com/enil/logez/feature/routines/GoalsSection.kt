package com.enil.logez.feature.routines

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enil.logez.R
import com.enil.logez.core.designsystem.ConfirmDialog
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.model.GoalMetric
import com.enil.logez.core.domain.model.GoalPeriod
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.feature.analytics.AnalyticsFormatters
import com.enil.logez.core.designsystem.parseDecimalInput

/** M8d — Goals card: active goals with a progress bar each, "Add Goal", per-goal delete. */
@Composable
fun GoalsSection(
    uiState: GoalsUiState,
    onCreateGoal: (GoalMetric, GoalPeriod, Double) -> Unit,
    onDeleteGoal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var deletingGoalId by remember { mutableStateOf<String?>(null) }

    LogEzCard(modifier = modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
        // Compact pass (Owner): the card's own padding and the per-goal spacing each drop
        // one step on the Spacing scale (md -> sm, sm -> xs) so the card eats less height.
        Column(modifier = Modifier.padding(Spacing.sm)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.goal_section_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.goal_add))
                }
            }
            if (!uiState.isLoading) {
                if (uiState.goals.isEmpty()) {
                    Text(
                        stringResource(R.string.goal_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.xxs),
                    )
                } else {
                    uiState.goals.forEach { row ->
                        GoalItem(
                            row = row,
                            weightUnit = uiState.weightUnit,
                            onDelete = { deletingGoalId = row.goal.id },
                            modifier = Modifier.padding(top = Spacing.xs),
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddGoalDialog(
            onConfirm = { metric, period, target -> onCreateGoal(metric, period, target); showAddDialog = false },
            onDismiss = { showAddDialog = false },
        )
    }
    deletingGoalId?.let { id ->
        ConfirmDialog(
            onDismissRequest = { deletingGoalId = null },
            title = stringResource(R.string.goal_delete_title),
            body = stringResource(R.string.goal_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onDeleteGoal(id) },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

@Composable
private fun GoalItem(row: GoalRow, weightUnit: WeightUnit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val progress = row.progress
    val fraction = if (progress.target > 0.0) (progress.current / progress.target).toFloat().coerceIn(0f, 1f) else 0f
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${goalPeriodLabel(row.goal.period)} ${goalMetricLabel(row.goal.metric)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_delete))
            }
        }
        // Material3 draws a trailing "stop indicator" dot at the end of the track by default;
        // an empty draw lambda is the supported way to suppress it (Owner: the dot reads as a
        // stray artifact next to the mono readout).
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxs),
            drawStopIndicator = {},
        )
        Text(
            goalProgressText(row.goal.metric, progress.current, progress.target, weightUnit),
            style = LogEzMono.dataSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
        )
    }
}

@Composable
private fun AddGoalDialog(onConfirm: (GoalMetric, GoalPeriod, Double) -> Unit, onDismiss: () -> Unit) {
    var metric by remember { mutableStateOf(GoalMetric.VOLUME) }
    var period by remember { mutableStateOf(GoalPeriod.WEEKLY) }
    var targetText by remember { mutableStateOf("") }
    val targetValue = parseDecimalInput(targetText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.goal_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text(stringResource(R.string.goal_metric_label), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    GoalMetric.entries.forEach { m ->
                        FilterChip(selected = metric == m, onClick = { metric = m }, label = { Text(goalMetricLabel(m)) })
                    }
                }
                Text(stringResource(R.string.goal_period_label), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    GoalPeriod.entries.forEach { p ->
                        FilterChip(selected = period == p, onClick = { period = p }, label = { Text(goalPeriodLabel(p)) })
                    }
                }
                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text(stringResource(R.string.goal_target_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { targetValue?.let { onConfirm(metric, period, it) } },
                enabled = targetValue != null && targetValue > 0.0,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun goalMetricLabel(metric: GoalMetric): String = when (metric) {
    GoalMetric.REPS -> stringResource(R.string.goal_metric_reps)
    GoalMetric.VOLUME -> stringResource(R.string.goal_metric_volume)
    GoalMetric.DURATION -> stringResource(R.string.goal_metric_duration)
    GoalMetric.WORKOUT_COUNT -> stringResource(R.string.goal_metric_workout_count)
}

@Composable
private fun goalPeriodLabel(period: GoalPeriod): String = when (period) {
    GoalPeriod.DAILY -> stringResource(R.string.goal_period_daily)
    GoalPeriod.WEEKLY -> stringResource(R.string.goal_period_weekly)
    GoalPeriod.MONTHLY -> stringResource(R.string.goal_period_monthly)
}

private fun goalProgressText(metric: GoalMetric, current: Double, target: Double, unit: WeightUnit): String = when (metric) {
    GoalMetric.VOLUME -> "${AnalyticsFormatters.volume(current, unit)} / ${AnalyticsFormatters.volume(target, unit)}"
    GoalMetric.DURATION -> "${AnalyticsFormatters.durationHoursMinutes(current.toLong())} / ${AnalyticsFormatters.durationHoursMinutes(target.toLong())}"
    GoalMetric.REPS, GoalMetric.WORKOUT_COUNT -> "${AnalyticsFormatters.count(current)} / ${AnalyticsFormatters.count(target)}"
}
