package com.enil.logez.feature.workout.finish

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.ConfirmDialog
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import com.enil.logez.core.designsystem.rememberClockTimeFormatter

/** PHASE2_PLAN.md §5.1.8(a) Save Workout screen, plus (b)'s conditional Update-Routine prompt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinishWorkoutScreen(
    onBack: () -> Unit,
    onSaved: (workoutId: String) -> Unit,
    onDiscardInstead: () -> Unit,
    viewModel: FinishWorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }
    var showIncompleteConfirm by remember { mutableStateOf(false) }
    var showNoSetsDialog by remember { mutableStateOf(false) }
    var showStructurePrompt by remember { mutableStateOf(false) }
    val isSaving = saveState is SaveState.Saving

    // Navigation is driven off the ViewModel's state, not off the call site, so a save that
    // outlives an Activity recreation still delivers its result to whichever screen comes back.
    val saveFailedMessage = stringResource(R.string.finish_save_failed)
    LaunchedEffect(saveState) {
        when (val state = saveState) {
            is SaveState.Saved -> onSaved(state.result.workoutId)
            is SaveState.Discarded -> onDiscardInstead()
            is SaveState.Failed -> {
                snackbarHostState.showSnackbar(saveFailedMessage)
                viewModel.clearSaveError()
            }
            else -> Unit
        }
    }

    /** §5.1.8 gates, in order: nothing logged -> offer Discard; unfinished sets -> warn; structural change -> prompt. */
    fun attemptSave() {
        if (isSaving) return
        when {
            uiState.completedSetCount == 0 -> showNoSetsDialog = true
            uiState.incompleteSetCount > 0 -> showIncompleteConfirm = true
            else -> scope.launch { if (viewModel.needsStructurePrompt()) showStructurePrompt = true else viewModel.save(null) }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { Text(stringResource(R.string.finish_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) return@Scaffold
        if (uiState.isMissing) {
            Text(
                stringResource(R.string.finish_workout_missing),
                modifier = Modifier.padding(padding).padding(Spacing.md),
                color = MaterialTheme.colorScheme.error,
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(Spacing.md),
        ) {
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::updateTitle,
                label = { Text(stringResource(R.string.finish_workout_title_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::updateNotes,
                label = { Text(stringResource(R.string.finish_description_label)) },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )

            // The date stays freely editable — that is what makes backdated manual logging work
            // (§5.1.8: "fully editable, enabling backdated manual logging"). Duration does not:
            // it reports how long the session actually ran, frozen into the workout row by
            // WorkoutLoggerViewModel.prepareForFinish() before this screen opened.
            LabeledRow(
                label = stringResource(R.string.finish_date_time_label),
                value = formatDateTime(uiState.startedAtMillis, rememberClockTimeFormatter()),
                onClick = { showDatePicker = true },
            )
            HorizontalDivider()
            LabeledRow(
                label = stringResource(R.string.finish_duration_label),
                value = formatFinishDuration(uiState.durationSeconds),
                valueStyle = LogEzMono.dataMedium,
            )

            if (uiState.isRoutineBased) {
                Text(
                    stringResource(R.string.finish_routine_settings),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
                )
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.finish_update_routine_values), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.finish_update_routine_values_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = uiState.updateRoutineValues, onCheckedChange = viewModel::setUpdateRoutineValues)
                }
            }

            Button(
                onClick = { attemptSave() },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
            ) {
                Text(stringResource(R.string.finish_save))
            }
        }
    }

    if (showDatePicker) {
        // Material3's DatePicker speaks UTC-midnight millis, while the workout's startedAt is an
        // instant the rest of the screen renders in the device zone. Converting explicitly at both
        // ends keeps the highlighted day equal to the displayed day; arithmetic on raw millis
        // (`% DAY_MILLIS`) silently mixed the two and landed the save on the wrong local date.
        val zone = remember { ZoneId.systemDefault() }
        val startedAt = uiState.startedAtMillis
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = toDatePickerMillis(startedAt, zone),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    // Keep the original time-of-day; the picker only moves the calendar date.
                    pickerState.selectedDateMillis?.let { picked ->
                        viewModel.updateStartedAt(fromDatePickerMillis(picked, startedAt, zone))
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showNoSetsDialog) {
        // Actually deletes the workout — the dialog previously only navigated away, so the
        // "discarded" session stayed IN_PROGRESS and blocked starting a new one.
        ConfirmDialog(
            onDismissRequest = { showNoSetsDialog = false },
            title = stringResource(R.string.finish_no_sets_title),
            body = stringResource(R.string.finish_no_sets_body),
            confirmLabel = stringResource(R.string.workout_discard),
            onConfirm = viewModel::discard,
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    if (showIncompleteConfirm) {
        ConfirmDialog(
            onDismissRequest = { showIncompleteConfirm = false },
            title = pluralStringResource(
                R.plurals.finish_incomplete_title,
                uiState.incompleteSetCount,
                uiState.incompleteSetCount,
            ),
            body = stringResource(R.string.finish_incomplete_body),
            confirmLabel = stringResource(R.string.finish_incomplete_confirm),
            onConfirm = { scope.launch { if (viewModel.needsStructurePrompt()) showStructurePrompt = true else viewModel.save(null) } },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    if (showStructurePrompt) {
        AlertDialog(
            onDismissRequest = { showStructurePrompt = false },
            title = { Text(stringResource(R.string.finish_structure_title)) },
            text = { Text(stringResource(R.string.finish_structure_body)) },
            confirmButton = {
                TextButton(onClick = { showStructurePrompt = false; viewModel.save(RoutineStructureChoice.UPDATE_ROUTINE) }) {
                    Text(stringResource(R.string.finish_structure_update))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStructurePrompt = false; viewModel.save(RoutineStructureChoice.KEEP_ORIGINAL) }) {
                    Text(stringResource(R.string.finish_structure_keep))
                }
            },
        )
    }
}

/**
 * One label/value line. [onClick] is null for read-only rows, which then take no ripple and offer
 * no click affordance to accessibility services.
 */
@Composable
private fun LabeledRow(
    label: String,
    value: String,
    valueStyle: TextStyle = LocalTextStyle.current,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Text(value, style = valueStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Minutes are zero-padded past the hour mark ("1h 04m") so the mono readout keeps a stable width.
 * Named apart from WorkoutSummaryScreen's own formatter — same package, different file.
 */
private fun formatFinishDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "%dh %02dm".format(h, m) else "%dm".format(m)
}

/**
 * The UTC-midnight value Material3's DatePicker expects, for whichever local day [millis] falls on.
 *
 * The picker's whole API is in UTC-midnight millis while a workout's `startedAt` is a plain
 * instant, so both directions need an explicit conversion through the device zone. Doing it with
 * raw millis arithmetic instead put the highlighted day and the displayed day on different dates
 * for every user east or west of UTC.
 */
internal fun toDatePickerMillis(millis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/** Moves [originalMillis] onto the calendar day the picker returned, keeping its local time-of-day. */
internal fun fromDatePickerMillis(pickedUtcMillis: Long, originalMillis: Long, zone: ZoneId): Long {
    val localTime = Instant.ofEpochMilli(originalMillis).atZone(zone).toLocalTime()
    val pickedDate = Instant.ofEpochMilli(pickedUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return pickedDate.atTime(localTime).atZone(zone).toInstant().toEpochMilli()
}

private fun formatDateTime(millis: Long, clock: DateTimeFormatter): String {
    val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return zoned.format(DateTimeFormatter.ofPattern("d MMM yyyy")) + ", " + zoned.format(clock)
}
