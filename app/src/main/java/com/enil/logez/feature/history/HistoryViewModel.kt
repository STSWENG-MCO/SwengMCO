package com.enil.logez.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.calc.VolumeCalculator
import com.enil.logez.core.domain.calc.isIncluded
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * PHASE2_PLAN.md §5.2 History tab: a reverse-chronological feed of completed workouts, each card
 * carrying its own stats (duration/volume/set count/records) and a short exercise summary.
 *
 * Card stats are computed the same way [com.enil.logez.feature.workout.finish.WorkoutSummaryViewModel]
 * computes them for the just-saved workout (same `isIncluded`/`VolumeCalculator` call, same
 * `bodyweightKg = null`) — deliberately, so a workout never shows one volume number on its Summary
 * screen and a different one back here.
 */
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val personalRecordsRepository: PersonalRecordsRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    val uiState: StateFlow<HistoryUiState> = combine(
        workoutRepository.observeCompleted(),
        settingsRepository.settings,
    ) { workouts, settings -> workouts to settings }
        .map { (workouts, settings) ->
            HistoryUiState(
                isLoading = false,
                cards = buildCards(workouts, settings.includeWarmupsInStats),
                weightUnit = settings.weightUnit,
                distanceUnit = settings.distanceUnit,
            )
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState(isLoading = true))

    /**
     * Three reads for the whole feed, regardless of how many workouts it holds: every completed
     * set, every workout id holding a record, and one exercise lookup per *distinct* exercise.
     * Doing this per card instead cost a query per workout plus one per set — a year of training
     * turned a single feed emission into thousands of round trips.
     */
    private suspend fun buildCards(workouts: List<WorkoutEntity>, includeWarmups: Boolean): List<WorkoutCardModel> {
        if (workouts.isEmpty()) return emptyList()

        val rowsByWorkout = workoutRepository.getSetsWithExerciseForCompletedWorkouts().groupBy { it.set.workoutId }
        val workoutIdsWithRecords = personalRecordsRepository.getWorkoutIdsWithRecords()
        // Resolved per distinct exercise, not per set. getById rather than a bulk active-only
        // fetch on purpose: an exercise soft-deleted since the workout was logged must still
        // resolve here, or its name would vanish from history.
        val exercisesById = rowsByWorkout.values.flatten()
            .map { it.exerciseId }
            .distinct()
            .mapNotNull { id -> exerciseRepository.getById(id)?.let { id to it } }
            .toMap()

        return workouts.map { workout ->
            val rows = rowsByWorkout[workout.id].orEmpty()
            val included = rows.filter { isIncluded(it.set, includeWarmups) }

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
                        bodyweightKg = null,
                    )
                }
            }

            val exerciseSummaries = rows.groupBy { it.workoutExerciseId }
                .entries
                .sortedBy { (_, blockRows) -> blockRows.first().exerciseOrderIndex }
                .map { (_, blockRows) ->
                    ExerciseSummaryLine(
                        name = exercisesById[blockRows.first().exerciseId]?.name.orEmpty(),
                        // Counted through the same `isIncluded` predicate as the card's Sets stat
                        // below (§8.6: one predicate, so warm-up semantics can't drift between
                        // surfaces). Counting raw `isCompleted` here made a card contradict
                        // itself — a workout with warm-ups showed "5 Sets" beside lines summing to 6.
                        setCount = blockRows.count { isIncluded(it.set, includeWarmups) },
                    )
                }

            WorkoutCardModel(
                workoutId = workout.id,
                title = workout.title,
                startedAtMillis = workout.startedAt,
                durationSeconds = workout.durationSeconds,
                volumeKg = volumeKg,
                // A GPS-tracked walk/run has no weight concept at all -- "0kg Volume" next to its
                // real distance would be noise, not data (§ same rationale as WorkoutSummaryViewModel).
                hasVolume = included.any { it.set.weightKg != null },
                hasDistance = included.any { it.set.distanceMeters != null },
                distanceMeters = included.sumOf { it.set.distanceMeters ?: 0.0 },
                setCount = included.size,
                hasRecords = workout.id in workoutIdsWithRecords,
                exerciseSummaries = exerciseSummaries,
                structure = workout.structure,
                // M11: the saved rows are post-purge, so this is the count of rounds that
                // actually survived — MAX across blocks, matching the round-grouped detail view.
                rounds = rows.groupBy { it.workoutExerciseId }.values.maxOfOrNull { it.size } ?: 0,
            )
        }
    }
}

data class HistoryUiState(
    val isLoading: Boolean = true,
    val cards: List<WorkoutCardModel> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
)

data class WorkoutCardModel(
    val workoutId: String,
    val title: String,
    val startedAtMillis: Long,
    val durationSeconds: Int,
    val volumeKg: Double,
    /** Whether any included set actually logged that field -- gates the matching card cell. */
    val hasVolume: Boolean = false,
    val hasDistance: Boolean = false,
    val distanceMeters: Double = 0.0,
    /** Sets counted through `isIncluded` — warm-ups drop out unless the setting says otherwise. */
    val setCount: Int,
    val hasRecords: Boolean,
    val exerciseSummaries: List<ExerciseSummaryLine>,
    /** M11: CIRCUIT cards add a chip and a Rounds stat. */
    val structure: WorkoutStructure = WorkoutStructure.REGULAR,
    val rounds: Int = 0,
)

data class ExerciseSummaryLine(val name: String, val setCount: Int)
