package com.enil.logez.feature.workout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.PreviousValueFormatter
import com.enil.logez.core.domain.calc.WarmupCalculator
import com.enil.logez.core.domain.reorderedBy
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.RpeScale
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.history.WorkoutEditor
import com.enil.logez.core.domain.model.TargetField
import com.enil.logez.core.domain.model.targetFields
import com.enil.logez.feature.workout.finish.LivePrDetector
import com.enil.logez.feature.workout.session.SetCompletionUseCase
import com.enil.logez.feature.workout.session.WorkoutSessionController
import com.enil.logez.feature.workout.session.WorkoutSessionState
import com.enil.logez.core.wellness.HealthMetricsSource
import com.enil.logez.core.wellness.HeartRateSample
import com.enil.logez.core.wellness.liveHeartRateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Intermediate grouping for the exercises + loading pair — makes the combine chain readable
 *  without 14-index casts. */
private data class ExercisesState(
    val exercises: List<WorkoutExerciseUiModel> = emptyList(),
    val isLoading: Boolean = true,
)

/** Intermediate grouping for workout metadata + UI flags. */
private data class WorkoutMeta(
    val workout: WorkoutEntity? = null,
    val supersetSourceId: String? = null,
    val keepAwakeEnabled: Boolean = true,
    val inlineTimerEnabled: Boolean = true,
)

/** Intermediate grouping for the session controller's live state. */
private data class SessionState(
    val isPaused: Boolean = false,
    val restExerciseId: String? = null,
    val inlineTimerExerciseId: String? = null,
    val inlineTimerSetId: String? = null,
)

/** Intermediate grouping for edit-mode held-in-memory values. */
private data class EditState(
    val editedStartedAtMillis: Long = 0L,
    val editedDurationSeconds: Int = 0,
)

/** Intermediate grouping for settings-derived flags. */
private data class SettingsFlags(
    val rpeTrackingEnabled: Boolean = false,
    val plateCalculator: PlateCalculatorConfig = PlateCalculatorConfig(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val warmupCalculatorEnabled: Boolean = true,
)

/**
 * Backs the live workout logger screen: set-by-set logging, the session/rest timers and their
 * foreground service, the finish hand-off, edit mode for backdated entries, RPE, the plate
 * calculator, and the warm-up-set calculator. Fast-tick values (elapsed time, rest countdown, live
 * heart rate) are deliberately exposed as separate cold `Flow`s rather than folded into
 * [WorkoutLoggerUiState] — see the flow properties below for why.
 */
@HiltViewModel
class WorkoutLoggerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionController: WorkoutSessionController,
    private val setCompletionUseCase: SetCompletionUseCase,
    private val livePrDetector: LivePrDetector,
    private val workoutEditor: WorkoutEditor,
    private val healthMetricsSource: HealthMetricsSource,
    private val clock: Clock,
    private val logger: AppLogger = AppLogger.NoOp,
) : ViewModel() {
    private val workoutId: String = checkNotNull(savedStateHandle[WORKOUT_ID_ARG])

    /**
     * §5.1.10 "Edit past workout": the same screen over an already-COMPLETED workout. No service,
     * no rest timers, no sounds, no live PR banners, no elapsed ticking — and, critically, no
     * write-through: see [persist].
     */
    private val isEditMode: Boolean = savedStateHandle[EDIT_MODE_ARG] ?: false

    private val exercises = MutableStateFlow<List<WorkoutExerciseUiModel>>(emptyList())
    private val isLoading = MutableStateFlow(true)
    private val workout = MutableStateFlow<WorkoutEntity?>(null)
    /** Edit mode's replacements for the live stopwatch — held in memory until Save (§5.1.10). */
    private val editedStartedAt = MutableStateFlow(0L)
    private val editedDurationSeconds = MutableStateFlow(0)
    private val _editSaveState = MutableStateFlow<EditSaveState>(EditSaveState.Idle)
    val editSaveState: StateFlow<EditSaveState> = _editSaveState
    private val supersetSource = MutableStateFlow<String?>(null)
    private val keepAwakeEnabled = MutableStateFlow(true)
    private val inlineTimerEnabled = MutableStateFlow(true)
    /** §5.1.7: the RPE column and picker exist only when this setting is on. Like keepAwake/
     * inlineTimer above, kept current by the settings collector in init — M16 made Settings
     * reachable mid-session from this screen's overflow menu (plain navigation, this ViewModel
     * stays alive underneath), so a one-time load snapshot would go stale on return. */
    private val rpeTrackingEnabled = MutableStateFlow(false)
    /** M17 §5.1.5: gate + equipment + display unit for the Plate Calculator, live for the same
     * mid-session-Settings reason as the flags above. */
    private val plateCalculator = MutableStateFlow(PlateCalculatorConfig())
    /** M18: the display unit the weight cells and KG/LBS headers render in (storage stays kg). */
    private val weightUnit = MutableStateFlow(WeightUnit.KG)
    /** M18 §5.1.6: gates the exercise-card "Add warm-up sets" menu item. */
    private val warmupCalculatorEnabled = MutableStateFlow(true)
    /** §5.1.3 step 7: gates smart-superset/circuit scrolling. */
    private val smartSupersetScrolling = MutableStateFlow(true)
    /** §5.1.3 step 6: gates the live in-workout PR banner. */
    private val livePrNotificationEnabled = MutableStateFlow(true)
    /** §8.6: whether warm-up sets are included in volume/PR calculations. */
    private val includeWarmupsInStats = MutableStateFlow(false)

    // Small helper collaborators sharing this ViewModel's own [exercises]/[supersetSource] state
    // and [persist] semantics (2026-09-19 debt audit — see each class's own doc comment for why
    // they're constructed here rather than Hilt-injected: unlike SetCompletionUseCase/
    // LivePrDetector/WorkoutEditor, they need this specific instance's live in-memory state, not
    // just repository access).
    private val notificationContentBuilder = WorkoutNotificationContentBuilder(sessionController)
    private val circuitRoundEditor = CircuitRoundEditor(exercises, workoutRepository, sessionController, ::persist)
    private val supersetEditor = SupersetEditor(exercises, supersetSource, workoutRepository, ::persist)

    /** The (mode, weight unit, distance unit) the PREVIOUS column was last resolved with — the
     * settings collector in init re-queries labels only when this actually changes. */
    private var appliedPreviousKey: Triple<PreviousValuesMode, WeightUnit, DistanceUnit>? = null

    private val _scrollToExercise = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** §5.1.3 step 7 "Smart Superset Scrolling" — the Screen collects this to `animateScrollToItem`. */
    val scrollToExercise: Flow<String> = _scrollToExercise.asSharedFlow()

    private val _scrollToRound = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    /** M11: the circuit analog of smart-superset scrolling — a 0-based round-card index to scroll to after a check. */
    val scrollToRound: Flow<Int> = _scrollToRound.asSharedFlow()

    private val _prBanner = MutableSharedFlow<List<PrType>>(extraBufferCapacity = 4)
    /** §5.1.3 step 6 — PrTypes just achieved, surfaced as a transient in-workout banner. */
    val prBanner: Flow<List<PrType>> = _prBanner.asSharedFlow()

    /**
     * Spine rule (PHASE2_PLAN.md §5.1: "Fast-tick values... are exposed as separate Flows from
     * the ViewModel/service and collected only inside the leaf composable that renders the digits
     * — never folded into the screen-level UiState"). `elapsedSecondsFlow`/`restRemainingMillisFlow`/
     * `inlineTimerSecondsFlow` are per-second self-ticking `flow { while(true) {...; delay(1000)} }`
     * builders (see [WorkoutSessionController]) — folding them into `uiState` would make every
     * collector of `uiState` (an Eagerly-shared StateFlow, collected the instant this ViewModel is
     * constructed) implicitly start that ticking loop for the ViewModel's entire lifetime,
     * including in plain unit tests with no Compose collector ever watching the digits. That's
     * exactly the class of bug M4a already hit once (an unbounded ticker hanging the test suite) —
     * here it resurfaces as a *unit-test hang* even though nothing about these fields is tested,
     * simply because constructing the ViewModel is enough to start them. Exposing them as
     * unaffiliated public `Flow` properties instead means they only ever run if something (a real
     * Composable) actually collects them.
     */
    val elapsedSecondsFlow: Flow<Long> = sessionController.elapsedSecondsFlow
    val restRemainingMillisFlow: Flow<Long?> = sessionController.restRemainingMillisFlow
    val inlineTimerSecondsFlow: Flow<Int?> = sessionController.inlineTimerSecondsFlow

    /**
     * M21f: exactly the same spine-rule cold-flow shape as the three flows above -- `liveHeartRateFlow`
     * is itself a `flow { while(true) {...; delay(...)} }` builder (see its own doc comment for why
     * an earlier, eagerly-started version of this hung the test suite). Owned by this ViewModel,
     * not [WorkoutSessionController] (a BPM poller has nothing to do with that controller's own
     * pause/resume/rest-timer bookkeeping). `emptyFlow()` in edit mode -- there is no live session
     * to sample, matching the other three flows' edit-mode behavior at the Screen layer.
     */
    val liveBpmFlow: Flow<HeartRateSample?> = if (isEditMode) emptyFlow() else liveHeartRateFlow(healthMetricsSource, logger = logger)

    val uiState: StateFlow<WorkoutLoggerUiState> = combine(
        // Group 1: exercises + loading
        combine(exercises, isLoading) { ex, loading ->
            ExercisesState(exercises = ex, isLoading = loading)
        },
        // Group 2: workout + UI flags
        combine(workout, supersetSource, keepAwakeEnabled, inlineTimerEnabled) { w, src, keepAwake, inlineTimer ->
            WorkoutMeta(workout = w, supersetSourceId = src,
                keepAwakeEnabled = keepAwake && !isEditMode, inlineTimerEnabled = inlineTimer && !isEditMode)
        },
        // Group 3: session state
        sessionController.state.map { s ->
            SessionState(
                isPaused = s.isPaused,
                restExerciseId = s.restExerciseId,
                inlineTimerExerciseId = s.inlineTimer?.exerciseId,
                inlineTimerSetId = s.inlineTimer?.setId,
            )
        },
        // Group 4: edit state
        combine(editedStartedAt, editedDurationSeconds) { startedAt, duration ->
            EditState(editedStartedAtMillis = startedAt, editedDurationSeconds = duration)
        },
        // Group 5: settings-derived flags
        combine(rpeTrackingEnabled, plateCalculator, weightUnit, warmupCalculatorEnabled) { rpe, plate, unit, warmup ->
            SettingsFlags(rpeTrackingEnabled = rpe, plateCalculator = plate, weightUnit = unit, warmupCalculatorEnabled = warmup)
        },
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val exState = flows[0] as ExercisesState
        @Suppress("UNCHECKED_CAST")
        val meta = flows[1] as WorkoutMeta
        @Suppress("UNCHECKED_CAST")
        val session = flows[2] as SessionState
        @Suppress("UNCHECKED_CAST")
        val edit = flows[3] as EditState
        @Suppress("UNCHECKED_CAST")
        val settings = flows[4] as SettingsFlags
        val allSets = exState.exercises.flatMap { it.sets }
        WorkoutLoggerUiState(
            isLoading = exState.isLoading,
            title = meta.workout?.title.orEmpty(),
            notes = meta.workout?.notes.orEmpty(),
            structure = meta.workout?.structure ?: WorkoutStructure.REGULAR,
            completedSetCount = allSets.count { it.isCompleted },
            totalVolumeKg = allSets.filter { it.isCompleted }.sumOf { (it.weightKg ?: 0.0) * (it.reps ?: 0) },
            exercises = exState.exercises,
            supersetSelectionActive = meta.supersetSourceId != null,
            supersetSourceExerciseId = meta.supersetSourceId,
            isPaused = session.isPaused,
            restExerciseId = session.restExerciseId,
            keepAwakeEnabled = meta.keepAwakeEnabled,
            inlineTimerEnabled = meta.inlineTimerEnabled,
            inlineTimerExerciseId = session.inlineTimerExerciseId,
            inlineTimerSetId = session.inlineTimerSetId,
            isEditMode = isEditMode,
            editedStartedAtMillis = edit.editedStartedAtMillis,
            editedDurationSeconds = edit.editedDurationSeconds,
            rpeTrackingEnabled = settings.rpeTrackingEnabled,
            plateCalculator = settings.plateCalculator,
            weightUnit = settings.weightUnit,
            // M11/M18 invariant backstop: warm-ups must remain impossible in circuits, so the
            // menu gate is force-false there regardless of the setting.
            warmupCalculatorEnabled = settings.warmupCalculatorEnabled && meta.workout?.structure != WorkoutStructure.CIRCUIT,
            // §5.1.10: "Removing every exercise blocks Save ('Delete the workout instead')." The
            // purge makes the real requirement stronger than a non-empty list: uncompleted sets are
            // dropped on save, so a workout whose every set is unchecked would save as zero
            // exercises — the same empty-COMPLETED state the finish flow refuses to create.
            canSaveEdit = exState.exercises.any { block -> block.sets.any { it.isCompleted } },

        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutLoggerUiState())

    init {
        viewModelScope.launch {
            // No session to rehydrate in edit mode — the workout being edited is COMPLETED, and
            // rehydrating would resurrect whatever live session state the controller last held.
            if (!isEditMode) sessionController.rehydrate()
            val w = workoutRepository.getById(workoutId)
            workout.value = w
            editedStartedAt.value = w?.startedAt ?: 0L
            editedDurationSeconds.value = w?.durationSeconds ?: 0
            val currentSettings = settingsRepository.settings.first()
            // Marks the PREVIOUS column as resolved with these settings BEFORE isLoading flips, so
            // the settings collector below doesn't immediately re-run the queries this load runs.
            appliedPreviousKey = Triple(currentSettings.previousValuesMode, currentSettings.weightUnit, currentSettings.distanceUnit)
            val workoutExercises = workoutRepository.getExercisesForWorkout(workoutId)
            // Batch: fetch all exercise metadata once, all sets once, all previous values once.
            val exerciseIds = workoutExercises.map { it.exerciseId }.distinct()
            val exerciseMap = exerciseIds.mapNotNull { id -> exerciseRepository.getById(id)?.let { id to it } }.toMap()
            // Keyed by workout-exercise INSTANCE id (we.id), never exercise id: the same exercise
            // can be added twice to one workout, and an exercise-id key would hand both cards the
            // merged set list (double counts, shifted PREVIOUS pairing, corrupted orderIndex).
            val setsByWorkoutExercise = (workoutRepository as? com.enil.logez.core.data.repository.WorkoutRepositoryImpl)
                ?.getAllSetsForWorkoutGroupedByWorkoutExercise(workoutId)
                ?: workoutExercises.associate { we -> we.id to workoutRepository.getSetsForWorkoutExercise(we.id) }
            val previousByExercise = workoutRepository.getPreviousWorkoutSetsBulk(
                exerciseIds,
                currentSettings.previousValuesMode,
                w?.routineId,
                beforeStartedAt = if (isEditMode) w?.startedAt else null,
            )
            exercises.value = workoutExercises.map { we ->
                val exercise = exerciseMap[we.exerciseId]
                val previousRows = previousByExercise[we.exerciseId].orEmpty().sortedBy { it.orderIndex }
                val sets = setsByWorkoutExercise[we.id].orEmpty().sortedBy { it.orderIndex }
                WorkoutExerciseUiModel(
                    id = we.id,
                    exerciseId = we.exerciseId,
                    exerciseName = exercise?.name.orEmpty(),
                    exerciseType = exercise?.exerciseType ?: ExerciseType.WEIGHT_REPS,
                    equipment = exercise?.equipment ?: Equipment.NONE,
                    supersetGroup = we.supersetGroup,
                    restTimerSeconds = we.restTimerSeconds,
                    notes = we.notes.orEmpty(),
                    sets = sets.mapIndexed { index, s ->
                        // M11: a circuit pairs PREVIOUS by round (orderIndex), not list position —
                        // the finish purge deletes a skipped round's row WITHOUT re-indexing, so a
                        // prior session's survivors can be {0,2}; positional pairing would shift
                        // round 3's values onto round 2's PREVIOUS label.
                        val previous = if (w?.structure == WorkoutStructure.CIRCUIT) {
                            previousRows.find { it.orderIndex == s.orderIndex }
                        } else {
                            previousRows.getOrNull(index)
                        }
                        s.toUiModel(
                            previousLabel = previous?.let {
                                PreviousValueFormatter.format(it, exercise?.exerciseType ?: ExerciseType.WEIGHT_REPS, currentSettings.weightUnit, currentSettings.distanceUnit)
                            } ?: "—",
                            previousRpeLabel = previous?.let { PreviousValueFormatter.formatRpeLine(it) },
                        )
                    },
                )
            }
            isLoading.value = false
        }

        // M16: the logger's overflow menu opens Settings mid-session with plain navigation, so
        // this ViewModel stays alive beneath the Settings screen and init never re-runs on
        // return. The load-time snapshot these four settings used to live off was fresh pre-M16
        // only because Settings was reachable solely via Profile, which forced a brand-new logger
        // nav entry — now they must be observed for the ViewModel's lifetime or a mid-session
        // toggle (RPE tracking, keep-awake, inline timer, previous-values mode) never applies.
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                keepAwakeEnabled.value = s.keepAwake
                inlineTimerEnabled.value = s.inlineTimerEnabled
                rpeTrackingEnabled.value = s.rpeTrackingEnabled
                plateCalculator.value = PlateCalculatorConfig(
                    enabled = s.plateCalculatorEnabled,
                    equipment = s.plateEquipment,
                    weightUnit = s.weightUnit,
                )
                weightUnit.value = s.weightUnit
                warmupCalculatorEnabled.value = s.warmupCalculatorEnabled
                smartSupersetScrolling.value = s.smartSupersetScrolling
                livePrNotificationEnabled.value = s.livePrNotificationEnabled
                includeWarmupsInStats.value = s.includeWarmupsInStats
                // PREVIOUS re-resolution costs repo queries, so it runs only on a real change
                // after the initial load (which applies the first value itself and stamps the key).
                val key = Triple(s.previousValuesMode, s.weightUnit, s.distanceUnit)
                if (!isLoading.value && key != appliedPreviousKey) {
                    appliedPreviousKey = key
                    refreshPreviousLabels(s)
                }
            }
        }

        // Both collectors below belong to a *live* session. Edit mode has no service, no
        // notification, and no external set-completion source (§5.1.10) — starting them would let
        // an edit session push content into a notification for a workout that finished days ago.
        if (!isEditMode) {
            // Keeps the ongoing notification's content current while this screen is open (§9.3) —
            // see the class doc: while mini-barred with no ViewModel alive, the last-pushed content
            // simply holds until the Logger (and this collector) is alive again.
            viewModelScope.launch {
                combine(exercises, sessionController.state.map { it.restExerciseId }.distinctUntilChanged()) { ex, restId -> ex to restId }
                    .collect { (ex, restId) -> notificationContentBuilder.push(ex, restId, weightUnit.value) }
            }

            // Mirrors a notification-driven "Complete set" action (§9.3 — the Service persists to
            // Room directly since it must work with no ViewModel alive) into this screen's own
            // write-through in-memory state, so an already-open Logger doesn't go stale.
            viewModelScope.launch {
                sessionController.setCompletedExternally.collect { (weId, setId) ->
                    updateExercises { list ->
                        list.map { ex -> if (ex.id != weId) ex else ex.copy(sets = ex.sets.map { if (it.id == setId) it.copy(isCompleted = true, failureError = false) else it }) }
                    }
                }
            }
        }
    }

    /**
     * M16: re-resolves every set's PREVIOUS label after a mid-session change to the previous-values
     * mode (or display units) — the same queries and pairing rules as the init load, applied onto
     * the live in-memory list.
     */
    private suspend fun refreshPreviousLabels(settings: UserSettings) {
        val w = workout.value
        val labelsBySetId = mutableMapOf<String, Pair<String, String?>>()
        for (ex in exercises.value) {
            val previousRows = workoutRepository.getPreviousWorkoutSets(
                ex.exerciseId,
                settings.previousValuesMode,
                w?.routineId,
                beforeStartedAt = if (isEditMode) w?.startedAt else null,
            ).sortedBy { it.orderIndex }
            ex.sets.forEachIndexed { index, s ->
                // Same pairing rule as init: a circuit pairs by round — and the live list keeps
                // list index == orderIndex (addRound appends, removeRound re-indexes), so index
                // stands in for the entity orderIndex init pairs with. Regular is positional.
                val previous = if (w?.structure == WorkoutStructure.CIRCUIT) {
                    previousRows.find { it.orderIndex == index }
                } else {
                    previousRows.getOrNull(index)
                }
                labelsBySetId[s.id] = (previous?.let {
                    PreviousValueFormatter.format(it, ex.exerciseType, settings.weightUnit, settings.distanceUnit)
                } ?: "—") to previous?.let { PreviousValueFormatter.formatRpeLine(it) }
            }
        }
        // Applied by set id onto whatever the list holds NOW — user edits that landed while the
        // queries above ran are preserved, and a set added meanwhile just keeps its default "—".
        updateExercises { list ->
            list.map { ex ->
                ex.copy(sets = ex.sets.map { s ->
                    labelsBySetId[s.id]?.let { (label, rpeLabel) -> s.copy(previousLabel = label, previousRpeLabel = rpeLabel) } ?: s
                })
            }
        }
    }

    private fun updateExercises(transform: (List<WorkoutExerciseUiModel>) -> List<WorkoutExerciseUiModel>) {
        exercises.update { transform(it) }
    }

    /**
     * The single seam between this screen's two persistence contracts.
     *
     * Live logging is write-through (§5.1.3 spine): every edit hits Room immediately, so a process
     * death mid-workout loses nothing. Edit mode is the opposite (§5.1.10): nothing touches the
     * database until Save, which lands "one transaction" — so Back genuinely discards, and killing
     * the app mid-edit leaves the saved workout exactly as it was.
     *
     * EVERY Room write on this screen must route through here — in-memory state is always updated by
     * the caller regardless, so the screen behaves identically in both modes. Four call sites
     * originally missed it because their repository call sat inside their own `viewModelScope.launch`
     * rather than on one line (`addSet`, `addExercises`, `replaceExercise`, `confirmSupersetTarget`),
     * and `replaceExercise` in particular rewrote a COMPLETED workout's exercise id and un-completed
     * all its sets the moment it was tapped — surviving "Discard changes" intact. If you add a write
     * here, wrap it in `persist { }`; a bare `viewModelScope.launch { workoutRepository.… }` is a bug.
     */
    private fun persist(block: suspend () -> Unit) {
        if (isEditMode) return
        viewModelScope.launch { block() }
    }

    private fun findSet(exerciseId: String, setId: String): WorkoutSetUiModel? =
        exercises.value.find { it.id == exerciseId }?.sets?.find { it.id == setId }

    // --- Set-field edits (write-through) ---

    fun updateWeight(exerciseId: String, setId: String, kg: Double?) =
        updateSetField(exerciseId, setId, { it.copy(weightKg = kg) }) { workoutRepository.updateWorkoutSetWeight(setId, kg) }
    fun updateReps(exerciseId: String, setId: String, reps: Int?) =
        updateSetField(exerciseId, setId, { it.copy(reps = reps) }) { workoutRepository.updateWorkoutSetReps(setId, reps) }
    fun updateDuration(exerciseId: String, setId: String, seconds: Int?) =
        updateSetField(exerciseId, setId, { it.copy(durationSeconds = seconds) }) { workoutRepository.updateWorkoutSetDuration(setId, seconds) }
    fun updateDistance(exerciseId: String, setId: String, meters: Double?) =
        updateSetField(exerciseId, setId, { it.copy(distanceMeters = meters) }) { workoutRepository.updateWorkoutSetDistance(setId, meters) }
    fun updateCustomMetric(exerciseId: String, setId: String, value: Double?) =
        updateSetField(exerciseId, setId, { it.copy(customMetric = value) }) { workoutRepository.updateWorkoutSetCustomMetric(setId, value) }
    /** §5.1.7 RPE picker's Done/Clear — [rpe] is one of [RpeScale.VALUES] or null, never free text. */
    fun updateRpe(exerciseId: String, setId: String, rpe: Double?) =
        updateSetField(exerciseId, setId, { it.copy(rpe = rpe) }) { workoutRepository.updateWorkoutSetRpe(setId, rpe) }

    /** [persist] is a targeted single-column DAO write — never a whole-row reconstruction, which would need
     * fields (orderIndex, completedAt, ...) this UI model doesn't track and would silently clobber them. */
    private fun updateSetField(exerciseId: String, setId: String, uiTransform: (WorkoutSetUiModel) -> WorkoutSetUiModel, persistField: suspend () -> Unit) {
        updateExercises { list ->
            list.map { ex ->
                if (ex.id != exerciseId) ex else ex.copy(sets = ex.sets.map { if (it.id == setId) uiTransform(it) else it })
            }
        }
        persist { persistField() }
    }

    fun updateSetType(exerciseId: String, setId: String, type: SetType) {
        // M11: no WARMUP rows inside a circuit — a warm-up would break row-index == round. The UI
        // hides the menu item; this guard is the invariant's backstop.
        if (isCircuit() && type == SetType.WARMUP) return
        updateExercises { list ->
            list.map { ex -> if (ex.id != exerciseId) ex else ex.copy(sets = ex.sets.map { if (it.id == setId) it.copy(setType = type, failureError = false) else it }) }
        }
        persist { workoutRepository.updateWorkoutSetType(setId, type) }
    }

    private fun isCircuit(): Boolean = workout.value?.structure == WorkoutStructure.CIRCUIT

    /**
     * §5.1.3 check gesture. Completing runs through [SetCompletionUseCase] (shared with the
     * notification's "Complete set" action) so the dropset-exception/rest-timer-resolution rule
     * lives in exactly one place; un-completing is local-only (no rest timer to reconsider — the
     * spec doesn't require cancelling a running timer just because an earlier set was reopened).
     * Returns false if rejected (FAILURE set, 0 reps).
     */
    fun toggleCheck(exerciseId: String, setId: String): Boolean {
        val set = findSet(exerciseId, setId) ?: return false
        if (!set.isCompleted && set.setType == SetType.FAILURE && (set.reps == null || set.reps == 0)) {
            updateExercises { list ->
                list.map { ex -> if (ex.id != exerciseId) ex else ex.copy(sets = ex.sets.map { if (it.id == setId) it.copy(failureError = true) else it }) }
            }
            return false
        }
        val nowCompleting = !set.isCompleted
        if (nowCompleting) {
            // §5.1.3 Inline Timer: completing a set with its own running stopwatch must stop and
            // commit it first — the play/pause control only renders for uncompleted sets (it
            // disappears the instant isCompleted flips), so afterward there would be no UI path
            // left to stop it and its elapsed value would never reach durationSeconds.
            sessionController.stopInlineTimer(exerciseId, setId)?.let { seconds -> updateDuration(exerciseId, setId, seconds) }
        }
        updateExercises { list ->
            list.map { ex -> if (ex.id != exerciseId) ex else ex.copy(sets = ex.sets.map { if (it.id == setId) it.copy(isCompleted = nowCompleting, failureError = false) else it }) }
        }
        if (nowCompleting) {
            // Edit mode flips the checkmark in memory only: completeSet() would persist, start a
            // rest timer and play a sound, and a "PR!" banner for a workout logged weeks ago is
            // meaningless — the real records are recomputed wholesale on Save (§5.1.10).
            if (!isEditMode) {
                viewModelScope.launch {
                    setCompletionUseCase.completeSet(workoutId, exerciseId, setId)
                    maybeRaisePrBanner(exerciseId, setId)
                    if (isCircuit()) maybeScrollToNextCircuitRow(exerciseId, setId) else maybeScrollToNextSupersetMember(exerciseId)
                }
            }
        } else {
            persist { workoutRepository.updateWorkoutSetCompletion(setId, false, null) }
        }
        return true
    }

    /** §5.1.3 step 6 / §8.4 point 1 — the live in-workout PR banner, gated by the Live PR setting. */
    private suspend fun maybeRaisePrBanner(exerciseId: String, setId: String) {
        if (!livePrNotificationEnabled.value) return
        val prTypes = livePrDetector.detect(
            workoutId = workoutId,
            workoutExerciseId = exerciseId,
            setId = setId,
            includeWarmupsInStats = includeWarmupsInStats.value,
        )
        if (prTypes.isNotEmpty()) _prBanner.tryEmit(prTypes)
    }

    /**
     * M11 — the circuit analog of smart-superset scrolling, gated by the SAME setting (a circuit
     * is the whole-workout generalization of "alternate exercises, follow me to the next one", so
     * a user who turned that assist off gets no auto-scroll here either). Targets the next
     * incomplete row of the same round, wrapping into later rounds and around to the top.
     */
    private suspend fun maybeScrollToNextCircuitRow(exerciseId: String, setId: String) {
        if (!smartSupersetScrolling.value) return
        val list = exercises.value
        val roundIndex = list.find { it.id == exerciseId }?.sets?.indexOfFirst { it.id == setId } ?: return
        if (roundIndex < 0) return
        val target = nextIncompleteCircuitPosition(list, roundIndex, exerciseId) ?: return
        _scrollToRound.tryEmit(target)
    }

    /** §5.1.3 step 7 — Smart Superset Scrolling, gated by the setting. */
    private suspend fun maybeScrollToNextSupersetMember(exerciseId: String) {
        if (!smartSupersetScrolling.value) return
        val list = exercises.value
        val current = list.find { it.id == exerciseId } ?: return
        val group = current.supersetGroup ?: return
        val members = list.filter { it.supersetGroup == group }
        if (members.size < 2) return
        val index = members.indexOfFirst { it.id == exerciseId }
        if (index < 0) return
        val next = members[(index + 1) % members.size]
        _scrollToExercise.tryEmit(next.id)
    }

    fun addSet(exerciseId: String) {
        // M11: circuit set counts move in lockstep via addRound/removeRound only.
        if (isCircuit()) return
        val exercise = exercises.value.find { it.id == exerciseId } ?: return
        val last = exercise.sets.lastOrNull()
        val newSet = WorkoutSetUiModel(
            id = UUID.randomUUID().toString(),
            setType = SetType.NORMAL,
            weightKg = last?.weightKg,
            reps = last?.reps,
            durationSeconds = last?.durationSeconds,
            distanceMeters = last?.distanceMeters,
            customMetric = last?.customMetric,
        )
        updateExercises { list -> list.map { if (it.id != exerciseId) it else it.copy(sets = it.sets + newSet) } }
        persist {
            workoutRepository.insertWorkoutSet(newSet.toEntity(exerciseId).copy(orderIndex = exercise.sets.size))
        }
    }

    /**
     * M18 §5.1.6 "Add warm-up sets": generates the Warmup Method ladder from the exercise's first
     * working (non-WARMUP) set's weight and PREPENDS it as WARMUP rows at orderIndex 0..n,
     * re-indexing the existing sets after it — write-through like every structural edit.
     * Re-invoking prepends again (the spec's "insert" verb; extras are removed like any set). The
     * UI disables the menu item when no working weight exists; the guard here is the backstop.
     */
    fun addWarmupSets(exerciseId: String) {
        // M11: no WARMUP rows inside a circuit — same invariant as updateSetType's guard.
        if (isCircuit()) return
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            if (!settings.warmupCalculatorEnabled) return@launch
            val exercise = exercises.value.find { it.id == exerciseId } ?: return@launch
            // BODYWEIGHT_ASSISTED: weightKg is assistance, so a %-of-working ladder would be
            // HARDER than the working set — excluded here and in the card's menu gate.
            if (exercise.exerciseType == ExerciseType.BODYWEIGHT_ASSISTED) return@launch
            val workingWeight = exercise.sets.firstOrNull { it.setType != SetType.WARMUP }?.weightKg ?: return@launch
            val plan = WarmupCalculator.generate(
                workingWeightKg = workingWeight,
                method = settings.warmupMethod,
                barbell = exercise.equipment == Equipment.BARBELL,
                equipment = settings.plateEquipment,
            )
            if (plan.isEmpty()) return@launch
            val newSets = plan.map {
                WorkoutSetUiModel(
                    id = UUID.randomUUID().toString(),
                    setType = SetType.WARMUP,
                    weightKg = it.weightKg,
                    reps = it.reps,
                )
            }
            updateExercises { list -> list.map { ex -> if (ex.id != exerciseId) ex else ex.copy(sets = newSets + ex.sets) } }
            persist {
                workoutRepository.insertWorkoutSets(newSets.mapIndexed { index, s -> s.toEntity(exerciseId).copy(orderIndex = index) })
                // Re-index everything below the prepended ladder so orderIndex stays contiguous
                // (reads the post-update list, same idiom as removeRound's re-index).
                exercises.value.find { it.id == exerciseId }?.sets?.forEachIndexed { index, s ->
                    if (index >= newSets.size) workoutRepository.updateWorkoutSetOrderIndex(s.id, index)
                }
            }
        }
    }

    fun removeSet(exerciseId: String, setId: String) {
        // M11: individual-row deletion is disabled in a circuit — a lone missing row would break
        // the row-index == round invariant. Rounds are removed whole via removeRound.
        if (isCircuit()) return
        // Clears an orphaned inline-timer pointer if this exact set's stopwatch was running —
        // it's about to be deleted, so nothing needs committing, just stopped (no-op otherwise).
        sessionController.stopInlineTimer(exerciseId, setId)
        updateExercises { list -> list.map { if (it.id != exerciseId) it else it.copy(sets = it.sets.filterNot { s -> s.id == setId }) } }
        persist { workoutRepository.deleteWorkoutSet(setId) }
    }

    // --- M11 circuit round ops ---

    /**
     * "+ Add Round": appends one set row to EVERY exercise, pre-seeded from that exercise's
     * previous (last) round — the circuit-mode replacement for per-exercise + Add Set. See
     * [CircuitRoundEditor] for the actual mechanics.
     */
    fun addRound() {
        if (!isCircuit()) return
        circuitRoundEditor.addRound()
    }

    /**
     * Round header's "Remove Round" ([roundIndex] 0-based). See [CircuitRoundEditor] for the
     * actual mechanics.
     */
    fun removeRound(roundIndex: Int) {
        if (!isCircuit()) return
        circuitRoundEditor.removeRound(roundIndex)
    }

    /** How many rows a given round holds values in — the Remove Round confirm's "anything logged" check. */
    fun roundHasLoggedValues(roundIndex: Int): Boolean = circuitRoundEditor.roundHasLoggedValues(roundIndex)

    // --- Notes ---

    fun updateWorkoutNotes(text: String) {
        val w = workout.value ?: return
        workout.value = w.copy(notes = text)
        persist { workoutRepository.updateWorkout(w.copy(notes = text, updatedAt = clock.now().toEpochMilliseconds())) }
    }

    fun updateExerciseNotes(exerciseId: String, text: String) {
        updateExercises { list -> list.map { if (it.id == exerciseId) it.copy(notes = text) else it } }
        persist { workoutRepository.updateWorkoutExerciseNotes(exerciseId, text.ifBlank { null }) }
    }

    // --- Exercise ops ---

    /**
     * Adds exercises with auto-fill from the last COMPLETED session. Workouts explicitly started
     * empty always begin each added exercise with one blank row; its PREVIOUS label remains useful,
     * but historical set count and entered values must not become the new workout's prescription.
     */
    fun addExercises(picked: List<Exercise>) {
        viewModelScope.launch {
            val wasEmpty = exercises.value.isEmpty()
            val settings = settingsRepository.settings.first()
            val startIndex = exercises.value.size
            // M11: a circuit newcomer joins every EXISTING round — exactly that many rows, no more
            // (auto-filled from PREVIOUS where a matching round exists, blank past it).
            val circuitRounds = if (isCircuit()) circuitRoundCount(exercises.value).coerceAtLeast(1) else null
            val newModels = mutableListOf<WorkoutExerciseUiModel>()
            val newExerciseEntities = mutableListOf<WorkoutExerciseEntity>()
            val newSetEntities = mutableListOf<WorkoutSetEntity>()

            picked.forEachIndexed { offset, exercise ->
                val weId = UUID.randomUUID().toString()
                // Same edit-mode bound init uses: an exercise added while editing an old workout
                // must prefill from what came BEFORE it, never from a later session.
                val previous = workoutRepository.getPreviousWorkoutSets(
                    exercise.id,
                    settings.previousValuesMode,
                    workout.value?.routineId,
                    beforeStartedAt = if (isEditMode) workout.value?.startedAt else null,
                ).sortedBy { it.orderIndex }
                val sets = if (sessionController.state.value.isEmptyWorkoutTimerMode) {
                    val p = previous.firstOrNull()
                    listOf(
                        WorkoutSetUiModel(
                            id = UUID.randomUUID().toString(),
                            previousLabel = p?.let {
                                PreviousValueFormatter.format(it, exercise.exerciseType, settings.weightUnit, settings.distanceUnit)
                            } ?: "—",
                            previousRpeLabel = p?.let { PreviousValueFormatter.formatRpeLine(it) },
                        ),
                    )
                } else if (circuitRounds != null) {
                    List(circuitRounds) { i ->
                        // By round (orderIndex), not list position — same purge-gap rule as init.
                        val p = previous.find { it.orderIndex == i }
                        WorkoutSetUiModel(
                            id = UUID.randomUUID().toString(), setType = SetType.NORMAL,
                            weightKg = p?.weightKg, reps = p?.reps, durationSeconds = p?.durationSeconds,
                            distanceMeters = p?.distanceMeters, customMetric = p?.customMetric,
                            previousLabel = p?.let { PreviousValueFormatter.format(it, exercise.exerciseType, settings.weightUnit, settings.distanceUnit) } ?: "—",
                            previousRpeLabel = p?.let { PreviousValueFormatter.formatRpeLine(it) },
                        )
                    }
                } else if (previous.isNotEmpty()) {
                    previous.mapIndexed { i, p ->
                        WorkoutSetUiModel(
                            id = UUID.randomUUID().toString(), setType = SetType.NORMAL,
                            weightKg = p.weightKg, reps = p.reps, durationSeconds = p.durationSeconds,
                            distanceMeters = p.distanceMeters, customMetric = p.customMetric,
                            previousLabel = PreviousValueFormatter.format(p, exercise.exerciseType, settings.weightUnit, settings.distanceUnit),
                            previousRpeLabel = PreviousValueFormatter.formatRpeLine(p),
                        )
                    }
                } else {
                    listOf(WorkoutSetUiModel(id = UUID.randomUUID().toString()))
                }
                newModels += WorkoutExerciseUiModel(
                    id = weId, exerciseId = exercise.id, exerciseName = exercise.name, exerciseType = exercise.exerciseType,
                    equipment = exercise.equipment, sets = sets,
                )
                newExerciseEntities += WorkoutExerciseEntity(
                    id = weId, workoutId = workoutId, exerciseId = exercise.id, orderIndex = startIndex + offset,
                    supersetGroup = null, restTimerSeconds = null, notes = null,
                )
                sets.forEachIndexed { i, s -> newSetEntities += s.toEntity(weId).copy(orderIndex = i) }
            }

            updateExercises { it + newModels }
            // Reads above are fine in both modes; only the writes are mode-dependent.
            persist {
                workoutRepository.insertWorkoutExercises(newExerciseEntities)
                workoutRepository.insertWorkoutSets(newSetEntities)
            }
            if (wasEmpty && newModels.isNotEmpty() && sessionController.state.value.isEmptyWorkoutTimerMode) {
                sessionController.resume()
            }
        }
    }

    fun removeExercise(exerciseId: String) {
        val before = exercises.value
        val removedStartingExercise = before.firstOrNull()?.id == exerciseId
        updateExercises { list -> supersetEditor.cleanupOrphans(list.filterNot { it.id == exerciseId }) }
        persist { workoutRepository.deleteWorkoutExercise(exerciseId) }
        if (removedStartingExercise && isEmptyWorkoutGraceActive()) {
            viewModelScope.launch { sessionController.resetElapsedTime(paused = exercises.value.isEmpty()) }
        }
    }

    /**
     * §5.1.9 Replace mode: "completed sets are discarded after confirm — re-attribution is NOT
     * offered." The confirm lives in WorkoutLoggerScreen and is shown only when the exercise has
     * completed sets; the underlying reset (uncomplete every set) is spec-mandated data behavior,
     * not UI polish, so it's unconditional here regardless of whether a dialog warned the user.
     */
    fun replaceExercise(exerciseId: String, newExercise: Exercise) {
        val replacedStartingExercise = exercises.value.firstOrNull()?.id == exerciseId
        val oldExercise = exercises.value.find { it.id == exerciseId } ?: return
        val carriedSets = oldExercise.sets.map {
            it.carryOverTo(oldExercise.exerciseType, newExercise.exerciseType).copy(isCompleted = false)
        }
        updateExercises { list ->
            list.map { ex ->
                if (ex.id != exerciseId) ex else ex.copy(exerciseId = newExercise.id, exerciseName = newExercise.name, exerciseType = newExercise.exerciseType, equipment = newExercise.equipment, sets = carriedSets)
            }
        }
        persist {
            val entities = carriedSets.mapIndexed { index, s -> s.toEntity(exerciseId).copy(orderIndex = index, isCompleted = false, completedAt = null) }
            workoutRepository.replaceWorkoutExerciseExercise(exerciseId, newExercise.id, entities)
        }
        if (replacedStartingExercise && isEmptyWorkoutGraceActive()) {
            viewModelScope.launch { sessionController.resetElapsedTime(paused = false) }
        }
    }

    private fun isEmptyWorkoutGraceActive(): Boolean =
        sessionController.state.value.isEmptyWorkoutTimerMode &&
            sessionController.elapsedSeconds() < EMPTY_WORKOUT_TIMER_GRACE_SECONDS

    /**
     * Applies a drag-reorder drop. Ids the caller does not name keep their relative order after the
     * named ones (M20a): the list comes from a screen-side optimistic copy, so a stale or partial id
     * list must degrade to a lost move, never to a vanished exercise. Live mode writes the resulting
     * full order through in one transaction; edit mode stamps it from list position on Save.
     */
    fun reorderExercises(orderedIds: List<String>) {
        updateExercises { list -> list.reorderedBy(orderedIds) { it.id } }
        val fullOrder = exercises.value.map { it.id }
        persist { workoutRepository.reorderWorkoutExercises(fullOrder) }
    }

    fun startSupersetSelection(sourceExerciseId: String) {
        // M11: no supersets inside a circuit — the circuit IS the sequence (UI hides the item too).
        if (isCircuit()) return
        supersetEditor.startSelection(sourceExerciseId)
    }
    fun cancelSupersetSelection() = supersetEditor.cancelSelection()
    fun confirmSupersetTarget(targetExerciseId: String) = supersetEditor.confirmTarget(targetExerciseId)
    fun removeFromSuperset(exerciseId: String) = supersetEditor.removeFromSuperset(exerciseId)

    fun updateRestTimer(exerciseId: String, seconds: Int?) {
        updateExercises { list -> list.map { if (it.id == exerciseId) it.copy(restTimerSeconds = seconds) else it } }
        persist { workoutRepository.updateWorkoutExerciseRestTimer(exerciseId, seconds) }
    }

    // --- Timers (M4b: §5.1.3/§5.1.4/§9.4) ---

    /** TopAppBar elapsed-duration tap menu: "Pause Workout Timer"/"Resume Workout Timer". */
    fun togglePause() {
        viewModelScope.launch {
            if (uiState.value.isPaused) sessionController.resume() else sessionController.pause()
        }
    }

    fun adjustRestTimer(deltaSeconds: Int) = sessionController.adjustRestTimer(deltaSeconds)
    fun skipRestTimer() = sessionController.skipRestTimer()

    /** §5.1.3 Inline Timer — one at a time; starting a new one implicitly abandons any other running (spec is silent on a conflict UI, and the UI only exposes one play button at a time regardless). */
    fun startInlineTimer(exerciseId: String, setId: String) = sessionController.startInlineTimer(exerciseId, setId)

    fun stopInlineTimer(exerciseId: String, setId: String) {
        val seconds = sessionController.stopInlineTimer(exerciseId, setId) ?: return
        updateDuration(exerciseId, setId, seconds)
    }

    // --- Finish / Discard ---

    /**
     * M4c: Finish no longer completes the workout here — it hands off to §5.1.8's Save Workout
     * screen, which owns the title/date/duration edits, the routine prompts, and the actual save
     * transaction. This only freezes the live duration into the row so the Save screen opens
     * showing what the timer read, and stops the session/service (the workout stays IN_PROGRESS
     * and fully recoverable until saved — §5.1.8's own edge case).
     */
    suspend fun prepareForFinish(): Boolean {
        // Re-read rather than trusting the in-memory copy, which is loaded once in init and never
        // refreshed. On a second Finish (Back out of the Save screen, tap Finish again) the stale
        // copy still holds the durationSeconds = 0 every workout is created with, so the fallback
        // below would write zero over the duration the first Finish had correctly stored.
        val stored = try {
            workoutRepository.getById(workoutId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "prepareForFinish: failed to re-read workout $workoutId", e)
            null
        } ?: return false
        val now = clock.now().toEpochMilliseconds()
        // Only trust the session controller's elapsed time while it is actually tracking THIS
        // workout. Re-entering Finish after a previous attempt (which already called endSession)
        // would otherwise read an empty session as "0 seconds" and clobber a correct stored
        // duration with zero — silently losing the session length the user actually worked.
        val session = sessionController.state.value
        val duration = if (session.workoutId == workoutId) {
            sessionController.elapsedSeconds(now).toInt()
        } else {
            stored.durationSeconds
        }
        // endSession() before the write (synchronous) so a rest timer that's already mid-fire in
        // the Service can't play sound/haptics/a heads-up notification for a workout that's
        // finishing — it cancels the Service's pending deadline-wait before the Room write starts.
        sessionController.endSession()
        val updated = stored.copy(durationSeconds = duration, updatedAt = now)
        return try {
            workoutRepository.updateWorkout(updated)
            workout.value = updated
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The workout stays IN_PROGRESS in the DB either way (this write only freezes the
            // duration) -- surfacing false keeps the user on this screen instead of crashing, and
            // Finish is safely re-tappable since sessionController.endSession() is idempotent.
            logger.e(TAG, "prepareForFinish: failed to persist frozen duration for workout $workoutId", e)
            false
        }
    }

    suspend fun discard(): Boolean {
        sessionController.endSession()
        return try {
            workoutRepository.deleteById(workoutId)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "discard: failed to delete workout $workoutId", e)
            false
        }
    }

    // --- Edit mode (M5b: §5.1.10) ---

    fun updateEditedStartedAt(millis: Long) { editedStartedAt.value = millis }
    fun updateEditedDuration(seconds: Int) { editedDurationSeconds.value = seconds.coerceAtLeast(0) }

    /** How many sets Save is about to discard — §5.1.10's "uncompleted rows dropped after a warning". */
    fun uncompletedSetCount(): Int = exercises.value.sumOf { ex -> ex.sets.count { !it.isCompleted } }

    /**
     * §5.1.10's save: hands the edited in-memory structure to [WorkoutEditor], which lands it in
     * one transaction and rebuilds records. Runs on [viewModelScope], not the caller's composition
     * scope, so an Activity recreation mid-save cannot cancel it part-way (the mistake M4c's finish
     * flow shipped and had to fix).
     */
    fun saveEdit() {
        val w = workout.value ?: return
        // Idle OR Failed — a failed save rolled back entirely, so a retry is both safe and exactly
        // what the user is trying to do. Blocking on "not Idle" made the failure snackbar's own
        // lifetime a dead window where every Save tap was silently swallowed (M4c hit this too).
        if (_editSaveState.value == EditSaveState.Saving || _editSaveState.value == EditSaveState.Saved) return
        if (exercises.value.none { ex -> ex.sets.any { it.isCompleted } }) return // §5.1.10 blocks Save outright
        _editSaveState.value = EditSaveState.Saving
        val snapshot = exercises.value
        val startedAt = editedStartedAt.value
        val duration = editedDurationSeconds.value
        viewModelScope.launch {
            _editSaveState.value = runCatching {
                workoutEditor.save(
                    workout = w,
                    startedAt = startedAt,
                    durationSeconds = duration,
                    exercises = snapshot.mapIndexed { index, ex ->
                        WorkoutExerciseEntity(
                            id = ex.id, workoutId = workoutId, exerciseId = ex.exerciseId, orderIndex = index,
                            supersetGroup = ex.supersetGroup, restTimerSeconds = ex.restTimerSeconds,
                            notes = ex.notes.ifBlank { null },
                        )
                    },
                    sets = snapshot.flatMap { ex ->
                        ex.sets.mapIndexed { index, s ->
                            s.toEntity(ex.id).copy(
                                orderIndex = index,
                                // A set checked off during the edit has no timestamp yet. It was
                                // performed during THIS workout, not now, so it is stamped with the
                                // workout's own start rather than the current clock.
                                completedAt = if (s.isCompleted) (s.completedAt ?: startedAt) else null,
                            )
                        }
                    },
                )
            }.fold(
                onSuccess = { EditSaveState.Saved },
                // The whole transaction rolled back, so the workout is untouched and a retry is safe.
                onFailure = {
                    logger.e(TAG, "saveEdit: workoutEditor.save failed for workout $workoutId", it)
                    EditSaveState.Failed
                },
            )
        }
    }

    fun clearEditSaveError() {
        if (_editSaveState.value == EditSaveState.Failed) _editSaveState.value = EditSaveState.Idle
    }

    companion object {
        const val WORKOUT_ID_ARG = "workoutId"
        const val EDIT_MODE_ARG = "editMode"
        private const val EMPTY_WORKOUT_TIMER_GRACE_SECONDS = 5 * 60L
        private const val TAG = "WorkoutLoggerViewModel"
    }
}

data class WorkoutLoggerUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val notes: String = "",
    /** M11: CIRCUIT flips the screen to round-grouped cards; everything else is shared. */
    val structure: WorkoutStructure = WorkoutStructure.REGULAR,
    val completedSetCount: Int = 0,
    val totalVolumeKg: Double = 0.0,
    val exercises: List<WorkoutExerciseUiModel> = emptyList(),
    val supersetSelectionActive: Boolean = false,
    val supersetSourceExerciseId: String? = null,
    val isPaused: Boolean = false,
    /** Which exercise's card should show the rest-timer bar — the numeric countdown itself is a separate leaf-collected Flow (see [WorkoutLoggerViewModel.restRemainingMillisFlow]). */
    val restExerciseId: String? = null,
    val keepAwakeEnabled: Boolean = true,
    val inlineTimerEnabled: Boolean = true,
    val inlineTimerExerciseId: String? = null,
    val inlineTimerSetId: String? = null,
    /** §5.1.10 edit mode: same screen, no timers/service/banners, nothing persisted until Save. */
    val isEditMode: Boolean = false,
    val editedStartedAtMillis: Long = 0L,
    val editedDurationSeconds: Int = 0,
    /** §5.1.10: "Removing every exercise blocks Save ('Delete the workout instead')." */
    val canSaveEdit: Boolean = false,
    /** §5.1.7: column + picker exist only when this is true — off by default. */
    val rpeTrackingEnabled: Boolean = false,
    /** M17 §5.1.5: Plate Calculator gate, owned equipment, and display unit for the set tables. */
    val plateCalculator: PlateCalculatorConfig = PlateCalculatorConfig(),
    /** M18: the unit weight cells display and accept — storage stays canonical kg (WeightDisplay). */
    val weightUnit: WeightUnit = WeightUnit.KG,
    /** M18 §5.1.6: shows the exercise-card "Add warm-up sets" item. Always false in a circuit. */
    val warmupCalculatorEnabled: Boolean = false,
)

private fun WorkoutSetEntity.toUiModel(previousLabel: String, previousRpeLabel: String? = null) = WorkoutSetUiModel(
    id = id, setType = setType, weightKg = weightKg, reps = reps, durationSeconds = durationSeconds,
    distanceMeters = distanceMeters, customMetric = customMetric, rpe = rpe, isCompleted = isCompleted,
    completedAt = completedAt, previousLabel = previousLabel, previousRpeLabel = previousRpeLabel,
)

internal fun WorkoutSetUiModel.toEntity(workoutExerciseId: String) = WorkoutSetEntity(
    id = id, workoutExerciseId = workoutExerciseId, orderIndex = 0, setType = setType, weightKg = weightKg,
    reps = reps, durationSeconds = durationSeconds, distanceMeters = distanceMeters, rpe = rpe,
    customMetric = customMetric, isCompleted = isCompleted, completedAt = completedAt,
)

/** Reuses the M3 Routine Builder's field-preservation rule (§5.1.2/§5.1.3 share the same Replace Exercise semantics). */
private fun WorkoutSetUiModel.carryOverTo(oldType: ExerciseType, newType: ExerciseType): WorkoutSetUiModel {
    val kept = oldType.targetFields() intersect newType.targetFields()
    return copy(
        weightKg = if (TargetField.WEIGHT in kept) weightKg else null,
        reps = if (TargetField.REPS in kept) reps else null,
        durationSeconds = if (TargetField.DURATION in kept) durationSeconds else null,
        distanceMeters = if (TargetField.DISTANCE in kept) distanceMeters else null,
    )
}

/** Where an edit-mode save stands — held in the ViewModel so it survives Activity recreation. */
sealed interface EditSaveState {
    data object Idle : EditSaveState
    data object Saving : EditSaveState
    data object Saved : EditSaveState
    data object Failed : EditSaveState
}
