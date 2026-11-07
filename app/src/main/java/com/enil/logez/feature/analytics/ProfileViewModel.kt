package com.enil.logez.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.calc.DashboardAggregator
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.calc.MuscleStatsCalculator
import com.enil.logez.core.domain.calc.StreakCalculator
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.DailyWellnessTotal
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WellnessRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.HealthMetricsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.2 "Profile tab" — headline stats (lifetime workout count + weekly streak),
 * the last-7-days strip with its mini muscle heat-map, and the quick chart card's four weekly
 * series (default 3m, §5.2 region 4). Fresh zone/today per refresh; single-snapshot publish.
 *
 * The temporary RPE toggle (2026-08-24) moved to the real Settings screen in M16. The temporary
 * DEBUG-only "Seed/Clear Demo Data" rows (2026-08-26) were removed from this screen entirely on
 * 2026-09-04 (Owner directive) — [com.enil.logez.core.data.seed.DemoDataSeeder] itself is untouched
 * and still Hilt-injectable/tested on its own, just no longer wired to any UI.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    val healthMetricsSource: HealthMetricsSource,
    private val wellnessRepository: WellnessRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    /** The screen's single load trigger — RefreshOnResume calls this on every ON_RESUME (no init load). */
    fun refresh() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val zone = ZoneId.systemDefault()
            val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone).toLocalDate()
            val workoutEntities = workoutRepository.getCompletedWorkouts()
            val workouts = workoutEntities.map { DashboardAggregator.WorkoutInfo(it.id, it.startedAt, it.durationSeconds) }
            val workoutDates = workouts.map { DashboardAggregator.localDate(it.startedAt, zone) }
            val joined = workoutRepository.getSetsWithExerciseForCompletedWorkouts()
            val exerciseById = joined.map { it.exerciseId }.distinct()
                .mapNotNull { id -> exerciseRepository.getById(id)?.let { id to it } }.toMap()
            val sets = joined.mapNotNull { row ->
                val exercise = exerciseById[row.exerciseId] ?: return@mapNotNull null
                DashboardAggregator.SetWithExercise(row.exerciseId, exercise.name, exercise.exerciseType, exercise.isBodyweightVolumeEligible, row.set)
            }

            val last7Window = today.minusDays(6)..today
            val last7Count = workouts.count { DashboardAggregator.localDate(it.startedAt, zone) in last7Window }
            val last7Muscle = joined.mapNotNull { row ->
                val exercise = exerciseById[row.exerciseId] ?: return@mapNotNull null
                MuscleStatsCalculator.MuscleSetInput(
                    exercise.primaryMuscleGroup,
                    DashboardAggregator.localDate(row.set.workoutStartedAt, zone),
                    row.set.setType,
                    row.set.isCompleted,
                )
            }
            val heatCounts = MuscleStatsCalculator
                .distributionInWindows(last7Muscle, last7Window, previousWindow = null, settings.includeWarmupsInStats)
                .current
            val heatMax = heatCounts.maxOfOrNull { it.setCount } ?: 0

            // M21e: read-only, graceful degrade -- a device with no Health Connect (or a user who
            // hasn't granted the permission yet) simply doesn't get this section, never a nag.
            // Each type is honoured on its own (2026-09-25): a user who ticked only steps on Health
            // Connect's screen used to see nothing, because this required all three grants.
            val wellnessAvailability = healthMetricsSource.availability()
            val wellnessGranted = healthMetricsSource.grantedTypes()
            val stepsGranted = HealthDataType.STEPS in wellnessGranted
            var todaySteps: Long? = null
            var todayCalories: Double? = null
            if (stepsGranted || HealthDataType.CALORIES in wellnessGranted) {
                val totals = healthMetricsSource.readTodayTotals()
                if (stepsGranted) todaySteps = totals.steps
                todayCalories = totals.caloriesBurned
                // The widget's steps line reads this cache; a calories-only grant has no steps to
                // cache, and writing a 0 would show the widget a step count the user never shared.
                if (stepsGranted) {
                    wellnessRepository.upsert(
                        DailyWellnessTotal(
                            date = today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            steps = totals.steps,
                            caloriesBurned = totals.caloriesBurned,
                            updatedAt = clock.now().toEpochMilliseconds(),
                        ),
                    )
                }
            }

            _uiState.value = ProfileUiState(
                isLoading = false,
                workoutCount = workouts.size,
                streakWeeks = StreakCalculator.weeklyStreak(workoutDates, today, settings.firstDayOfWeek),
                streakDays = StreakCalculator.dailyStreak(workoutDates, today),
                last7Count = last7Count,
                last7Heat = if (heatMax == 0) emptyMap() else heatCounts.associate { it.group to it.setCount.toFloat() / heatMax },
                quickCharts = TrainingMetric.entries.associateWith { metric ->
                    DashboardAggregator.weeklyTrainingSeries(
                        metric, workouts, sets, ChartRange.LAST_3_MONTHS, today, zone,
                        settings.firstDayOfWeek, settings.includeWarmupsInStats,
                    )
                },
                weightUnit = settings.weightUnit,
                muscleDiagramVariant = settings.muscleDiagramVariant,
                wellnessAvailability = wellnessAvailability,
                wellnessGranted = wellnessGranted,
                todaySteps = todaySteps,
                todayCaloriesBurned = todayCalories,
            )
        }
    }

    /** Called after the Compose permission launcher resolves — re-runs the one load path rather than duplicating it. */
    fun onWellnessPermissionResult(anyGranted: Boolean) {
        if (anyGranted) {
            healthMetricsSource.onPermissionsRegranted()
            refresh()
        }
    }
}

data class ProfileUiState(
    val isLoading: Boolean = true,
    val workoutCount: Int = 0,
    val streakWeeks: Int = 0,
    val streakDays: Int = 0,
    val last7Count: Int = 0,
    val last7Heat: Map<MuscleGroup, Float> = emptyMap(),
    val quickCharts: Map<TrainingMetric, List<DashboardAggregator.WeeklyBar>> = emptyMap(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val muscleDiagramVariant: MuscleDiagramVariant = MuscleDiagramVariant.MALE,
    /** M21e: whether Health Connect is usable, needs installing/updating, or is unsupported here -- picks which card shows. */
    val wellnessAvailability: HealthConnectAvailability = HealthConnectAvailability.Unavailable,
    /** The Health Connect types the user granted. Empty shows the Connect card; each stat shows only for its own type. */
    val wellnessGranted: Set<HealthDataType> = emptySet(),
    val todaySteps: Long? = null,
    val todayCaloriesBurned: Double? = null,
)
