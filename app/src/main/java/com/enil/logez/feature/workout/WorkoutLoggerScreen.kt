package com.enil.logez.feature.workout

import android.app.Activity
import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.ConfirmDialog
import com.enil.logez.core.designsystem.DragHandle
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.SyncOptimisticList
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.feature.exercises.ExercisePickerMode
import com.enil.logez.feature.exercises.ExercisePickerSheet
import com.enil.logez.core.wellness.HeartRateSample
import com.enil.logez.feature.workout.finish.fromDatePickerMillis
import com.enil.logez.feature.workout.finish.labelRes
import com.enil.logez.feature.workout.finish.toDatePickerMillis
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.enil.logez.core.designsystem.rememberClockTimeFormatter

/** M20d: which set the screen-hoisted plate calculator sheet is open for. */
private data class PlateTarget(val exerciseId: String, val setId: String, val initialWeightKg: Double?)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutLoggerScreen(
    onExit: () -> Unit,
    onNavigateToFinish: () -> Unit,
    onDiscarded: () -> Unit,
    onExerciseClick: (exerciseId: String) -> Unit,
    onCreateExercise: (prefillName: String?) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: WorkoutLoggerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current
    // Exposed as MutableState (not just `var x by remember {}`) so WorkoutLoggerDialogs and the
    // circuit/regular body composables below can read and write the same instance — same
    // delegate object either way, this just lets it cross a composable-function boundary.
    val pickerModeState = remember { mutableStateOf<ExercisePickerMode?>(null) }
    var pickerMode by pickerModeState
    val replaceTargetIdState = remember { mutableStateOf<String?>(null) }
    val pendingReplaceTargetIdState = remember { mutableStateOf<String?>(null) }
    var replaceTargetId by replaceTargetIdState
    var pendingReplaceTargetId by pendingReplaceTargetIdState
    var menuExpanded by remember { mutableStateOf(false) }
    var timerMenuExpanded by remember { mutableStateOf(false) }
    val showDiscardConfirmState = remember { mutableStateOf(false) }
    var showDiscardConfirm by showDiscardConfirmState
    var isFinishing by remember { mutableStateOf(false) }
    val showDiscardEditConfirmState = remember { mutableStateOf(false) }
    var showDiscardEditConfirm by showDiscardEditConfirmState
    val showEditIncompleteConfirmState = remember { mutableStateOf(false) }
    var showEditIncompleteConfirm by showEditIncompleteConfirmState
    val showEditDatePickerState = remember { mutableStateOf(false) }
    var showEditDatePicker by showEditDatePickerState
    // M11: which round's "Remove Round" is awaiting confirmation (0-based), if any.
    val pendingRemoveRoundIndexState = remember { mutableStateOf<Int?>(null) }
    var pendingRemoveRoundIndex by pendingRemoveRoundIndexState
    // M20d: the set the screen-hoisted (non-modal) plate calculator sheet currently targets, if
    // any — hoisted out of SetRow so the sheet is a single screen-level instance instead of one
    // per row (both the regular and circuit branches share this one sheet host below).
    val plateTargetState = remember { mutableStateOf<PlateTarget?>(null) }
    var plateTarget by plateTargetState
    val snackbarHostState = remember { SnackbarHostState() }
    val editSaveState by viewModel.editSaveState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Stable callback object for card composables — replaces direct viewModel references.
    // Each lambda captures only the ViewModel reference (stable across recompositions); the
    // exercise/set IDs are bound at the call site inside the lambda body.
    val workoutCallbacks = remember {
        WorkoutCallbacks(
            onStartSupersetSelection = viewModel::startSupersetSelection,
            onConfirmSupersetTarget = viewModel::confirmSupersetTarget,
            onRemoveFromSuperset = viewModel::removeFromSuperset,
            onRemoveExercise = viewModel::removeExercise,
            onUpdateNotes = viewModel::updateExerciseNotes,
            onAddSet = viewModel::addSet,
            onAddWarmupSets = viewModel::addWarmupSets,
            onUpdateSetType = viewModel::updateSetType,
            onRemoveSet = viewModel::removeSet,
            onUpdateWeight = viewModel::updateWeight,
            onUpdateReps = viewModel::updateReps,
            onUpdateDuration = viewModel::updateDuration,
            onUpdateDistance = viewModel::updateDistance,
            onUpdateCustomMetric = viewModel::updateCustomMetric,
            onToggleCheck = viewModel::toggleCheck,
            onUpdateRpe = viewModel::updateRpe,
            onStartInlineTimer = viewModel::startInlineTimer,
            onStopInlineTimer = viewModel::stopInlineTimer,
            onOpenPlateCalculator = { exerciseId, setId, currentWeightKg ->
                plateTarget = PlateTarget(exerciseId, setId, currentWeightKg)
            },
        )
    }

    // §5.1.10: "Returns to Workout Detail." Driven off ViewModel state, not the tap, so a save
    // that outlives an Activity recreation still navigates when it lands.
    val editSaveFailedMessage = stringResource(R.string.workout_edit_save_failed)
    LaunchedEffect(editSaveState) {
        when (editSaveState) {
            is EditSaveState.Saved -> onExit()
            is EditSaveState.Failed -> {
                snackbarHostState.showSnackbar(editSaveFailedMessage)
                viewModel.clearEditSaveError()
            }
            else -> Unit
        }
    }

    fun handleBack() {
        // §5.1.10: in edit mode nothing has been persisted, so leaving genuinely discards — which
        // is worth confirming. Live logging is the opposite: it is already write-through, so
        // navigating away just leaves the workout IN_PROGRESS (spine); the service keeps it
        // foregrounded and the mini-bar surfaces it globally, so this exits without ceremony.
        if (uiState.isEditMode) showDiscardEditConfirm = true else onExit()
    }
    BackHandler(onBack = ::handleBack)

    // §9.6 Keep-awake: scoped strictly to this screen, cleared on dispose/navigate-away — no wakelock.
    val view = LocalView.current
    DisposableEffect(uiState.keepAwakeEnabled) {
        val window = (view.context as? Activity)?.window
        if (uiState.keepAwakeEnabled) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // §5.1.3 step 7: Smart Superset Scrolling. M20h: animated, not instant — the app's only
    // approved motion (Owner, decisions.md 2026-09-08). An instant jump gives no sense of which
    // way the list moved, so you lose your place mid-set; this is wayfinding, not decoration.
    LaunchedEffect(Unit) {
        viewModel.scrollToExercise.collect { exerciseId ->
            val index = uiState.exercises.indexOfFirst { it.id == exerciseId }
            if (index >= 0) listState.animateScrollToItemInterruptible(index)
        }
    }

    // M11: the circuit analog — round-card indices map 1:1 onto the circuit list's items.
    LaunchedEffect(Unit) {
        viewModel.scrollToRound.collect { roundIndex ->
            if (roundIndex >= 0) listState.animateScrollToItemInterruptible(roundIndex)
        }
    }

    // §5.1.3 step 6 / §8.4: the live PR banner.
    val prBannerPrefix = stringResource(R.string.pr_banner, "")
    LaunchedEffect(Unit) {
        viewModel.prBanner.collect { prTypes ->
            val names = prTypes.joinToString(", ") { resources.getString(it.labelRes()) }
            snackbarHostState.showSnackbar(prBannerPrefix.trimEnd() + " " + names)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = {
                    Column {
                        Text(
                            if (uiState.isEditMode) {
                                stringResource(R.string.workout_edit_title, formatEditDate(uiState.editedStartedAtMillis))
                            } else {
                                uiState.title
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // §5.1.10: no elapsed ticking in edit mode — the stopwatch's slot carries
                        // the same set/volume figures without the live timer that has nothing to
                        // count, and date/duration become editable rows in the body instead.
                        if (uiState.isEditMode) {
                            Text(
                                pluralStringResource(R.plurals.workout_edit_stats, uiState.completedSetCount, uiState.completedSetCount, formatVolumeShort(uiState.totalVolumeKg, uiState.weightUnit)),
                                style = LogEzMono.dataSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                maxLines = 1,
                            )
                        } else {
                            // Owner: this row was reading as stacked, not side-by-side -- it was
                            // always a Row, but AssistChip's Material3 spec padding/min-height made
                            // it visually heavy next to the compact mono stats text, and the stats
                            // text had no maxLines cap, so a long "elapsed · sets · volume" string
                            // could push the chip out of the TopAppBar title slot's limited width.
                            // Box(weight) + a maxLines/ellipsis cap + a hand-built compact chip
                            // (not AssistChip's padding) both fix that.
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .clickable(enabled = uiState.exercises.isNotEmpty()) { timerMenuExpanded = true },
                                ) {
                                    WorkoutStatsText(elapsedSecondsFlow = viewModel.elapsedSecondsFlow)
                                }
                                DropdownMenu(expanded = timerMenuExpanded, onDismissRequest = { timerMenuExpanded = false }) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(if (uiState.isPaused) R.string.workout_resume_timer else R.string.workout_pause_timer)) },
                                        onClick = { timerMenuExpanded = false; viewModel.togglePause() },
                                    )
                                }
                                // M21f: absent whenever Health Connect has nothing to show (not
                                // connected, no permission, no wearable data) -- HeartRateChip
                                // itself returns early on a null value.
                                HeartRateChip(viewModel.liveBpmFlow)
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = ::handleBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (uiState.isEditMode) {
                        TextButton(
                            enabled = uiState.canSaveEdit && editSaveState !is EditSaveState.Saving,
                            onClick = {
                                if (viewModel.uncompletedSetCount() > 0) showEditIncompleteConfirm = true else viewModel.saveEdit()
                            },
                        ) { Text(stringResource(R.string.action_save)) }
                    } else {
                        // A filled green box, not a plain text action (Owner) -- Finish is the
                        // screen's one high-stakes action, so it gets the same vibrant-primary
                        // treatment as every other primary CTA in the app.
                        Button(
                            // Guarded: prepareForFinish does a Room write and the service stop is
                            // an IPC, so the first tap is slow enough to double-tap. Two runs would
                            // end the session, take the no-session duration fallback on the second,
                            // and push a second Save screen onto the back stack.
                            enabled = !isFinishing,
                            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                            modifier = Modifier.padding(end = Spacing.xs),
                            onClick = {
                                // Freeze the live duration into the row, stop the service, then
                                // hand off to the Save screen — the workout stays IN_PROGRESS
                                // until it saves there.
                                isFinishing = true
                                scope.launch {
                                    if (viewModel.prepareForFinish()) {
                                        stopWorkoutSessionService(context)
                                        onNavigateToFinish()
                                    }
                                    isFinishing = false
                                }
                            },
                        ) { Text(stringResource(R.string.workout_finish)) }
                        IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options)) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            // M16: mid-session settings access (rest timer default, sounds, …).
                            // Plain navigation — the session is write-through and foregrounded by
                            // the service, and the ViewModel (kept alive beneath Settings) observes
                            // the settings Flow, so edits made there apply live on return.
                            DropdownMenuItem(text = { Text(stringResource(R.string.settings_title)) }, onClick = { menuExpanded = false; onSettingsClick() })
                            DropdownMenuItem(text = { Text(stringResource(R.string.workout_discard)) }, onClick = { menuExpanded = false; showDiscardConfirm = true })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (uiState.supersetSelectionActive) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(modifier = Modifier.fillMaxWidth().padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.routine_builder_superset_banner), modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::cancelSupersetSelection) { Text(stringResource(R.string.action_cancel)) }
                    }
                }
            }
            if (!uiState.isLoading) {
                // §5.1.10: "In place of the stopwatch, editable Date & time and Duration rows
                // (same pickers as 5.1.8a)."
                if (uiState.isEditMode) {
                    EditDateDurationRows(
                        startedAtMillis = uiState.editedStartedAtMillis,
                        durationSeconds = uiState.editedDurationSeconds,
                        onOpenDatePicker = { showEditDatePicker = true },
                        onDurationChange = viewModel::updateEditedDuration,
                    )
                }

                val isCircuit = uiState.structure == WorkoutStructure.CIRCUIT

                if (isCircuit) {
                    CircuitWorkoutBody(
                        uiState = uiState,
                        viewModel = viewModel,
                        workoutCallbacks = workoutCallbacks,
                        listState = listState,
                        onExerciseClick = onExerciseClick,
                        pickerModeState = pickerModeState,
                        replaceTargetIdState = replaceTargetIdState,
                        pendingReplaceTargetIdState = pendingReplaceTargetIdState,
                        pendingRemoveRoundIndexState = pendingRemoveRoundIndexState,
                    )
                } else {
                    RegularWorkoutBody(
                        uiState = uiState,
                        viewModel = viewModel,
                        workoutCallbacks = workoutCallbacks,
                        listState = listState,
                        onExerciseClick = onExerciseClick,
                        pickerModeState = pickerModeState,
                        replaceTargetIdState = replaceTargetIdState,
                        pendingReplaceTargetIdState = pendingReplaceTargetIdState,
                    )
                }
            }
        }
    }

    WorkoutLoggerDialogs(
        uiState = uiState,
        viewModel = viewModel,
        workoutCallbacks = workoutCallbacks,
        pickerModeState = pickerModeState,
        replaceTargetIdState = replaceTargetIdState,
                        pendingReplaceTargetIdState = pendingReplaceTargetIdState,
        onCreateExercise = onCreateExercise,
        plateTargetState = plateTargetState,
        showEditDatePickerState = showEditDatePickerState,
        showEditIncompleteConfirmState = showEditIncompleteConfirmState,
        showDiscardEditConfirmState = showDiscardEditConfirmState,
        onExit = onExit,
        pendingRemoveRoundIndexState = pendingRemoveRoundIndexState,
        showDiscardConfirmState = showDiscardConfirmState,
        scope = scope,
        context = context,
        onDiscarded = onDiscarded,
    )
}

/**
 * The exercise picker sheet, plate calculator sheet, and the 5 confirm/date-pick `AlertDialog`s
 * `WorkoutLoggerScreen` can show — extracted as pure code motion (2026-09-19 debt audit finding
 * #21) so the main composable's body doesn't have to hold all of this leaf UI in the same scope
 * as the top bar and the circuit/regular list bodies. Each state param is the actual
 * `MutableState` the caller declared (not a value+setter pair) so this stays a single source of
 * truth with the parent, not a copy.
 */
@Composable
private fun WorkoutLoggerDialogs(
    uiState: WorkoutLoggerUiState,
    viewModel: WorkoutLoggerViewModel,
    workoutCallbacks: WorkoutCallbacks,
    pickerModeState: MutableState<ExercisePickerMode?>,
    replaceTargetIdState: MutableState<String?>,
    pendingReplaceTargetIdState: MutableState<String?>,
    onCreateExercise: (prefillName: String?) -> Unit,
    plateTargetState: MutableState<PlateTarget?>,
    showEditDatePickerState: MutableState<Boolean>,
    showEditIncompleteConfirmState: MutableState<Boolean>,
    showDiscardEditConfirmState: MutableState<Boolean>,
    onExit: () -> Unit,
    pendingRemoveRoundIndexState: MutableState<Int?>,
    showDiscardConfirmState: MutableState<Boolean>,
    scope: CoroutineScope,
    context: Context,
    onDiscarded: () -> Unit,
) {
    var pickerMode by pickerModeState
    var replaceTargetId by replaceTargetIdState
    var pendingReplaceTargetId by pendingReplaceTargetIdState
    var plateTarget by plateTargetState
    var showEditDatePicker by showEditDatePickerState
    var showEditIncompleteConfirm by showEditIncompleteConfirmState
    var showDiscardEditConfirm by showDiscardEditConfirmState
    var pendingRemoveRoundIndex by pendingRemoveRoundIndexState
    var showDiscardConfirm by showDiscardConfirmState

    pickerMode?.let { mode ->
        ExercisePickerSheet(
            mode = mode,
            onDismiss = { pickerMode = null; replaceTargetId = null },
            onAddCommitted = { exercises -> viewModel.addExercises(exercises) },
            onExercisePicked = { exercise -> replaceTargetId?.let { viewModel.replaceExercise(it, exercise) } },
            onCreateExercise = onCreateExercise,
        )
    }

    // M20d: one screen-level sheet instance for both the regular and circuit tables (SetRow calls
    // callbacks.onOpenPlateCalculator instead of hosting its own). "Use X" writes the closest
    // ACHIEVED total into the set via the same canonical-kg onUpdateWeight path typing into the
    // cell uses, so it behaves identically in live and edit modes.
    plateTarget?.let { target ->
        PlateCalculatorSheet(
            setId = target.setId,
            initialWeightKg = target.initialWeightKg,
            weightUnit = uiState.plateCalculator.weightUnit,
            equipment = uiState.plateCalculator.equipment,
            onApply = { kg -> workoutCallbacks.onUpdateWeight(target.exerciseId, target.setId, kg) },
            onDismiss = { plateTarget = null },
        )
    }

    if (showEditDatePicker) {
        // Same UTC-vs-local conversion the finish flow needed (§5.1.8a): the picker speaks
        // UTC-midnight millis while the row renders in the device zone, and mixing the two put
        // backdating on the wrong day for anyone east or west of UTC.
        val zone = remember { ZoneId.systemDefault() }
        val startedAt = uiState.editedStartedAtMillis
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = toDatePickerMillis(startedAt, zone))
        DatePickerDialog(
            onDismissRequest = { showEditDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { picked ->
                        viewModel.updateEditedStartedAt(fromDatePickerMillis(picked, startedAt, zone))
                    }
                    showEditDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showEditDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showEditIncompleteConfirm) {
        val uncompletedSetCount = viewModel.uncompletedSetCount()
        ConfirmDialog(
            onDismissRequest = { showEditIncompleteConfirm = false },
            title = pluralStringResource(R.plurals.finish_incomplete_title, uncompletedSetCount, uncompletedSetCount),
            body = stringResource(R.string.finish_incomplete_body),
            confirmLabel = stringResource(R.string.finish_incomplete_confirm),
            onConfirm = viewModel::saveEdit,
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    if (showDiscardEditConfirm) {
        ConfirmDialog(
            onDismissRequest = { showDiscardEditConfirm = false },
            title = stringResource(R.string.workout_edit_discard_title),
            body = stringResource(R.string.workout_edit_discard_body),
            confirmLabel = stringResource(R.string.workout_edit_discard_confirm),
            onConfirm = onExit,
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    // M4c: no confirm dialog here any more — Finish opens §5.1.8's Save Workout screen, which is
    // itself the review-and-confirm step (and owns the incomplete-sets / no-sets warnings).

    // M11: Remove Round holds logged values somewhere — confirm before deleting the whole slice.
    pendingRemoveRoundIndex?.let { roundIndex ->
        ConfirmDialog(
            onDismissRequest = { pendingRemoveRoundIndex = null },
            title = stringResource(R.string.workout_remove_round_title, roundIndex + 1),
            body = stringResource(R.string.workout_remove_round_body),
            confirmLabel = stringResource(R.string.workout_remove_round),
            onConfirm = { viewModel.removeRound(roundIndex) },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    // Replace uncompletes every set on the exercise — write-through, no undo — and finishing then
    // purges whatever is still uncompleted. So a replace over logged sets gets the same confirm
    // Remove Round already has; an exercise with nothing logged goes straight to the picker.
    pendingReplaceTargetId?.let { targetId ->
        ConfirmDialog(
            onDismissRequest = { pendingReplaceTargetId = null },
            title = stringResource(R.string.workout_replace_confirm_title),
            body = stringResource(R.string.workout_replace_confirm_body),
            confirmLabel = stringResource(R.string.workout_replace_confirm_action),
            onConfirm = { replaceTargetId = targetId; pickerMode = ExercisePickerMode.REPLACE },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    if (showDiscardConfirm) {
        ConfirmDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = stringResource(R.string.workout_discard_confirm_title),
            body = stringResource(R.string.workout_discard_confirm_body),
            confirmLabel = stringResource(R.string.workout_discard),
            onConfirm = {
                scope.launch {
                    if (viewModel.discard()) {
                        stopWorkoutSessionService(context)
                        onDiscarded()
                    }
                }
            },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

/**
 * The circuit-mode round list, its rest-timer bar, and the Add Round/Add Exercise row — extracted
 * as pure code motion (2026-09-19 debt audit finding #21) alongside [RegularWorkoutBody].
 */
@Composable
private fun ColumnScope.CircuitWorkoutBody(
    uiState: WorkoutLoggerUiState,
    viewModel: WorkoutLoggerViewModel,
    workoutCallbacks: WorkoutCallbacks,
    listState: LazyListState,
    onExerciseClick: (exerciseId: String) -> Unit,
    pickerModeState: MutableState<ExercisePickerMode?>,
    replaceTargetIdState: MutableState<String?>,
    pendingReplaceTargetIdState: MutableState<String?>,
    pendingRemoveRoundIndexState: MutableState<Int?>,
) {
    var pickerMode by pickerModeState
    var replaceTargetId by replaceTargetIdState
    var pendingReplaceTargetId by pendingReplaceTargetIdState
    var pendingRemoveRoundIndex by pendingRemoveRoundIndexState
    val displayConfig = WorkoutLoggerDisplayConfig(
        rpeTrackingEnabled = uiState.rpeTrackingEnabled,
        inlineTimerEnabled = uiState.inlineTimerEnabled,
        isEditMode = uiState.isEditMode,
        plateCalculator = uiState.plateCalculator,
        weightUnit = uiState.weightUnit,
    )

    // M11: round-grouped rendering — one card per round, exercises in sequence
    // order inside it. Grouping is pure (buildCircuitRounds) and never repairs
    // data: unequal set counts render as inert "—" slots.
    val rounds = buildCircuitRounds(uiState.exercises)
    LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
        // Keyed by the round's first surviving set id, not its position: removing
        // an earlier round must not re-attach later cards' remembered UI state
        // (open menus, unparsed cell text) to a different round.
        items(
            items = rounds,
            key = { r -> r.entries.firstNotNullOfOrNull { it.set?.id } ?: "round-${r.roundNumber}" },
        ) { round ->
            CircuitRoundCard(
                round = round,
                callbacks = workoutCallbacks,
                onExerciseClick = onExerciseClick,
                onOpenReplacePicker = { weId -> if (uiState.exercises.hasCompletedSets(weId)) pendingReplaceTargetId = weId else { replaceTargetId = weId; pickerMode = ExercisePickerMode.REPLACE } },
                onRemoveRound = {
                    val roundIndex = round.roundNumber - 1
                    if (viewModel.roundHasLoggedValues(roundIndex)) {
                        pendingRemoveRoundIndex = roundIndex
                    } else {
                        viewModel.removeRound(roundIndex)
                    }
                },
                canRemoveRound = rounds.size > 1,
                inlineTimerExerciseId = uiState.inlineTimerExerciseId,
                inlineTimerSetId = uiState.inlineTimerSetId,
                inlineTimerSecondsFlow = viewModel.inlineTimerSecondsFlow,
                config = displayConfig,
            )
        }
    }

    // M11: in circuit mode the per-exercise cards' rest bar has no single home (a
    // round card holds every exercise), so the countdown + controls dock here
    // instead. Owner, 2026-09-03: moved from above the round list to just above
    // the Add Round/Add Exercise row, matching the regular table's own global bar.
    if (uiState.restExerciseId != null && !uiState.isEditMode) {
        Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
            RestTimerBar(
                remainingMillisFlow = viewModel.restRemainingMillisFlow,
                onMinus15 = { viewModel.adjustRestTimer(-15) },
                onPlus15 = { viewModel.adjustRestTimer(15) },
                onSkip = viewModel::skipRestTimer,
            )
        }
    }

    // M11: + Add Round replaces per-exercise + Add Set — pinned beside Add Exercise.
    Row(modifier = Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Button(onClick = viewModel::addRound, modifier = Modifier.weight(1f), enabled = uiState.exercises.isNotEmpty()) {
            Text(stringResource(R.string.workout_add_round))
        }
        Button(onClick = { pickerMode = ExercisePickerMode.ADD }, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.routine_builder_add_exercise))
        }
    }
}

/**
 * The regular (non-circuit) drag-reorderable exercise list, its rest-timer bar, and the Add
 * Exercise button — extracted as pure code motion (2026-09-19 debt audit finding #21) alongside
 * [CircuitWorkoutBody]. The drag-reorder state (`localExercises`/`reorderState`) is local to this
 * body and was never read outside it, so it moves wholesale with no external threading needed.
 */
@Composable
private fun ColumnScope.RegularWorkoutBody(
    uiState: WorkoutLoggerUiState,
    viewModel: WorkoutLoggerViewModel,
    workoutCallbacks: WorkoutCallbacks,
    listState: LazyListState,
    onExerciseClick: (exerciseId: String) -> Unit,
    pickerModeState: MutableState<ExercisePickerMode?>,
    replaceTargetIdState: MutableState<String?>,
    pendingReplaceTargetIdState: MutableState<String?>,
) {
    var pickerMode by pickerModeState
    var replaceTargetId by replaceTargetIdState
    var pendingReplaceTargetId by pendingReplaceTargetIdState
    val displayConfig = WorkoutLoggerDisplayConfig(
        rpeTrackingEnabled = uiState.rpeTrackingEnabled,
        inlineTimerEnabled = uiState.inlineTimerEnabled,
        isEditMode = uiState.isEditMode,
        plateCalculator = uiState.plateCalculator,
        warmupCalculatorEnabled = uiState.warmupCalculatorEnabled,
        weightUnit = uiState.weightUnit,
    )

    // M20a drag reorder: the ViewModel's reorderExercises() is write-through (one
    // persist{} batch per call) and Reorderable's onMove fires on every hover swap, so
    // a screen-level optimistic copy absorbs the swaps and the ViewModel gets exactly
    // one call on drop. One list instance for the screen's life (see SyncOptimisticList).
    val localExercises = remember { mutableStateListOf<WorkoutExerciseUiModel>().apply { addAll(uiState.exercises) } }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
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
    LazyColumn(state = listState, modifier = Modifier.weight(1f).padding(horizontal = Spacing.md)) {
        items(items = localExercises, key = { it.id }) { exercise ->
            // animateItemModifier = Modifier: no sibling-slide (near-zero-motion rule).
            // Declined at the M20a checkpoint (decisions.md 2026-09-07) -- a settled
            // decision, not an open question.
            ReorderableItem(reorderState, key = exercise.id, animateItemModifier = Modifier) { isDragging ->
                WorkoutExerciseCard(
                    exercise = exercise,
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
                    callbacks = workoutCallbacks,
                    onExerciseClick = { onExerciseClick(exercise.exerciseId) },
                    onOpenReplacePicker = { if (uiState.exercises.hasCompletedSets(exercise.id)) pendingReplaceTargetId = exercise.id else { replaceTargetId = exercise.id; pickerMode = ExercisePickerMode.REPLACE } },
                    onRpeChange = { setId, rpe -> viewModel.updateRpe(exercise.id, setId, rpe) },
                    inlineTimerSetId = if (uiState.inlineTimerExerciseId == exercise.id) uiState.inlineTimerSetId else null,
                    inlineTimerSecondsFlow = viewModel.inlineTimerSecondsFlow,
                    onStartInlineTimer = { setId -> viewModel.startInlineTimer(exercise.id, setId) },
                    onStopInlineTimer = { setId -> viewModel.stopInlineTimer(exercise.id, setId) },
                    config = displayConfig,
                )
            }
        }
    }

    // Owner, 2026-09-03: rest timer moved out of the resting exercise's own card into
    // one persistent bar here, just above Add Exercise — same engine/controls as the
    // circuit bar above, just for the regular (non-circuit) table.
    if (uiState.restExerciseId != null && !uiState.isEditMode) {
        Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
            RestTimerBar(
                remainingMillisFlow = viewModel.restRemainingMillisFlow,
                onMinus15 = { viewModel.adjustRestTimer(-15) },
                onPlus15 = { viewModel.adjustRestTimer(15) },
                onSkip = viewModel::skipRestTimer,
            )
        }
    }

    Button(
        onClick = { pickerMode = ExercisePickerMode.ADD },
        modifier = Modifier.fillMaxWidth().padding(Spacing.md),
    ) {
        Text(stringResource(R.string.routine_builder_add_exercise))
    }
}

/**
 * M20h: `animateScrollToItem` holds the scroll list's `MutatorMutex` at `MutatePriority.Default`
 * for the whole ~300ms animation. Any Default-or-higher mutation that starts while it's running —
 * the user flicking the list themselves (`UserInput`, which outranks `Default`), or Reorderable's
 * own drag-scroll — cancels it with an internal `MutationInterruptedException`. Left uncaught, that
 * exception is a `CancellationException`, so it propagates out of the collecting `LaunchedEffect`
 * and ends it for good: every later superset/circuit auto-scroll for the rest of this screen's life
 * would silently do nothing. `ensureActive()` tells the two cancellation causes apart — rethrow only
 * if this coroutine's own job was the one cancelled, not the mutex's.
 */
private suspend fun LazyListState.animateScrollToItemInterruptible(index: Int) {
    try {
        animateScrollToItem(index)
    } catch (e: CancellationException) {
        currentCoroutineContext().ensureActive()
    }
}

/** Leaf composable (spine rule) — the only thing that recomposes every second is this Text, not the whole TopAppBar. */
@Composable
private fun WorkoutStatsText(elapsedSecondsFlow: Flow<Long>) {
    val elapsedSeconds by elapsedSecondsFlow.collectAsStateWithLifecycle(0L)
    Text(
        formatElapsed(elapsedSeconds),
        style = LogEzMono.dataSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Compact live-heart-rate chip beside the session timer. */
@Composable
private fun HeartRateChip(liveBpmFlow: Flow<HeartRateSample?>, modifier: Modifier = Modifier) {
    val sample by liveBpmFlow.collectAsStateWithLifecycle(null)
    val value = sample?.bpm ?: return
    Surface(
        shape = RoundedCornerShape(Radius.pill),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        modifier = modifier,
    ) {
        Text(
            stringResource(R.string.workout_bpm_value, value),
            style = LogEzMono.dataSmall,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
        )
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}


/**
 * §5.1.10's replacement for the live stopwatch: the workout's date and duration, both editable.
 * Reuses the finish screen's pickers and its digits-only/length-capped duration handling rather
 * than re-deriving them — the same Int-overflow and display-vs-saved-value divergence would apply.
 */
@Composable
private fun EditDateDurationRows(
    startedAtMillis: Long,
    durationSeconds: Int,
    onOpenDatePicker: () -> Unit,
    onDurationChange: (Int) -> Unit,
) {
    var durationText by rememberSaveable { mutableStateOf((durationSeconds / 60).toString()) }
    Column(modifier = Modifier.padding(horizontal = Spacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDatePicker).padding(vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.finish_date_time_label), modifier = Modifier.weight(1f))
            Text(formatEditDateTime(startedAtMillis, rememberClockTimeFormatter()), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.finish_duration_label), modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = durationText,
                onValueChange = { new ->
                    val digits = new.filter { it.isDigit() }.take(5)
                    durationText = digits
                    val minutes = digits.toLongOrNull() ?: 0L
                    if (minutes != durationSeconds / 60L) {
                        onDurationChange((minutes * 60).coerceIn(0L, 99_999L * 60L).toInt())
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                suffix = { Text(stringResource(R.string.unit_minutes_short)) },
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()
    }
}

private fun formatEditDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy"))

private fun formatEditDateTime(millis: Long, clock: DateTimeFormatter): String {
    val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    return zoned.format(DateTimeFormatter.ofPattern("d MMM yyyy")) + ", " + zoned.format(clock)
}

private fun formatVolumeShort(kg: Double, unit: com.enil.logez.core.domain.model.WeightUnit): String = com.enil.logez.core.designsystem.formatWeight(kg, unit)

private fun List<WorkoutExerciseUiModel>.hasCompletedSets(workoutExerciseId: String): Boolean =
    any { it.id == workoutExerciseId && it.sets.any { s -> s.isCompleted } }
