package com.enil.logez.feature.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.GoalDefinitionEntity
import com.enil.logez.core.domain.calc.DashboardAggregator
import com.enil.logez.core.domain.calc.GoalProgressCalculator
import com.enil.logez.core.domain.model.GoalMetric
import com.enil.logez.core.domain.model.GoalPeriod
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.GoalRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * M8d — the Goals card on the Workout tab. Same shape as [com.enil.logez.feature.analytics.AnalyticsViewModel]:
 * one suspend bulk load, driven by `RefreshOnResume` (no init load, per the M6b single-load-trigger
 * lesson), that gathers exactly the same [DashboardAggregator.WorkoutInfo]/[DashboardAggregator.SetWithExercise]
 * rows the Analytics dashboard does, then hands each goal + that data to [GoalProgressCalculator].
 */
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val goalRepository: GoalRepository,
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GoalsUiState())
    val uiState: StateFlow<GoalsUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    /**
     * The screen's single load trigger — called from `RefreshOnResume`, and again after
     * create/delete. Cancel-and-restart (MonthlyReportViewModel's M6b fix, same shape here):
     * without it, a create/delete's own trailing refresh racing an unrelated RefreshOnResume call
     * could let the older of the two finish last and silently revert the newer one's result.
     */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val zone = ZoneId.systemDefault()
            val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone).toLocalDate()

            val goals = goalRepository.observeAll().first()
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
            val workouts = workoutEntities.map { DashboardAggregator.WorkoutInfo(it.id, it.startedAt, it.durationSeconds) }

            val rows = goals.map { goal ->
                val progress = GoalProgressCalculator.progress(
                    goal, workouts, sets, today, zone, settings.firstDayOfWeek, settings.includeWarmupsInStats,
                )
                GoalRow(goal, progress)
            }
            _uiState.value = GoalsUiState(isLoading = false, goals = rows, weightUnit = settings.weightUnit)
        }
    }

    fun createGoal(metric: GoalMetric, period: GoalPeriod, targetValue: Double) {
        if (targetValue <= 0.0) return
        viewModelScope.launch {
            val now = clock.now().toEpochMilliseconds()
            goalRepository.upsert(
                GoalDefinitionEntity(
                    id = UUID.randomUUID().toString(),
                    metric = metric,
                    period = period,
                    targetValue = targetValue,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            refresh()
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch {
            goalRepository.deleteById(id)
            refresh()
        }
    }
}

data class GoalRow(val goal: GoalDefinitionEntity, val progress: GoalProgressCalculator.Progress)

data class GoalsUiState(
    val isLoading: Boolean = true,
    val goals: List<GoalRow> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
)
