package com.enil.logez.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.domain.calc.DashboardAggregator
import com.enil.logez.core.domain.calc.StreakCalculator
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.WidgetRefresher
import com.enil.logez.core.domain.repository.DailyWellnessTotal
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.WellnessRepository
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.RoutineRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.activity.ActivityTrackingController
import com.enil.logez.feature.activity.ActivityTrackingStartResult
import com.enil.logez.core.wellness.DailyStepCount
import com.enil.logez.core.wellness.HealthMetricsSource
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.canRead
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.session.WorkoutSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** PHASE2_PLAN.md §5.1.1 Workout tab: folders + routines, unified from three Room `Flow`s. */
@HiltViewModel
class WorkoutTabViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    private val exerciseRepository: ExerciseRepository,
    private val workoutStarter: WorkoutStarter,
    private val sessionController: WorkoutSessionController,
    private val activityTrackingController: ActivityTrackingController,
    private val sessionDiscarder: SessionDiscarder,
    private val healthMetricsSource: HealthMetricsSource,
    private val wellnessRepository: WellnessRepository,
    private val widgetRefresher: WidgetRefresher,
    private val clock: Clock,
) : ViewModel() {
    private val _quickTrackExercises = MutableStateFlow<QuickTrackExercises?>(null)

    private val _todaySteps = MutableStateFlow<Long?>(null)
    private val _recentSteps = MutableStateFlow<List<DailyStepCount>>(emptyList())

    /** M21: the tab's own steps scorecard -- absent (null), not zero, whenever Health Connect has nothing to show (same graceful-degrade rule as the Profile wellness card). */
    val todaySteps: StateFlow<Long?> = _todaySteps.asStateFlow()

    /** Seven calendar days ending today, including explicit zeroes for days with no returned data. */
    val recentSteps: StateFlow<List<DailyStepCount>> = _recentSteps.asStateFlow()

    /** Called via `RefreshOnResume` so the scorecard reflects new steps without requiring a full tab re-entry. */
    fun refreshSteps() {
        viewModelScope.launch {
            val available = healthMetricsSource.canRead(HealthDataType.STEPS)
            if (!available) {
                _todaySteps.value = null
                _recentSteps.value = emptyList()
                return@launch
            }

            val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds())
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
            val start = today.minusDays(6)
            val totals = healthMetricsSource.readTodayTotals()
            val stepsByDate = healthMetricsSource.readStepsHistory(start, today)
                .associate { it.date to it.steps }

            _todaySteps.value = totals.steps
            _recentSteps.value = (0L..6L).map { offset ->
                val date = start.plusDays(offset)
                DailyStepCount(date, if (date == today) totals.steps else stepsByDate[date] ?: 0L)
            }

            // This tab reads steps far more often than Profile does, and until now threw the
            // result away. The widget cannot read Health Connect itself, so this cache is the only
            // way its steps line ever populates.
            wellnessRepository.upsert(
                DailyWellnessTotal(
                    date = today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    steps = totals.steps,
                    caloriesBurned = totals.caloriesBurned,
                    updatedAt = clock.now().toEpochMilliseconds(),
                ),
            )
            widgetRefresher.refresh()
        }
    }

    /**
     * M21a "Track a walk/run": the two seed exercises the card offers, looked up by their exact
     * frozen seed names (`exercises_seed.json`, PHASE2_PLAN §7.9) rather than a hardcoded id, so a
     * user who has edited/deleted either one degrades to the card simply not showing instead of
     * pointing at a stale id. A one-shot lookup, kept out of [uiState]'s own `combine` (already at
     * kotlinx.coroutines' 5-flow typed-overload ceiling) since these two rows aren't expected to
     * change mid-session.
     */
    val quickTrackExercises: StateFlow<QuickTrackExercises?> = _quickTrackExercises.asStateFlow()

    init {
        viewModelScope.launch {
            val active = exerciseRepository.getAllActive()
            val running = active.firstOrNull { it.name == QUICK_TRACK_RUNNING_NAME }
            val walking = active.firstOrNull { it.name == QUICK_TRACK_WALKING_NAME }
            if (running != null && walking != null) {
                _quickTrackExercises.value = QuickTrackExercises(running, walking)
            }
        }
    }

    val uiState: StateFlow<WorkoutTabUiState> = combine(
        routineRepository.observeFolders(),
        routineRepository.observeAllRoutines(),
        // M11: round counts nested with the previews so the outer combine stays within
        // kotlinx.coroutines' 5-flow typed overload (the same trick the heatmap input uses).
        combine(routineRepository.observeRoutineExercisePreviews(), routineRepository.observeRoutineRoundCounts()) { previews, roundCounts ->
            previews to roundCounts
        },
        workoutRepository.observeInProgress(),
        // M8c heatmap: nested so the outer combine stays within kotlinx.coroutines' 5-flow typed
        // overload. Reuses `observeCompleted()` (already Flow-based) rather than the suspend-only
        // `getCompletedWorkoutTimestamps()`, so the heatmap stays reactive with no separate
        // refresh-on-resume load of its own.
        combine(workoutRepository.observeCompleted(), settingsRepository.settings) { completed, settings ->
            completed to settings
        },
    ) { folders, routines, previewInput, inProgress, heatmapInput ->
        val (previewRows, roundCountRows) = previewInput
        val previewByRoutine = previewRows.groupBy { it.routineId }
        val roundsByRoutine = roundCountRows.associate { it.routineId to it.rounds }
        fun cardFor(routine: RoutineEntity): RoutineCardModel {
            val names = previewByRoutine[routine.id].orEmpty().sortedBy { it.orderIndex }.map { it.exerciseName }
            return RoutineCardModel(
                routine = routine,
                exercisePreview = buildExercisePreview(names),
                exerciseCount = names.size,
                rounds = (roundsByRoutine[routine.id] ?: 1).coerceAtLeast(1),
            )
        }
        val routinesByFolder = routines.filter { it.folderId != null }.groupBy { it.folderId }

        // M8c heatmap: "today" re-derives on every recombination (any Room change touching this
        // tab), not on a lifecycle timer — a passive progress widget, not date-critical business
        // logic like StreakCalculator's other consumers (Calendar/Profile), which resolve it on
        // RESUME specifically to survive a real midnight/timezone change mid-visit.
        val (completed, settings) = heatmapInput
        val firstDayOfWeek = settings.firstDayOfWeek
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone).toLocalDate()
        val workoutDates = completed.map { DashboardAggregator.localDate(it.startedAt, zone) }
        val heatmapCounts = StreakCalculator.countsByDate(workoutDates)
        // Same StreakCalculator call Profile/the finish summary already make -- surfaced here too
        // (Owner-requested redesign pass) so the number that keeps someone opening the tab daily
        // isn't buried below the heatmap card's fold. 0 renders as "no chip" at the call site, same
        // honest-empty-state rule as the steps scorecard and quick-track card above it.
        val weeklyStreak = StreakCalculator.weeklyStreak(workoutDates, today, firstDayOfWeek)
        val dailyStreak = StreakCalculator.dailyStreak(workoutDates, today)

        WorkoutTabUiState(
            isLoading = false,
            folders = folders.map { f ->
                FolderSection(folder = f, routines = routinesByFolder[f.id].orEmpty().sortedBy { it.orderIndex }.map(::cardFor))
            },
            rootRoutines = routines.filter { it.folderId == null }.sortedBy { it.orderIndex }.map(::cardFor),
            inProgressWorkoutId = inProgress?.id,
            inProgressWorkoutTitle = inProgress?.title,
            heatmapCounts = heatmapCounts,
            heatmapToday = today,
            heatmapFirstDayOfWeek = firstDayOfWeek,
            weeklyStreak = weeklyStreak,
            dailyStreak = dailyStreak,
            showHeatmap = settings.showHeatmap,
            showGoals = settings.showGoals,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WorkoutTabUiState())

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val now = clock.now().toEpochMilliseconds()
            routineRepository.createFolderAtTop(
                RoutineFolderEntity(id = UUID.randomUUID().toString(), name = name.trim(), orderIndex = 0, createdAt = now, updatedAt = now),
            )
        }
    }

    fun renameFolder(id: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { routineRepository.renameFolder(id, name.trim(), clock.now().toEpochMilliseconds()) }
    }

    fun deleteFolder(folder: RoutineFolderEntity) {
        viewModelScope.launch { routineRepository.deleteFolder(folder) }
    }

    fun reorderFolders(orderedIds: List<String>) {
        viewModelScope.launch { routineRepository.reorderFolders(orderedIds) }
    }

    fun reorderRoutines(orderedIds: List<String>) {
        viewModelScope.launch { routineRepository.reorderRoutines(orderedIds) }
    }

    fun moveRoutineToFolder(routineId: String, folderId: String?) {
        viewModelScope.launch { routineRepository.moveRoutineToFolder(routineId, folderId, clock.now().toEpochMilliseconds()) }
    }

    fun deleteRoutine(id: String) {
        viewModelScope.launch { routineRepository.deleteRoutineById(id) }
    }

    /** §5.1.1 routine three-dots "Duplicate": full structural copy, fresh ids, no history attached. */
    suspend fun duplicateRoutine(routineId: String): String? {
        val original = routineRepository.getRoutineById(routineId) ?: return null
        val originalExercises = routineRepository.getExercisesForRoutine(routineId)
        val now = clock.now().toEpochMilliseconds()
        val newRoutineId = UUID.randomUUID().toString()
        val exerciseIdMap = originalExercises.associate { it.id to UUID.randomUUID().toString() }

        val newExercises = originalExercises.map { re -> re.copy(id = exerciseIdMap.getValue(re.id), routineId = newRoutineId) }
        val newSets = originalExercises.flatMap { re ->
            routineRepository.getSetsForRoutineExercise(re.id).map { s ->
                s.copy(id = UUID.randomUUID().toString(), routineExerciseId = exerciseIdMap.getValue(re.id))
            }
        }
        val newRoutine = original.copy(id = newRoutineId, name = "${original.name} (copy)", createdAt = now, updatedAt = now)

        routineRepository.createRoutineAtTop(newRoutine, newExercises, newSets)
        return newRoutineId
    }

    suspend fun startEmptyWorkout(): StartResult {
        val result = workoutStarter.startEmptyOrConflict()
        if (result is StartResult.Started) sessionController.startSession(result.workoutId, waitForFirstExercise = true)
        return result
    }

    suspend fun startRoutine(routineId: String): StartResult {
        val result = workoutStarter.startFromRoutineOrConflict(routineId)
        if (result is StartResult.Started) sessionController.startSession(result.workoutId)
        return result
    }

    suspend fun discardInProgressAndStartEmpty(): String {
        sessionDiscarder.discardInProgress()
        val id = workoutStarter.startEmpty()
        sessionController.startSession(id, waitForFirstExercise = true)
        return id
    }

    suspend fun discardInProgressAndStartRoutine(routineId: String): String {
        sessionDiscarder.discardInProgress()
        val id = workoutStarter.startFromRoutine(routineId)
        sessionController.startSession(id)
        return id
    }

    /**
     * What the resume dialog needs to route correctly. [InProgressWorkoutInfo.kind] comes from the
     * persisted marker so it still answers after a process death — `activityTrackingController`'s
     * state does not, and reading that alone used to send recovered runs into the strength Logger.
     */
    suspend fun inProgressWorkoutInfo(): InProgressWorkoutInfo? =
        workoutRepository.getInProgress()?.let { InProgressWorkoutInfo(it.id, it.kind, it.startedAt) }

    /** Whether tracking is still actually collecting, which only in-memory state can answer. */
    fun isGpsSessionAlive(): Boolean = activityTrackingController.state.value.isTracking

    /**
     * M21a: starts the ad-hoc workout, the GPS controller, AND `WorkoutSessionController`'s shared
     * elapsed-time state (so the true run-start time is what the Logger's own duration display
     * reflects once tracking finishes and hands off there — see `ActivityTrackingScreen`'s Finish
     * path, which starts `WorkoutSessionService` itself at that point, not here: only one
     * foreground service runs at a time, `ActivityTrackingService` during tracking). The caller
     * (Composable) starts `ActivityTrackingService` and navigates to the live-tracking screen.
     */
    suspend fun startActivityTracking(exerciseId: String, title: String): ActivityTrackingStartResult {
        val result = workoutStarter.startActivityTrackingOrConflict(exerciseId, title)
        if (result is ActivityTrackingStartResult.Started) {
            activityTrackingController.startTracking(result.workoutId, result.workoutSetId)
            sessionController.startSession(result.workoutId)
        }
        return result
    }

    suspend fun discardInProgressAndStartActivityTracking(exerciseId: String, title: String): ActivityTrackingStartResult.Started {
        sessionDiscarder.discardInProgress()
        val (workoutId, workoutSetId) = workoutStarter.startActivityTracking(exerciseId, title)
        activityTrackingController.startTracking(workoutId, workoutSetId)
        sessionController.startSession(workoutId)
        return ActivityTrackingStartResult.Started(workoutId, workoutSetId)
    }
}

/** M21a: the two seed rows the quick-track card offers — exact names from `exercises_seed.json`. */
private const val QUICK_TRACK_RUNNING_NAME = "Running (Outdoor)"
private const val QUICK_TRACK_WALKING_NAME = "Walking (Outdoor)"

data class QuickTrackExercises(val running: Exercise, val walking: Exercise)

/** Just enough about the in-progress workout to route a resume, without leaking the entity. */
data class InProgressWorkoutInfo(val id: String, val kind: WorkoutKind, val startedAt: Long)

data class WorkoutTabUiState(
    val isLoading: Boolean = true,
    val folders: List<FolderSection> = emptyList(),
    val rootRoutines: List<RoutineCardModel> = emptyList(),
    /** §9.5 process-death recovery surfaced directly on the tab — no need to fail a Start tap first to discover it. */
    val inProgressWorkoutId: String? = null,
    val inProgressWorkoutTitle: String? = null,
    /** M8c progress heatmap. */
    val heatmapCounts: Map<LocalDate, Int> = emptyMap(),
    val heatmapToday: LocalDate = LocalDate.ofEpochDay(0),
    val heatmapFirstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    /** Same figure the finish summary/Profile already show (`StreakCalculator.weeklyStreak`). 0 means no chip, not a "0 weeks" chip. */
    val weeklyStreak: Int = 0,
    /** `StreakCalculator.dailyStreak` -- same honest-empty-state rule: 0 means no chip. */
    val dailyStreak: Int = 0,
    /** Owner, 2026-09-03: Settings toggles hiding the heatmap/Goals sections below. Default on. */
    val showHeatmap: Boolean = true,
    val showGoals: Boolean = true,
)

data class FolderSection(val folder: RoutineFolderEntity, val routines: List<RoutineCardModel>)
data class RoutineCardModel(
    val routine: RoutineEntity,
    val exercisePreview: String,
    /** M11 circuit card preview line ("N rounds · M exercises"); harmless extras for REGULAR cards. */
    val exerciseCount: Int = 0,
    val rounds: Int = 1,
)

/** §5.1.1 routine card subtitle: "Bench Press, Incline DB Press, +3 more". Pure — unit-tested directly. */
internal fun buildExercisePreview(names: List<String>): String = when {
    names.isEmpty() -> ""
    names.size <= 2 -> names.joinToString(", ")
    else -> "${names.take(2).joinToString(", ")}, +${names.size - 2} more"
}
