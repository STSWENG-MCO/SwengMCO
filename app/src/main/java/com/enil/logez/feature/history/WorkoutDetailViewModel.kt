package com.enil.logez.feature.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.PolylineEncoding
import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.calc.VolumeCalculator
import com.enil.logez.core.domain.calc.isIncluded
import com.enil.logez.core.domain.repository.ActivityTrackRepository
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import com.enil.logez.core.domain.repository.RoutineRepository
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.workout.InProgressWorkout
import com.enil.logez.feature.workout.InProgressWorkoutResolver
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.session.WorkoutSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import com.enil.logez.core.domain.calc.HeartRateSummary
import com.enil.logez.core.domain.calc.HeartRateSummaryCalculator
import com.enil.logez.core.domain.repository.WorkoutHeartRateSampleRepository
import com.enil.logez.core.wellness.WorkoutHeartRateBackfill
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** PHASE2_PLAN.md §5.2 "Workout Detail": read-only record of one completed workout. */
@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
    private val personalRecordsRepository: PersonalRecordsRepository,
    private val settingsRepository: SettingsRepository,
    private val workoutDeleter: WorkoutDeleter,
    private val workoutToRoutineConverter: WorkoutToRoutineConverter,
    private val workoutStarter: WorkoutStarter,
    private val sessionController: WorkoutSessionController,
    private val activityTrackRepository: ActivityTrackRepository,
    private val inProgressWorkoutResolver: InProgressWorkoutResolver,
    private val sessionDiscarder: SessionDiscarder,
    private val heartRateSampleRepository: WorkoutHeartRateSampleRepository,
    private val heartRateBackfill: WorkoutHeartRateBackfill,
) : ViewModel() {
    /** Which resume path this screen's conflict dialog should take -- see [InProgressWorkoutResolver]. */
    suspend fun inProgressWorkout(): InProgressWorkout? = inProgressWorkoutResolver.resolve()

    private val workoutId: String = checkNotNull(savedStateHandle[WORKOUT_ID_ARG])

    private val _uiState = MutableStateFlow(WorkoutDetailUiState())
    val uiState: StateFlow<WorkoutDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    /**
     * Re-reads from Room — the screen calls this on RESUME, since an edit rewrites what it shows.
     * Then it asks Health Connect for any heart rate that synced since this workout was saved and
     * reloads again if some arrived (2026-09-26): a watch's readings usually arrive after Save, so
     * opening the workout later is when they get added. Room first, so an edit never waits on
     * Health Connect.
     */
    fun refresh() {
        viewModelScope.launch {
            reload()
            val workout = workoutRepository.getById(workoutId) ?: return@launch
            val endedAt = workout.endedAt ?: (workout.startedAt + workout.durationSeconds * 1000L)
            if (heartRateBackfill.backfill(workoutId, workout.startedAt, endedAt) > 0) reload()
        }
    }

    private suspend fun reload() {
        val workout = workoutRepository.getById(workoutId)
        if (workout == null) {
            // Reachable if this workout was just deleted from another surface (or its own overflow
            // menu, whose onDeleted callback pops this screen before this ever runs) — a missing
            // row is not an error, it just means there is nothing left to show.
            _uiState.update { it.copy(isLoading = false, isMissing = true) }
            return
        }

        val settings = settingsRepository.settings.first()
        val includeWarmups = settings.includeWarmupsInStats
        val routineName = workout.routineId?.let { routineRepository.getRoutineById(it)?.name }

        val workoutExercises = workoutRepository.getExercisesForWorkout(workoutId).sortedBy { it.orderIndex }
        // Fetched once: session-scoped PrTypes (BEST_SESSION_VOLUME, MOST_SESSION_REPS) carry a
        // null workoutSetId and never appear on a set badge, but they still count as a record this
        // workout earned — hasRecords below must see them too, not just the set-scoped subset.
        val workoutPrs = personalRecordsRepository.getForWorkout(workoutId)
        val prsBySetId = workoutPrs.filter { it.workoutSetId != null }.associateBy { it.workoutSetId }

        // Resolved once per distinct exercise (not once per exercise block, and not once per set
        // for the volume sum below) -- same batched pattern as HistoryViewModel's card-list build.
        // A workout's own exercise count is small, but this was still one getById() call per
        // exercise block AND a second one per completed set of that exercise.
        val exercisesById = workoutExercises.map { it.exerciseId }.distinct()
            .mapNotNull { id -> exerciseRepository.getById(id)?.let { id to it } }
            .toMap()

        val exerciseBlocks = workoutExercises.map { we ->
            val exercise = exercisesById[we.exerciseId]
            val sets = workoutRepository.getSetsForWorkoutExercise(we.id).sortedBy { it.orderIndex }
            DetailExerciseBlock(
                workoutExercise = we,
                exercise = exercise,
                sets = sets.map { ws ->
                    DetailSetRow(
                        setId = ws.id,
                        orderIndex = ws.orderIndex,
                        setType = ws.setType,
                        weightKg = ws.weightKg,
                        reps = ws.reps,
                        durationSeconds = ws.durationSeconds,
                        distanceMeters = ws.distanceMeters,
                        customMetric = ws.customMetric,
                        rpe = ws.rpe,
                        isCompleted = ws.isCompleted,
                        pr = prsBySetId[ws.id],
                    )
                },
            )
        }

        val rows = workoutRepository.getSetsWithExerciseForWorkout(workoutId)
        val included = rows.filter { isIncluded(it.set, includeWarmups) }
        val volumeKg = included.sumOf { row -> setVolume(exercisesById[row.exerciseId], row.set) }

        // M21b/c: any set with an ActivityTrackEntity means this workout was GPS-tracked -- an
        // N-query loop; ActivityTrackRepository has no bulk-lookup method and a workout has at
        // most a handful of sets, so this isn't worth a second repository method for (unlike the
        // exercise lookup above, which is resolved once per distinct exercise, not once per set).
        // Deliberately scans the unfiltered `rows`, not `included`: a recorded GPS track is a fact
        // about what happened, not a stats-inclusion choice, so re-tagging the tracked set as a
        // warm-up (which drops it from `included`, and so from hasVolume/hasDistance above) must
        // not also hide the Route card -- the two are intentionally independent, not copy-paste
        // drift. WorkoutSummaryViewModel's routePoints lookup makes the identical choice, and for
        // the identical reason, so the two screens can't disagree about whether a route exists.
        val track = rows.firstNotNullOfOrNull { row -> activityTrackRepository.getByWorkoutSetId(row.set.setId) }
        val routePoints = track?.routePolyline?.let(PolylineEncoding::decode) ?: emptyList()

        val heartRateSummary = HeartRateSummaryCalculator.summarize(
            samples = heartRateSampleRepository.getForWorkout(workoutId).map { it.recordedAt to it.bpm },
            windowStartMillis = workout.startedAt,
            windowEndMillis = workout.endedAt ?: (workout.startedAt + workout.durationSeconds * 1000L),
            maxHeartRateBpm = settings.maxHeartRateBpm,
        )

        _uiState.value = WorkoutDetailUiState(
            isLoading = false,
            workout = workout,
            heartRateSummary = heartRateSummary,
            routineName = routineName,
            durationSeconds = workout.durationSeconds,
            weightUnit = settings.weightUnit,
            distanceUnit = settings.distanceUnit,
            volumeKg = volumeKg,
            // A GPS-tracked walk/run never logged weight -- "0kg Volume" would be noise next to
            // its real distance, so the cell is gated on whether it was actually tracked (same
            // rationale as WorkoutSummaryViewModel/HistoryViewModel).
            hasVolume = included.any { it.set.weightKg != null },
            hasDistance = included.any { it.set.distanceMeters != null },
            distanceMeters = included.sumOf { it.set.distanceMeters ?: 0.0 },
            completedSetCount = included.size,
            hasRecords = workoutPrs.isNotEmpty(),
            exerciseBlocks = exerciseBlocks,
            // A track with no recorded point has nothing to draw; showing its card put an empty map
            // centred on the Metro Manila fallback on screen. The walk/run summary says in words
            // that GPS recorded no points instead (2026-09-26).
            hasRoute = track?.routePolyline != null,
            routePoints = routePoints,
        )
    }

    private fun setVolume(exercise: Exercise?, set: StatSet): Double {
        if (exercise == null) return 0.0
        return VolumeCalculator.setVolume(
            exerciseType = exercise.exerciseType,
            isBodyweightVolumeEligible = exercise.isBodyweightVolumeEligible,
            weightKg = set.weightKg,
            reps = set.reps,
            bodyweightKg = null,
        )
    }

    /** §5.2 "Copy Workout" — mirrors [com.enil.logez.feature.routines.RoutineDetailViewModel.startRoutine]. */
    suspend fun startCopy(): StartResult {
        val result = workoutStarter.startFromWorkoutOrConflict(workoutId)
        if (result is StartResult.Started) sessionController.startSession(result.workoutId)
        return result
    }

    suspend fun discardInProgressAndStartCopy(): String {
        sessionDiscarder.discardInProgress()
        val id = workoutStarter.startFromWorkout(workoutId)
        sessionController.startSession(id)
        return id
    }

    /** §5.2 "Save as Routine" — returns the new routine's id so the caller can open its editor. */
    suspend fun saveAsRoutine(): String? = workoutToRoutineConverter.convert(workoutId)

    /** §5.2 "Delete Workout" — hard delete + PR rebuild for every exercise the workout touched. */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            workoutDeleter.delete(workoutId)
            onDeleted()
        }
    }

    companion object {
        const val WORKOUT_ID_ARG = "workoutId"
    }
}

data class WorkoutDetailUiState(
    val isLoading: Boolean = true,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val isMissing: Boolean = false,
    val workout: WorkoutEntity? = null,
    val routineName: String? = null,
    val durationSeconds: Int = 0,
    val volumeKg: Double = 0.0,
    /** Whether any included set actually logged that field -- gates the matching stat cell. */
    val hasVolume: Boolean = false,
    val hasDistance: Boolean = false,
    val distanceMeters: Double = 0.0,
    val completedSetCount: Int = 0,
    val hasRecords: Boolean = false,
    val exerciseBlocks: List<DetailExerciseBlock> = emptyList(),
    /** M21b: true if any set in this workout was GPS-tracked -- shows the Route card. */
    val hasRoute: Boolean = false,
    /** M21c: decoded from the tracked set's saved polyline -- the actual line the Route card draws. */
    val routePoints: List<Pair<Double, Double>> = emptyList(),
    /** Saved heart rate for this workout, or null with none; shows the Heart rate card (2026-09-26). */
    val heartRateSummary: HeartRateSummary? = null,
)

data class DetailExerciseBlock(
    val workoutExercise: WorkoutExerciseEntity,
    val exercise: Exercise?,
    val sets: List<DetailSetRow>,
)

data class DetailSetRow(
    val setId: String,
    val orderIndex: Int,
    val setType: com.enil.logez.core.domain.model.SetType,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val customMetric: Double?,
    val rpe: Double?,
    val isCompleted: Boolean,
    val pr: PersonalRecordEntity?,
)
