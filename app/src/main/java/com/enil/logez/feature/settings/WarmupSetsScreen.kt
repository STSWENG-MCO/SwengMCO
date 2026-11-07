package com.enil.logez.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.domain.model.WarmupStep
import kotlin.math.roundToInt
import androidx.compose.ui.res.pluralStringResource

/** Which step is being edited (null target = adding a new one). */
private data class WarmupStepDialogState(val index: Int?, val initial: WarmupStep?)

/**
 * M18 Warm-up Sets sub-page (§5.1.6's "Warmup Method", relocated into the Settings tree like
 * M17's Plate Equipment): the percent-of-working-weight × reps ladder that the logger's
 * "Add warm-up sets" inserts. Every edit persists instantly through [SettingsViewModel] — no Save
 * button, back = done — using the same read-current-inside-the-coroutine write path as the plate
 * equipment editor, so back-to-back edits compose instead of clobbering. Rows are ordered: the
 * ladder is inserted top-to-bottom, so the up/down controls are real reordering, not cosmetics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarmupSetsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val method = settings.warmupMethod
    var dialog by remember { mutableStateOf<WarmupStepDialogState?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { ScreenTitle(stringResource(R.string.settings_warmup_method_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (method.isEmpty()) {
                item(key = "empty_note") {
                    Text(
                        stringResource(R.string.settings_warmup_empty_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            }
            items(count = method.size, key = { "step_$it" }) { index ->
                val step = method[index]
                WarmupStepRow(
                    step = step,
                    canMoveUp = index > 0,
                    canMoveDown = index < method.size - 1,
                    onClick = { dialog = WarmupStepDialogState(index = index, initial = step) },
                    onMoveUp = { viewModel.moveWarmupStep(index, -1) },
                    onMoveDown = { viewModel.moveWarmupStep(index, +1) },
                    onRemove = { viewModel.removeWarmupStep(index) },
                )
            }
            item(key = "actions") {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.xs)) {
                    TextButton(onClick = { dialog = WarmupStepDialogState(index = null, initial = null) }) {
                        Text(stringResource(R.string.settings_warmup_add_step))
                    }
                    TextButton(onClick = viewModel::resetWarmupMethod) {
                        Text(stringResource(R.string.settings_warmup_reset))
                    }
                }
            }
            item(key = "subtitle_note") {
                Text(
                    stringResource(R.string.settings_warmup_method_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
    }

    dialog?.let { state ->
        WarmupStepDialog(
            initial = state.initial,
            onConfirm = { step ->
                val index = state.index
                if (index == null) viewModel.addWarmupStep(step) else viewModel.updateWarmupStep(index, step)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

/** One ladder row: "40% of working weight · 5 reps", tap to edit, up/down to reorder, X to remove. */
@Composable
private fun WarmupStepRow(
    step: WarmupStep,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        headlineContent = { Text(stringResource(R.string.settings_warmup_step_row, step.displayPercent())) },
        supportingContent = { Text(pluralStringResource(R.plurals.set_reps, step.reps, step.reps)) },
        trailingContent = {
            Row {
                IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                    Icon(Icons.Outlined.ArrowUpward, contentDescription = stringResource(R.string.workout_move_up))
                }
                IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                    Icon(Icons.Outlined.ArrowDownward, contentDescription = stringResource(R.string.workout_move_down))
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.settings_warmup_remove_step),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
    HorizontalDivider()
}

/**
 * Percent + reps editor for one step. Validation mirrors what the ladder can sensibly hold — a
 * whole percent 1..100 (stored as the 0..1 fraction the engine multiplies by) and reps 1..99 —
 * with the confirm disabled and the inline reason shown while either field is invalid.
 */
@Composable
private fun WarmupStepDialog(
    initial: WarmupStep?,
    onConfirm: (WarmupStep) -> Unit,
    onDismiss: () -> Unit,
) {
    var percentText by remember { mutableStateOf(initial?.displayPercent()?.toString().orEmpty()) }
    var repsText by remember { mutableStateOf(initial?.reps?.toString().orEmpty()) }
    val percent = percentText.toIntOrNull()?.takeIf { it in 1..100 }
    val reps = repsText.toIntOrNull()?.takeIf { it in 1..99 }
    val percentError = percentText.isNotEmpty() && percent == null
    val repsError = repsText.isNotEmpty() && reps == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.settings_warmup_add_step else R.string.settings_warmup_edit_step)) },
        text = {
            Column {
                OutlinedTextField(
                    value = percentText,
                    onValueChange = { percentText = it },
                    label = { Text(stringResource(R.string.settings_warmup_percent_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = percentError,
                    supportingText = if (percentError) {
                        { Text(stringResource(R.string.settings_warmup_invalid_percent)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = repsText,
                    onValueChange = { repsText = it },
                    label = { Text(stringResource(R.string.settings_warmup_reps_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = repsError,
                    supportingText = if (repsError) {
                        { Text(stringResource(R.string.settings_warmup_invalid_reps)) }
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (percent != null && reps != null) onConfirm(WarmupStep(percent = percent / 100.0, reps = reps)) },
                enabled = percent != null && reps != null,
            ) {
                Text(stringResource(R.string.action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** 0.40 → 40. Rounded, not truncated, so a fraction like 0.4 surviving Double storage stays 40. */
internal fun WarmupStep.displayPercent(): Int = (percent * 100).roundToInt()
