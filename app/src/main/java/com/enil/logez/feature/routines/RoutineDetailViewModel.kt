package com.enil.logez.feature.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.RoutineRepository
import com.enil.logez.feature.workout.InProgressWorkout
import com.enil.logez.feature.workout.InProgressWorkoutResolver
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.session.WorkoutSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** PHASE2_PLAN.md §5.1.1 "Tap routine card body → read-only routine detail". */
@HiltViewModel
class RoutineDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    routineRepository: RoutineRepository,
    exerciseRepository: ExerciseRepository,
    private val workoutStarter: WorkoutStarter,
    private val sessionController: WorkoutSessionController,
    private val inProgressWorkoutResolver: InProgressWorkoutResolver,
    private val sessionDiscarder: SessionDiscarder,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    /** Which resume path this screen's conflict dialog should take -- see [InProgressWorkoutResolver]. */
    suspend fun inProgressWorkout(): InProgressWorkout? = inProgressWorkoutResolver.resolve()

    private val routineId: String = checkNotNull(savedStateHandle[ROUTINE_ID_ARG])

    suspend fun startRoutine(): StartResult {
        val result = workoutStarter.startFromRoutineOrConflict(routineId)
        if (result is StartResult.Started) sessionController.startSession(result.workoutId)
        return result
    }

    suspend fun discardInProgressAndStart(): String {
        sessionDiscarder.discardInProgress()
        val id = workoutStarter.startFromRoutine(routineId)
        sessionController.startSession(id)
        return id
    }

    val uiState: StateFlow<RoutineDetailUiState> = combine(
        routineRepository.observeRoutineById(routineId),
        routineRepository.observeExercisesForRoutine(routineId),
        combine(exerciseRepository.observeActive(), settingsRepository.settings) { exercises, settings -> exercises to settings.weightUnit },
    ) { routine, routineExercises, exercisesAndUnit -> Triple(routine, routineExercises, exercisesAndUnit) }
        .map { (routine, routineExercises, exercisesAndUnit) ->
            val (exercises, weightUnit) = exercisesAndUnit
            val exerciseById = exercises.associateBy { it.id }
            val rows = routineExercises.sortedBy { it.orderIndex }.map { re ->
                RoutineDetailExerciseRow(
                    routineExercise = re,
                    exercise = exerciseById[re.exerciseId],
                    sets = routineRepository.getSetsForRoutineExercise(re.id).sortedBy { it.orderIndex },
                )
            }
            RoutineDetailUiState(isLoading = false, routine = routine, exercises = rows, weightUnit = weightUnit)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RoutineDetailUiState())

    companion object {
        const val ROUTINE_ID_ARG = "routineId"
    }
}

data class RoutineDetailUiState(
    val isLoading: Boolean = true,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val routine: RoutineEntity? = null,
    val exercises: List<RoutineDetailExerciseRow> = emptyList(),
)

data class RoutineDetailExerciseRow(
    val routineExercise: RoutineExerciseEntity,
    val exercise: Exercise?,
    val sets: List<RoutineSetEntity>,
)
