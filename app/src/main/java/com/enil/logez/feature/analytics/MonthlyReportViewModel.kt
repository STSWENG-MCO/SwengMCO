package com.enil.logez.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.calc.DashboardAggregator
import com.enil.logez.core.domain.calc.MuscleStatsCalculator
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.2 card 6 — the Monthly Report for any completed calendar month. An archive
 * is free once the aggregates exist, so the picker spans first-workout-month through the current
 * month. Same single-snapshot
 * refresh discipline as [AnalyticsViewModel], plus a cancel-and-restart rebuild job: unlike that
 * class's fully-synchronous rebuild, this one suspends mid-rebuild (the PR read), so without the
 * guard a stale in-flight rebuild could publish after — and silently revert — a newer selection.
 */
@HiltViewModel
class MonthlyReportViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val personalRecordsRepository: PersonalRecordsRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    enum class ComparisonMetric { WORKOUTS, DURATION, VOLUME, SETS }

    private data class ReportData(
        val workouts: List<DashboardAggregator.WorkoutInfo> = emptyList(),
        val sets: List<DashboardAggregator.SetWithExercise> = emptyList(),
        val muscleInputs: List<MuscleStatsCalculator.MuscleSetInput> = emptyList(),
        val exerciseNameById: Map<String, String> = emptyMap(),
        val includeWarmups: Boolean = false,
        val weightUnit: WeightUnit = WeightUnit.KG,
        val zone: ZoneId = ZoneId.systemDefault(),
        val currentMonth: YearMonth = YearMonth.of(1970, 1),
    )

    private var data = ReportData()
    private var selectedMonth: YearMonth? = null
    private var comparisonMetric = ComparisonMetric.WORKOUTS
    private var rebuildJob: Job? = null

    private val _uiState = MutableStateFlow(MonthlyReportUiState())
    val uiState: StateFlow<MonthlyReportUiState> = _uiState.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val zone = ZoneId.systemDefault()
            val workoutEntities = workoutRepository.getCompletedWorkouts()
            val joined = workoutRepository.getSetsWithExerciseForCompletedWorkouts()
            val exerciseById = joined.map { it.exerciseId }.distinct()
                .mapNotNull { id -> exerciseRepository.getById(id)?.let { id to it } }.toMap()
            data = ReportData(
                workouts = workoutEntities.map { DashboardAggregator.WorkoutInfo(it.id, it.startedAt, it.durationSeconds) },
                sets = joined.mapNotNull { row ->
                    val exercise = exerciseById[row.exerciseId] ?: return@mapNotNull null
                    DashboardAggregator.SetWithExercise(row.exerciseId, exercise.name, exercise.exerciseType, exercise.isBodyweightVolumeEligible, row.set)
                },
                muscleInputs = joined.mapNotNull { row ->
                    val exercise = exerciseById[row.exerciseId] ?: return@mapNotNull null
                    MuscleStatsCalculator.MuscleSetInput(
                        exercise.primaryMuscleGroup,
                        DashboardAggregator.localDate(row.set.workoutStartedAt, zone),
                        row.set.setType,
                        row.set.isCompleted,
                    )
                },
                exerciseNameById = exerciseById.mapValues { it.value.name },
                includeWarmups = settings.includeWarmupsInStats,
                weightUnit = settings.weightUnit,
                zone = zone,
                currentMonth = YearMonth.from(Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone).toLocalDate()),
            )
            rebuildAsync()
        }
    }

    fun selectMonth(month: YearMonth) { selectedMonth = month; rebuildAsync() }
    fun selectComparisonMetric(metric: ComparisonMetric) { comparisonMetric = metric; rebuildAsync() }

    /**
     * Month arrows step from the ViewModel's own resolved month, never from the published UI
     * state — the publish lags the tap by a suspend read, so UI-state-derived stepping collapses
     * rapid taps into a single step. Steps outside the picker's span are clamped to no-ops.
     */
    fun stepMonth(delta: Long) {
        val d = data
        val months = availableMonths(d)
        if (months.isEmpty()) return
        val target = resolveMonth(d, months).plusMonths(delta)
        if (target in months) {
            selectedMonth = target
            rebuildAsync()
        }
    }

    /** Defaults to the last completed month; an archive month stays selected once picked. */
    private fun resolveMonth(d: ReportData, months: List<YearMonth>): YearMonth =
        selectedMonth?.takeIf { it in months }
            ?: d.currentMonth.minusMonths(1).takeIf { it in months }
            ?: months.lastOrNull()
            ?: d.currentMonth

    /**
     * The PR list needs a suspend read (records by achievedAt window), so rebuilds run in a
     * coroutine — cancel-and-restart, because two in-flight rebuilds complete in whatever order
     * Room's query pool finishes them, and a stale one publishing last would silently revert a
     * newer month/metric selection. All selections are captured before the suspend point so a
     * single publish can never mix generations.
     */
    private fun rebuildAsync() {
        rebuildJob?.cancel()
        rebuildJob = viewModelScope.launch {
            val d = data
            val metric = comparisonMetric
            val months = availableMonths(d)
            val month = resolveMonth(d, months)
            val window = DashboardAggregator.monthWindow(month)
            val totals = DashboardAggregator.periodTotals(d.workouts, d.sets, window, d.zone, d.includeWarmups)

            val comparisonMonths = (5 downTo 0).map { month.minusMonths(it.toLong()) }
            val comparison = DashboardAggregator.monthlyTotals(d.workouts, d.sets, comparisonMonths, d.zone, d.includeWarmups)

            val fromMillis = window.start.atStartOfDay(d.zone).toInstant().toEpochMilli()
            val untilMillis = window.endInclusive.plusDays(1).atStartOfDay(d.zone).toInstant().toEpochMilli()
            val prRows = personalRecordsRepository.getAchievedBetween(fromMillis, untilMillis)
                .sortedWith(compareBy({ d.exerciseNameById[it.exerciseId] ?: "" }, { it.prType }))
                .map { MonthPrRow(d.exerciseNameById[it.exerciseId] ?: "", it.prType, it.value, it.achievedAt) }

            val previousWindow = DashboardAggregator.monthWindow(month.minusMonths(1))
            val distribution = MuscleStatsCalculator.distributionInWindows(d.muscleInputs, window, previousWindow, d.includeWarmups)

            _uiState.value = MonthlyReportUiState(
                isLoading = false,
                months = months,
                month = month,
                comparisonMetric = metric,
                comparison = comparison,
                totals = totals,
                personalRecords = prRows,
                workoutDates = d.workouts
                    .map { DashboardAggregator.localDate(it.startedAt, d.zone) }
                    .filter { it in window }
                    .toSet(),
                distributionCurrent = distribution.current.sortedByDescending { it.setCount },
                distributionPrevious = distribution.previous?.sortedByDescending { it.setCount },
                topExercises = DashboardAggregator.mainExercises(d.sets, window, d.zone, d.includeWarmups).take(5),
                weightUnit = d.weightUnit,
            )
        }
    }

    private fun availableMonths(d: ReportData): List<YearMonth> {
        val first = d.workouts.minOfOrNull { it.startedAt }?.let { YearMonth.from(DashboardAggregator.localDate(it, d.zone)) }
            ?: return emptyList()
        val months = mutableListOf<YearMonth>()
        var m = first
        while (m <= d.currentMonth) { months.add(m); m = m.plusMonths(1) }
        return months
    }
}

data class MonthPrRow(val exerciseName: String, val prType: PrType, val value: Double, val achievedAt: Long)

data class MonthlyReportUiState(
    val isLoading: Boolean = true,
    val months: List<YearMonth> = emptyList(),
    val month: YearMonth = YearMonth.of(1970, 1),
    val comparisonMetric: MonthlyReportViewModel.ComparisonMetric = MonthlyReportViewModel.ComparisonMetric.WORKOUTS,
    val comparison: List<DashboardAggregator.MonthTotals> = emptyList(),
    val totals: DashboardAggregator.PeriodTotals = DashboardAggregator.PeriodTotals(0, 0, 0.0, 0),
    val personalRecords: List<MonthPrRow> = emptyList(),
    val workoutDates: Set<LocalDate> = emptySet(),
    val distributionCurrent: List<MuscleStatsCalculator.GroupShare> = emptyList(),
    val distributionPrevious: List<MuscleStatsCalculator.GroupShare>? = null,
    val topExercises: List<DashboardAggregator.ExerciseFrequency> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
)
