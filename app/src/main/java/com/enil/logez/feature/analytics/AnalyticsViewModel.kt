package com.enil.logez.feature.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.calc.BodyRegion
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.calc.ChartAggregator
import com.enil.logez.core.domain.calc.DashboardAggregator
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.calc.MuscleStatsCalculator
import com.enil.logez.core.domain.calc.RegionShare
import com.enil.logez.core.domain.calc.StatBucket
import com.enil.logez.core.domain.calc.balanceAxes
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.core.wellness.DailyStepCount
import com.enil.logez.core.wellness.HealthMetricsSource
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.canRead
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.2 "Analytics dashboard" — the five in-place cards (the sixth, Monthly Report,
 * is its own screen/ViewModel). Suspend bulk loads refreshed on every screen RESUME (zone/today
 * resolved fresh each time — frozen-field convention), with everything one refresh loads held in
 * a single snapshot assigned in one reference write, so a selector tap landing mid-refresh reads
 * a coherent old-or-new snapshot, never a torn mix (M6a lesson).
 */
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val healthMetricsSource: HealthMetricsSource,
    private val clock: Clock,
) : ViewModel() {

    private data class DashboardData(
        val workouts: List<DashboardAggregator.WorkoutInfo> = emptyList(),
        val sets: List<DashboardAggregator.SetWithExercise> = emptyList(),
        val muscleInputs: List<MuscleStatsCalculator.MuscleSetInput> = emptyList(),
        val includeWarmups: Boolean = false,
        val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
        val weightUnit: WeightUnit = WeightUnit.KG,
        val muscleDiagramVariant: MuscleDiagramVariant = MuscleDiagramVariant.MALE,
        val zone: ZoneId = ZoneId.systemDefault(),
        val today: LocalDate = LocalDate.ofEpochDay(0),
        /** M21: absent (not zero-filled) whenever Health Connect has nothing to show. */
        val stepsAvailable: Boolean = false,
        val stepsHistory: List<DailyStepCount> = emptyList(),
    )

    private data class Selections(
        val trainingMetric: TrainingMetric,
        /** Shared across every [TrainingMetric] (Owner directive) — one range toggle for the whole card, not a per-metric pick. */
        val trainingRange: ChartRange,
        val trainingSelectedBar: Int?,
        val distributionRange: ChartRange,
        val bodyWeek: LocalDate?,
        val setCountRange: ChartRange,
        val setCountBucket: StatBucket,
        val deselectedMuscles: Set<MuscleGroup>,
        val mainRange: ChartRange,
        val stepsSelectedBar: Int?,
    )

    private var data = DashboardData()
    private var selections = Selections(
        trainingMetric = savedStateHandle.get<String>(FOCUS_ARG)
            ?.let { runCatching { TrainingMetric.valueOf(it) }.getOrNull() }
            ?: TrainingMetric.VOLUME,
        trainingRange = ChartRange.LAST_3_MONTHS,
        trainingSelectedBar = null,
        distributionRange = ChartRange.LAST_3_MONTHS,
        bodyWeek = null,
        setCountRange = ChartRange.LAST_3_MONTHS,
        setCountBucket = StatBucket.WEEK,
        deselectedMuscles = emptySet(),
        mainRange = ChartRange.LAST_3_MONTHS,
        stepsSelectedBar = null,
    )

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    /** The screen's single load trigger — RefreshOnResume calls this on every ON_RESUME (no init load). */
    fun refresh() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val zone = ZoneId.systemDefault()
            val workoutEntities = workoutRepository.getCompletedWorkouts()
            val joined = workoutRepository.getSetsWithExerciseForCompletedWorkouts()
            val exerciseById = joined.map { it.exerciseId }.distinct()
                .mapNotNull { id -> exerciseRepository.getById(id)?.let { id to it } }.toMap()

            val sets = joined.mapNotNull { row ->
                val exercise = exerciseById[row.exerciseId] ?: return@mapNotNull null
                DashboardAggregator.SetWithExercise(
                    exerciseId = row.exerciseId,
                    exerciseName = exercise.name,
                    exerciseType = exercise.exerciseType,
                    isBodyweightVolumeEligible = exercise.isBodyweightVolumeEligible,
                    set = row.set,
                )
            }
            val muscleInputs = joined.mapNotNull { row ->
                val exercise = exerciseById[row.exerciseId] ?: return@mapNotNull null
                MuscleStatsCalculator.MuscleSetInput(
                    primaryMuscleGroup = exercise.primaryMuscleGroup,
                    workoutDate = DashboardAggregator.localDate(row.set.workoutStartedAt, zone),
                    setType = row.set.setType,
                    isCompleted = row.set.isCompleted,
                )
            }
            val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone).toLocalDate()

            // M21: same graceful-degrade rule as the Profile wellness card and Workout tab
            // scorecard — absent, not zero-filled, whenever Health Connect has nothing to show.
            // Health Connect's own permission grant only sees the last 30 days of history (its own
            // permission screen says so), so this always asks for exactly that window regardless of
            // the card's own selectors — there's never more to fetch beyond it.
            val stepsAvailable = healthMetricsSource.canRead(HealthDataType.STEPS)
            val stepsHistory = if (stepsAvailable) healthMetricsSource.readStepsHistory(today.minusDays(29), today) else emptyList()

            // A surviving positional selection would re-anchor to a different week when the
            // reloaded bar list shifts — drop it with the data it indexed into.
            selections = selections.copy(trainingSelectedBar = null, stepsSelectedBar = null)
            data = DashboardData(
                workouts = workoutEntities.map { DashboardAggregator.WorkoutInfo(it.id, it.startedAt, it.durationSeconds) },
                sets = sets,
                muscleInputs = muscleInputs,
                includeWarmups = settings.includeWarmupsInStats,
                firstDayOfWeek = settings.firstDayOfWeek,
                weightUnit = settings.weightUnit,
                muscleDiagramVariant = settings.muscleDiagramVariant,
                zone = zone,
                today = today,
                stepsAvailable = stepsAvailable,
                stepsHistory = stepsHistory,
            )
            rebuild(isLoading = false)
        }
    }

    // --- card selectors: every one rebuilds from the same single snapshot ---

    fun selectTrainingMetric(metric: TrainingMetric) {
        selections = selections.copy(trainingMetric = metric, trainingSelectedBar = null); rebuild()
    }

    fun selectTrainingRange(range: ChartRange) {
        selections = selections.copy(trainingRange = range, trainingSelectedBar = null); rebuild()
    }

    fun selectTrainingBar(index: Int?) { selections = selections.copy(trainingSelectedBar = index); rebuild() }
    fun selectDistributionRange(range: ChartRange) { selections = selections.copy(distributionRange = range); rebuild() }
    fun selectBodyWeek(week: LocalDate) { selections = selections.copy(bodyWeek = week); rebuild() }
    fun selectSetCountRange(range: ChartRange) { selections = selections.copy(setCountRange = range); rebuild() }
    fun selectSetCountBucket(bucket: StatBucket) { selections = selections.copy(setCountBucket = bucket); rebuild() }

    /** §5.2 card 4 — tapping a muscle (diagram or row) toggles it out of / back into the stat. */
    fun toggleMuscle(group: MuscleGroup) {
        val current = selections.deselectedMuscles
        selections = selections.copy(deselectedMuscles = if (group in current) current - group else current + group)
        rebuild()
    }

    fun selectMainRange(range: ChartRange) { selections = selections.copy(mainRange = range); rebuild() }

    fun selectStepsBar(index: Int?) { selections = selections.copy(stepsSelectedBar = index); rebuild() }

    private fun rebuild(isLoading: Boolean = _uiState.value.isLoading) {
        val d = data
        val s = selections
        _uiState.value = buildUiState(d, s, isLoading)
    }

    private fun buildUiState(d: DashboardData, s: Selections, isLoading: Boolean): AnalyticsUiState {
        // Card 1 — training charts.
        val trainingRange = s.trainingRange
        val bars = DashboardAggregator.weeklyTrainingSeries(
            s.trainingMetric, d.workouts, d.sets, trainingRange, d.today, d.zone, d.firstDayOfWeek, d.includeWarmups,
        )

        // Card 2 — muscle distribution + period tiles.
        val distribution = MuscleStatsCalculator.distribution(d.muscleInputs, s.distributionRange, d.today, d.includeWarmups)
        val totals = DashboardAggregator.periodTotals(
            d.workouts, d.sets, ChartAggregator.window(s.distributionRange, d.today), d.zone, d.includeWarmups,
        )

        // Card 3 — week-by-week body view over all logged weeks.
        val weeklyCounts = MuscleStatsCalculator.setCountsPerMuscleGroup(
            d.muscleInputs, ChartRange.ALL_TIME, StatBucket.WEEK, d.firstDayOfWeek, d.today, d.includeWarmups,
        )
        val bodyWeeks = weeklyCounts.map { it.bucketStart }.distinct().sortedDescending()
        val bodyWeek = s.bodyWeek?.takeIf { it in bodyWeeks } ?: bodyWeeks.firstOrNull()
        val bodyWeekCounts = weeklyCounts.filter { it.bucketStart == bodyWeek }
        val bodyMax = bodyWeekCounts.maxOfOrNull { it.setCount } ?: 0

        // Card 4 — set counts per muscle group with the deselection filter.
        val bucketCounts = MuscleStatsCalculator.setCountsPerMuscleGroup(
            d.muscleInputs, s.setCountRange, s.setCountBucket, d.firstDayOfWeek, d.today, d.includeWarmups,
        )
        val activeCounts = bucketCounts.filter { it.group !in s.deselectedMuscles }
        val setCountBars = activeCounts
            .groupBy { it.bucketStart }.toSortedMap()
            .map { (bucketStart, rows) -> SetCountBar(bucketStart, rows.sumOf { it.setCount }) }
        val muscleTotals = bucketCounts
            .groupBy { it.group }
            .map { (group, rows) -> MuscleTotalRow(group, rows.sumOf { it.setCount }, group !in s.deselectedMuscles) }
            .sortedWith(compareByDescending<MuscleTotalRow> { it.setCount }.thenBy { it.group.name })
        val setCountMax = muscleTotals.filter { it.included }.maxOfOrNull { it.setCount } ?: 0

        // Card 7 — steps, sourced from Health Connect (Owner request, 2026-09-11). Sorted since
        // AggregationResultGroupedByPeriod's own ordering isn't documented as sorted.
        val stepsBars = d.stepsHistory.sortedBy { it.date }.map { DailyStepBar(it.date, it.steps) }

        return AnalyticsUiState(
            isLoading = isLoading,
            hasAnyWorkouts = d.workouts.isNotEmpty(),
            weightUnit = d.weightUnit,
            muscleDiagramVariant = d.muscleDiagramVariant,
            training = TrainingCardState(
                metric = s.trainingMetric,
                range = trainingRange,
                bars = bars,
                selectedBar = s.trainingSelectedBar?.takeIf { it in bars.indices },
            ),
            distribution = DistributionCardState(
                range = s.distributionRange,
                current = distribution.current.sortedByDescending { it.setCount },
                previous = distribution.previous?.sortedByDescending { it.setCount },
                totals = totals,
                // M20c: the muscle-balance wheel's 8 fixed axes, computed from the same
                // (unsorted -- order doesn't matter here) distribution the list above already has.
                balance = balanceAxes(distribution.current),
            ),
            body = BodyCardState(
                weeks = bodyWeeks,
                selectedWeek = bodyWeek,
                intensities = if (bodyMax == 0) emptyMap() else bodyWeekCounts.associate { it.group to it.setCount.toFloat() / bodyMax },
                counts = bodyWeekCounts.sortedByDescending { it.setCount }.map { MuscleTotalRow(it.group, it.setCount, true) },
            ),
            setCounts = SetCountCardState(
                range = s.setCountRange,
                bucket = s.setCountBucket,
                bars = setCountBars,
                muscles = muscleTotals,
                diagramIntensities = if (setCountMax == 0) emptyMap() else muscleTotals.filter { it.included }
                    .associate { it.group to it.setCount.toFloat() / setCountMax },
                selectedMuscles = muscleTotals.filter { it.included }.map { it.group }.toSet(),
            ),
            mainExercises = MainExercisesCardState(
                range = s.mainRange,
                rows = DashboardAggregator.mainExercises(
                    d.sets, ChartAggregator.window(s.mainRange, d.today), d.zone, d.includeWarmups,
                ),
            ),
            steps = StepsCardState(
                available = d.stepsAvailable,
                bars = stepsBars,
                selectedBar = s.stepsSelectedBar?.takeIf { it in stepsBars.indices },
            ),
        )
    }

    companion object {
        const val FOCUS_ARG = "focus"
    }
}

data class SetCountBar(val bucketStart: LocalDate, val setCount: Int)
data class MuscleTotalRow(val group: MuscleGroup, val setCount: Int, val included: Boolean)

data class TrainingCardState(
    val metric: TrainingMetric = TrainingMetric.VOLUME,
    val range: ChartRange = ChartRange.LAST_3_MONTHS,
    val bars: List<DashboardAggregator.WeeklyBar> = emptyList(),
    val selectedBar: Int? = null,
)

data class DistributionCardState(
    val range: ChartRange = ChartRange.LAST_3_MONTHS,
    val current: List<MuscleStatsCalculator.GroupShare> = emptyList(),
    val previous: List<MuscleStatsCalculator.GroupShare>? = null,
    val totals: DashboardAggregator.PeriodTotals = DashboardAggregator.PeriodTotals(0, 0, 0.0, 0),
    /** M20c (ADR-0009): always all 8 [BodyRegion]s, zero-filled — see [balanceAxes]. */
    val balance: List<RegionShare> = BodyRegion.entries.map { RegionShare(it, 0, 0) },
)

data class BodyCardState(
    val weeks: List<LocalDate> = emptyList(),
    val selectedWeek: LocalDate? = null,
    val intensities: Map<MuscleGroup, Float> = emptyMap(),
    val counts: List<MuscleTotalRow> = emptyList(),
)

data class SetCountCardState(
    val range: ChartRange = ChartRange.LAST_3_MONTHS,
    val bucket: StatBucket = StatBucket.WEEK,
    val bars: List<SetCountBar> = emptyList(),
    val muscles: List<MuscleTotalRow> = emptyList(),
    val diagramIntensities: Map<MuscleGroup, Float> = emptyMap(),
    val selectedMuscles: Set<MuscleGroup> = emptySet(),
)

data class MainExercisesCardState(
    val range: ChartRange = ChartRange.LAST_3_MONTHS,
    val rows: List<DashboardAggregator.ExerciseFrequency> = emptyList(),
)

data class DailyStepBar(val date: LocalDate, val steps: Long)

/**
 * M21. No range chips, unlike every other card here — Health Connect's own permission grant only
 * exposes the last 30 days of history (its own permission screen says so), so there's never a wider
 * window this card could actually show; a range selector implying otherwise would mislead.
 */
data class StepsCardState(
    val available: Boolean = false,
    val bars: List<DailyStepBar> = emptyList(),
    val selectedBar: Int? = null,
)

data class AnalyticsUiState(
    val isLoading: Boolean = true,
    val hasAnyWorkouts: Boolean = false,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val muscleDiagramVariant: MuscleDiagramVariant = MuscleDiagramVariant.MALE,
    val training: TrainingCardState = TrainingCardState(),
    val distribution: DistributionCardState = DistributionCardState(),
    val body: BodyCardState = BodyCardState(),
    val setCounts: SetCountCardState = SetCountCardState(),
    val mainExercises: MainExercisesCardState = MainExercisesCardState(),
    val steps: StepsCardState = StepsCardState(),
)
