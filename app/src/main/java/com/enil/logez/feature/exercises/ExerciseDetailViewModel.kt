package com.enil.logez.feature.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.domain.calc.ChartAggregator
import com.enil.logez.core.domain.calc.ChartMetric
import com.enil.logez.core.domain.calc.ChartPoint
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.calc.SetRecordCalculator
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.calc.WorkoutMuscleTargetCalculator
import com.enil.logez.core.domain.calc.isIncluded
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.ExerciseHistoryEntry
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.MeasurementRepository
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.2 "Exercise Detail". M2 built the How-to and History tabs; M6a adds the
 * Summary tab: per-ExerciseType metric graphs (§8.9), the Personal Records list read from the
 * `personal_records` cache, and the on-demand Set Records table (§8.5).
 *
 * Data loads in [refresh], re-run on every screen RESUME — so the chart window's `today`, the
 * system zone, and the stats-affecting settings (warm-up inclusion, units) are all re-resolved
 * whenever the user returns here, never frozen at construction (the CalendarViewModel lesson).
 */
@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val personalRecordsRepository: PersonalRecordsRepository,
    private val measurementRepository: MeasurementRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val exerciseId: String = checkNotNull(savedStateHandle[EXERCISE_ID_ARG])

    private val _uiState = MutableStateFlow(ExerciseDetailUiState())
    val uiState: StateFlow<ExerciseDetailUiState> = _uiState.asStateFlow()

    /**
     * Everything one refresh loads, held as a single immutable snapshot assigned in one reference
     * write — refresh() suspends several times while loading, and a selectRange/selectMetric tap
     * landing mid-flight must read a coherent old-or-new snapshot, never a torn mix of both.
     */
    private data class SummaryData(
        val statSets: List<StatSet> = emptyList(),
        val bodyweightByWorkoutId: Map<String, Double?> = emptyMap(),
        val records: List<PersonalRecordEntity> = emptyList(),
        val includeWarmupsInStats: Boolean = false,
    )

    private var summaryData = SummaryData()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val exercise = exerciseRepository.getById(exerciseId)
            val history = if (exercise != null) workoutRepository.getExerciseHistory(exerciseId) else emptyList()

            if (exercise != null) {
                val settings = settingsRepository.settings.first()
                val statSets = workoutRepository.getStatSetsForExercise(exerciseId)
                summaryData = SummaryData(
                    statSets = statSets,
                    bodyweightByWorkoutId = resolveBodyweights(exercise, statSets),
                    records = personalRecordsRepository.getForExercise(exerciseId),
                    includeWarmupsInStats = settings.includeWarmupsInStats,
                )

                val metrics = ChartAggregator.metricsFor(exercise.exerciseType)
                val currentSummary = _uiState.value.summary
                val selectedMetric = currentSummary.selectedMetric?.takeIf { it in metrics } ?: metrics.first()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        exercise = exercise,
                        history = history,
                        muscleDiagramVariant = settings.muscleDiagramVariant,
                        muscleIntensity = WorkoutMuscleTargetCalculator.intensities(
                            listOf(WorkoutMuscleTargetCalculator.TargetSet(exercise.primaryMuscleGroup, exercise.secondaryMuscleGroups)),
                        ),
                        summary = buildSummary(
                            exercise = exercise,
                            metrics = metrics,
                            selectedMetric = selectedMetric,
                            selectedRange = currentSummary.selectedRange,
                            weightUnit = settings.perExerciseUnitOverrides[exerciseId] ?: settings.weightUnit,
                            distanceUnit = settings.distanceUnit,
                        ),
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, exercise = null, history = history) }
            }
        }
    }

    fun selectRange(range: ChartRange) = reselect { it.copy(selectedRange = range) }

    fun selectMetric(metric: ChartMetric) = reselect { it.copy(selectedMetric = metric) }

    private fun reselect(change: (SummaryUiState) -> SummaryUiState) {
        val exercise = _uiState.value.exercise ?: return
        val changed = change(_uiState.value.summary)
        _uiState.update {
            it.copy(
                summary = buildSummary(
                    exercise = exercise,
                    metrics = changed.metrics,
                    selectedMetric = changed.selectedMetric ?: return,
                    selectedRange = changed.selectedRange,
                    weightUnit = changed.weightUnit,
                    distanceUnit = changed.distanceUnit,
                ),
            )
        }
    }

    private fun buildSummary(
        exercise: Exercise,
        metrics: List<ChartMetric>,
        selectedMetric: ChartMetric,
        selectedRange: ChartRange,
        weightUnit: WeightUnit,
        distanceUnit: DistanceUnit,
    ): SummaryUiState {
        val data = summaryData
        val zone = ZoneId.systemDefault()
        val today = clock.now().toEpochMilliseconds().let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val window = ChartAggregator.window(selectedRange, today)

        val allPoints = ChartAggregator.points(
            metric = selectedMetric,
            exerciseType = exercise.exerciseType,
            isBodyweightVolumeEligible = exercise.isBodyweightVolumeEligible,
            sets = data.statSets,
            bodyweightByWorkout = data.bodyweightByWorkoutId,
            includeWarmupsInStats = data.includeWarmupsInStats,
        )
        val points = if (window == null) {
            allPoints
        } else {
            allPoints.filter { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() in window }
        }

        // Ordered by the PrType enum's declaration order so every exercise lists its records in
        // the same §8.4-matrix order the plan tables use.
        val recordsByType = data.records.associateBy { it.prType }
        val prRows = PrType.entries.mapNotNull { type ->
            recordsByType[type]?.let { PrRow(type, it.value, it.achievedAt) }
        }

        return SummaryUiState(
            hasAnyLoggedSets = data.statSets.any { isIncluded(it, data.includeWarmupsInStats) },
            metrics = metrics,
            selectedMetric = selectedMetric,
            selectedRange = selectedRange,
            points = points,
            personalRecords = prRows,
            setRecords = SetRecordCalculator.setRecords(exercise.exerciseType, data.statSets, data.includeWarmupsInStats),
            weightUnit = weightUnit,
            distanceUnit = distanceUnit,
        )
    }

    /** Mirrors PersonalRecordsUpdater's per-date memoised resolution — bodyweight only matters for eligible exercises (§8.3). */
    private suspend fun resolveBodyweights(exercise: Exercise, statSets: List<StatSet>): Map<String, Double?> {
        if (!exercise.isBodyweightVolumeEligible) return emptyMap()
        val zone = ZoneId.systemDefault()
        val byDate = mutableMapOf<String, Double?>()
        return statSets.associate { it.workoutId to it.workoutStartedAt }
            .mapValues { (_, startedAt) ->
                val date = Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                byDate.getOrPut(date) { measurementRepository.getLatestWeightKgOnOrBefore(date) }
            }
    }

    /** §5.2: any exercise can be duplicated into a custom copy with no history attached. Returns the new id. */
    suspend fun duplicate(): String? {
        val original = _uiState.value.exercise ?: return null
        val newId = UUID.randomUUID().toString()
        val now = clock.now().toEpochMilliseconds()
        exerciseRepository.upsertCustom(
            original.copy(
                id = newId,
                isCustom = true,
                isBodyweightVolumeEligible = false,
                isDeleted = false,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return newId
    }

    /** §5.2, widened 2026-08-26 to any exercise (seed or custom) — soft delete keeps history queryable. */
    fun delete(onDeleted: () -> Unit) {
        val exercise = _uiState.value.exercise ?: return
        viewModelScope.launch {
            exerciseRepository.softDelete(exercise.id)
            onDeleted()
        }
    }

    companion object {
        const val EXERCISE_ID_ARG = "exerciseId"
    }
}

data class ExerciseDetailUiState(
    val isLoading: Boolean = true,
    val exercise: Exercise? = null,
    val history: List<ExerciseHistoryEntry> = emptyList(),
    /** Which muscles this exercise (its primary + secondary groups) targets, keyed for [com.enil.logez.core.designsystem.BodyDiagram] -- primary=1.0f, each secondary=0.5f, via [WorkoutMuscleTargetCalculator]. */
    val muscleIntensity: Map<MuscleGroup, Float> = emptyMap(),
    val muscleDiagramVariant: MuscleDiagramVariant = MuscleDiagramVariant.MALE,
    val summary: SummaryUiState = SummaryUiState(),
)

/** §5.2 Summary tab state. [points] is already filtered to [selectedRange]'s window. */
data class SummaryUiState(
    val hasAnyLoggedSets: Boolean = false,
    val metrics: List<ChartMetric> = emptyList(),
    val selectedMetric: ChartMetric? = null,
    val selectedRange: ChartRange = ChartRange.LAST_3_MONTHS,
    val points: List<ChartPoint> = emptyList(),
    val personalRecords: List<PrRow> = emptyList(),
    val setRecords: List<SetRecordCalculator.SetRecord> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
)

data class PrRow(val prType: PrType, val value: Double, val achievedAt: Long)
