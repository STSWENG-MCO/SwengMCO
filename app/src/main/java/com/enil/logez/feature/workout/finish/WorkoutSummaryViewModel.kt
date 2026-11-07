package com.enil.logez.feature.workout.finish

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.PolylineEncoding
import com.enil.logez.core.domain.calc.HeartRateSummary
import com.enil.logez.core.domain.calc.HeartRateSummaryCalculator
import com.enil.logez.core.domain.calc.PaceCalculator
import com.enil.logez.core.domain.calc.RouteSplit
import com.enil.logez.core.domain.calc.RouteSplitsCalculator
import com.enil.logez.core.domain.calc.VolumeCalculator
import com.enil.logez.core.domain.calc.WorkoutMuscleTargetCalculator
import com.enil.logez.core.domain.calc.MuscleStatsCalculator
import com.enil.logez.core.domain.calc.RegionShare
import com.enil.logez.core.domain.calc.balanceAxes
import com.enil.logez.core.domain.calc.isIncluded
import com.enil.logez.core.domain.model.GpsActivity
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.domain.repository.ActivityTrackRepository
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutHeartRateSampleRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.core.wellness.WorkoutHeartRateBackfill
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Backs the post-save workout summary: total volume, completed sets, total reps, duration,
 * targeted muscles, and one medal per PR achieved. Reads the already-`COMPLETED` workout — every
 * number here is derived from persisted rows, never from live logger state, so the screen always
 * shows exactly what was saved.
 *
 * Stat parity invariant: the share card never shows a stat the summary screen doesn't — both
 * surfaces render the same Duration/Volume/Sets/Reps/Distance values from this one UiState, so
 * adding a card stat means adding the matching screen cell (and vice versa).
 *
 * A GPS-tracked walk/run (`WorkoutEntity.kind == GPS_TRACKED`) gets its own summary (Owner,
 * 2026-09-26): route, distance, time, pace, splits and heart rate, and no muscle data at all. The
 * Running and Walking seed exercises list leg muscles as secondary targets, which used to light up
 * the body diagram and draw an empty balance radar (their primary group, CARDIO, maps to no body
 * region) on every run's summary.
 */
@HiltViewModel
class WorkoutSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val personalRecordsRepository: PersonalRecordsRepository,
    private val settingsRepository: SettingsRepository,
    private val activityTrackRepository: ActivityTrackRepository,
    private val heartRateSampleRepository: WorkoutHeartRateSampleRepository,
    private val heartRateBackfill: WorkoutHeartRateBackfill,
) : ViewModel() {
    private val workoutId: String = checkNotNull(savedStateHandle[WORKOUT_ID_ARG])

    private val _uiState = MutableStateFlow(WorkoutSummaryUiState())
    val uiState: StateFlow<WorkoutSummaryUiState> = _uiState

    init {
        viewModelScope.launch { load() }
    }

    /**
     * Reads Health Connect again for this workout and, if new heart rate arrived since the last
     * look, rebuilds the summary. The screen calls this whenever it comes back to the foreground:
     * a watch's heart rate usually syncs after Save, typically when the user opens Samsung Health
     * to force it and then returns here (2026-09-26).
     */
    fun refreshHeartRate() {
        viewModelScope.launch {
            val workout = workoutRepository.getById(workoutId) ?: return@launch
            val endedAt = workout.endedAt ?: (workout.startedAt + workout.durationSeconds * 1000L)
            if (heartRateBackfill.backfill(workoutId, workout.startedAt, endedAt) > 0) load()
        }
    }

    private suspend fun load() {
        run {
            val workout = workoutRepository.getById(workoutId) ?: return
            val settings = settingsRepository.settings.first()
            val sets = workoutRepository.getSetsWithExerciseForWorkout(workoutId)
            val included = sets.filter { isIncluded(it.set, settings.includeWarmupsInStats) }
            val workoutPrs = personalRecordsRepository.getForWorkout(workoutId)

            // Resolved once per distinct exercise (not once per set for volume, again per set for
            // muscle targets, again per block for the share-card lines, and again per PR medal --
            // four independent unbatched getById() loops previously, all over the same small set
            // of exercises this one workout actually used).
            val exercisesById = (sets.map { it.exerciseId } + workoutPrs.map { it.exerciseId }).distinct()
                .mapNotNull { id -> exerciseRepository.getById(id)?.let { id to it } }
                .toMap()

            val volumeKg = included.sumOf { row ->
                val exercise = exercisesById[row.exerciseId]
                if (exercise == null) {
                    0.0
                } else {
                    VolumeCalculator.setVolume(
                        exerciseType = exercise.exerciseType,
                        isBodyweightVolumeEligible = exercise.isBodyweightVolumeEligible,
                        weightKg = row.set.weightKg,
                        reps = row.set.reps,
                        // Bodyweight-dependent volume needs a logged bodyweight; without one those
                        // exercise types contribute 0 by VolumeCalculator's own rules (§8.3),
                        // which is the honest answer rather than a fabricated estimate.
                        bodyweightKg = null,
                    )
                }
            }

            val muscleTargets = included.mapNotNull { row ->
                    exercisesById[row.exerciseId]?.let { exercise ->
                        WorkoutMuscleTargetCalculator.TargetSet(
                            primary = exercise.primaryMuscleGroup,
                            secondary = exercise.secondaryMuscleGroups,
                        )
                    }
                }
            val isGpsTracked = workout.kind == WorkoutKind.GPS_TRACKED
            val muscleIntensity = if (isGpsTracked) emptyMap() else WorkoutMuscleTargetCalculator.intensities(muscleTargets)
            val muscleBalance = if (isGpsTracked) {
                emptyList()
            } else {
                balanceAxes(
                    muscleTargets.groupingBy { it.primary }.eachCount().map { (group, count) ->
                        MuscleStatsCalculator.GroupShare(group = group, setCount = count, sharePercent = 0)
                    },
                )
            }

            // Share-card exercise lines, built the way HistoryViewModel builds its card summaries
            // (group by workoutExerciseId, order by exerciseOrderIndex, count through the same
            // `isIncluded` predicate as the Sets stat) so the card can't contradict History.
            val exerciseLines = sets.groupBy { it.workoutExerciseId }
                .entries
                .sortedBy { (_, blockRows) -> blockRows.first().exerciseOrderIndex }
                .map { (_, blockRows) ->
                    val includedRows = blockRows.filter { isIncluded(it.set, settings.includeWarmupsInStats) }
                    SummaryExerciseLine(
                        name = exercisesById[blockRows.first().exerciseId]?.name.orEmpty(),
                        setCount = includedRows.size,
                        // Owner (P-079): each line also carries average reps per set. Null (not 0)
                        // for exercises that log no reps at all — duration/distance rows have no
                        // honest average to show.
                        avgReps = if (includedRows.any { it.set.reps != null } && includedRows.isNotEmpty()) {
                            includedRows.sumOf { it.set.reps ?: 0 }.toDouble() / includedRows.size
                        } else {
                            null
                        },
                    )
                }

            // M21c: scans the unfiltered `sets`, not `included` -- a recorded GPS track is a
            // fact about what happened, not a stats-inclusion choice, so re-tagging this set as a
            // warm-up (excluding it from `included`) must not also hide the route it recorded.
            // WorkoutDetailViewModel's hasRoute check makes the identical choice for the identical
            // reason; keeping the two aligned matters here specifically, since disagreeing would
            // mean the same workout shows a route on one screen and not the other (adversarial
            // review, 2026-09-10). At most one set is ever GPS-tracked per workout today (the
            // "Track a walk/run" flow creates exactly one), so the first hit is the whole answer --
            // an N-query loop like WorkoutDetailViewModel's, same justification (no bulk-lookup
            // method, and a workout has at most a handful of sets).
            val track = sets.firstNotNullOfOrNull { row -> activityTrackRepository.getByWorkoutSetId(row.set.setId) }
            val routePoints = track?.routePolyline?.let(PolylineEncoding::decode) ?: emptyList()
            // Null for runs tracked before route times were saved (2026-09-26); those get no splits.
            val routeTimes = track?.routeTimes?.let(PolylineEncoding::decodeDeltas)?.map { it.toInt() } ?: emptyList()

            // M21f: read from the local cache WorkoutFinisher already wrote at finish time, never
            // Health Connect directly -- by the time this screen shows, the samples (if any) are
            // already saved, same "local cache of what Health Connect already tracks" shape as
            // routePoints/DailyWellnessTotalEntity elsewhere in this milestone.
            val heartRateSamples = heartRateSampleRepository.getForWorkout(workoutId).map { it.recordedAt to it.bpm }

            val totalDistanceMeters = included.sumOf { it.set.distanceMeters ?: 0.0 }
            val windowEnd = workout.endedAt ?: (workout.startedAt + workout.durationSeconds * 1000L)
            val heartRateSummary = if (isGpsTracked) {
                HeartRateSummaryCalculator.summarize(heartRateSamples, workout.startedAt, windowEnd, settings.maxHeartRateBpm)
            } else {
                null
            }
            val averagePace = if (isGpsTracked) {
                PaceCalculator.paceSecondsPerUnit(totalDistanceMeters, workout.durationSeconds, settings.distanceUnit)
            } else {
                null
            }
            val gpsExerciseId = if (isGpsTracked) sets.firstOrNull()?.exerciseId else null

            val prs = workoutPrs.map { pr ->
                PrMedal(
                    exerciseName = exercisesById[pr.exerciseId]?.name.orEmpty(),
                    prType = pr.prType,
                    value = pr.value,
                )
            }

            _uiState.value = WorkoutSummaryUiState(
                isLoading = false,
                weightUnit = settings.weightUnit,
                distanceUnit = settings.distanceUnit,
                title = workout.title,
                startedAtMillis = workout.startedAt,
                durationSeconds = workout.durationSeconds,
                completedSetCount = included.size,
                // Same `included` list as the Sets stat, so warm-up filtering can't drift between
                // the two numbers. Rep-less sets (cardio/time) honestly contribute 0.
                totalReps = included.sumOf { it.set.reps ?: 0 },
                totalVolumeKg = volumeKg,
                // A GPS-tracked walk/run has no weight/reps concept at all -- showing "0kg"/"0 Reps"
                // next to a real distance/duration reads as noise, not data. Gate each cell on
                // whether it was actually logged, not on whether the aggregate happens to be zero
                // (a bodyweight exercise can legitimately total 0kg volume while still being
                // weight-tracked in spirit -- checking the raw field's presence, not the computed
                // total, is what distinguishes "not tracked" from "tracked as zero").
                hasVolume = included.any { it.set.weightKg != null },
                hasReps = included.any { it.set.reps != null },
                hasDistance = included.any { it.set.distanceMeters != null },
                totalDistanceMeters = totalDistanceMeters,
                routePoints = routePoints,
                isGpsTracked = isGpsTracked,
                gpsActivity = GpsActivity.resolve(gpsExerciseId, gpsExerciseId?.let { exercisesById[it]?.name }),
                hasTrack = track != null,
                averagePaceSecondsPerUnit = averagePace,
                // Units per hour, from the same average pace, so the two cells can't disagree.
                averageSpeedPerHour = averagePace?.takeIf { it > 0.0 }?.let { 3600.0 / it },
                heartRateSummary = heartRateSummary,
                splits = if (isGpsTracked) {
                    RouteSplitsCalculator.splits(
                        routePoints, routeTimes, settings.distanceUnit, workout.startedAt,
                        heartRateSummary?.samples.orEmpty(), trackedDistanceMeters = totalDistanceMeters,
                    )
                } else {
                    emptyList()
                },
                paceSeries = if (isGpsTracked) {
                    RouteSplitsCalculator.paceSeries(routePoints, routeTimes, settings.distanceUnit, workout.startedAt, totalDistanceMeters)
                } else {
                    emptyList()
                },
                heartRateSamples = heartRateSamples,
                muscleIntensity = muscleIntensity,
                muscleDiagramVariant = settings.muscleDiagramVariant,
                muscleBalance = muscleBalance,
                prMedals = prs,
                exerciseLines = exerciseLines,
                structure = workout.structure,
                // M11, exactly HistoryViewModel's derivation: the saved rows are post-purge, so
                // this is the count of rounds that actually survived — MAX across blocks.
                rounds = sets.groupBy { it.workoutExerciseId }.values.maxOfOrNull { it.size } ?: 0,
            )
        }
    }

    companion object {
        const val WORKOUT_ID_ARG = "workoutId"
    }
}

data class PrMedal(val exerciseName: String, val prType: PrType, val value: Double)

/** One share-card exercise line, History's "3 × Bench Press (Barbell)" convention. */
data class SummaryExerciseLine(val name: String, val setCount: Int, val avgReps: Double? = null)

data class WorkoutSummaryUiState(
    val isLoading: Boolean = true,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val title: String = "",
    val startedAtMillis: Long = 0L,
    val durationSeconds: Int = 0,
    val completedSetCount: Int = 0,
    /** Reps summed over the same `isIncluded` set list as [completedSetCount]; null reps count 0. */
    val totalReps: Int = 0,
    val totalVolumeKg: Double = 0.0,
    /** Whether any included set actually logged that field -- gates the matching summary cell. */
    val hasVolume: Boolean = false,
    val hasReps: Boolean = false,
    val hasDistance: Boolean = false,
    val totalDistanceMeters: Double = 0.0,
    /** Decoded from the workout's `ActivityTrackEntity`, if any set in it was GPS-tracked. */
    val routePoints: List<Pair<Double, Double>> = emptyList(),
    /** M21f: (recordedAtMillis, bpm) pairs saved by `WorkoutFinisher` at finish time, oldest first; empty if no wearable data existed for this workout's window. */
    val heartRateSamples: List<Pair<Long, Long>> = emptyList(),
    val muscleIntensity: Map<com.enil.logez.core.domain.model.MuscleGroup, Float> = emptyMap(),
    val muscleDiagramVariant: MuscleDiagramVariant = MuscleDiagramVariant.MALE,
    val muscleBalance: List<RegionShare> = emptyList(),
    val prMedals: List<PrMedal> = emptyList(),
    val exerciseLines: List<SummaryExerciseLine> = emptyList(),
    /** M11: CIRCUIT summaries add a "CIRCUIT · N rounds" line on screen and card. */
    val structure: WorkoutStructure = WorkoutStructure.REGULAR,
    val rounds: Int = 0,
    /** A GPS-tracked walk/run: the summary switches to its own route-first layout. */
    val isGpsTracked: Boolean = false,
    val gpsActivity: GpsActivity = GpsActivity.RUN,
    /**
     * Whether a track row exists at all. False only for an interrupted run whose time was kept
     * without a route; true with an empty [routePoints] when GPS never produced a usable fix.
     */
    val hasTrack: Boolean = false,
    /** Seconds per km/mi over the whole session, or null under 50 m (PaceCalculator's floor). */
    val averagePaceSecondsPerUnit: Double? = null,
    /** km/h or mph; null exactly when [averagePaceSecondsPerUnit] is. */
    val averageSpeedPerHour: Double? = null,
    /** GPS workouts only: null without a heart-rate sample inside the workout's window. */
    val heartRateSummary: HeartRateSummary? = null,
    /** GPS workouts with saved route times only (tracked from 2026-09-26 on). */
    val splits: List<RouteSplit> = emptyList(),
    /** (epochMillis, seconds per unit), smoothed over a minute; same availability as [splits]. */
    val paceSeries: List<Pair<Long, Double>> = emptyList(),
)
