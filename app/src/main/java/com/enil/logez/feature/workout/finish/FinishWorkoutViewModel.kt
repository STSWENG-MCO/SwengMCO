package com.enil.logez.feature.workout.finish

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.calc.ExerciseShape
import com.enil.logez.core.domain.calc.RoutineStructureDiffer
import com.enil.logez.core.domain.repository.RoutineRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Save Workout screen (title/date/duration) and the conditional "update this routine to
 * match?" prompt shown when a workout diverged from the routine it started from. Nothing here is
 * persisted until [save] runs — killing the app on this screen leaves the workout `IN_PROGRESS`
 * (recoverable), so this screen is pure in-memory draft state over a workout that is still live.
 */
@HiltViewModel
class FinishWorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val workoutFinisher: WorkoutFinisher,
    private val logger: AppLogger = AppLogger.NoOp,
) : ViewModel() {
    private val workoutId: String = checkNotNull(savedStateHandle[WORKOUT_ID_ARG])

    private val _uiState = MutableStateFlow(FinishWorkoutUiState())
    val uiState: StateFlow<FinishWorkoutUiState> = _uiState

    private val _saveState = MutableStateFlow<SaveState>(SaveState.Idle)
    val saveState: StateFlow<SaveState> = _saveState

    private var workout: WorkoutEntity? = null

    init {
        viewModelScope.launch {
            val w = workoutRepository.getById(workoutId)
            if (w == null) {
                // Nothing to save — the row is gone (discarded elsewhere, or the id is stale).
                // Surfacing this as an error beats leaving isLoading true forever, which rendered
                // an empty screen with no spinner, no message and no way forward but Back.
                _uiState.update { it.copy(isLoading = false, isMissing = true) }
                return@launch
            }
            workout = w
            val sets = workoutRepository.getSetsWithExerciseForWorkout(workoutId)
            _uiState.value = FinishWorkoutUiState(
                isLoading = false,
                title = w.title,
                notes = w.notes.orEmpty(),
                startedAtMillis = w.startedAt,
                durationSeconds = w.durationSeconds,
                isRoutineBased = w.routineId != null,
                updateRoutineValues = true, // §8.10: "default ON, per-save"
                incompleteSetCount = sets.count { !it.set.isCompleted },
                completedSetCount = sets.count { it.set.isCompleted },
            )
        }
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value) }
    fun updateStartedAt(millis: Long) = _uiState.update { it.copy(startedAtMillis = millis) }

    /**
     * No UI caller any more — the Save screen shows the session's elapsed time read-only rather
     * than letting it be typed. Kept because `FinishWorkoutViewModelTest` drives the "edits are
     * held in memory until save" case through it.
     */

    fun setUpdateRoutineValues(enabled: Boolean) = _uiState.update { it.copy(updateRoutineValues = enabled) }

    /**
     * Step 1 of saving: decide whether §5.1.8(b)'s prompt is owed. Returns true when the caller
     * should show it and then call [save] with an explicit choice; false when it can save straight
     * away. Kept separate from [save] so the prompt is a UI decision, not a hidden side effect.
     */
    suspend fun needsStructurePrompt(): Boolean {
        val w = workout ?: return false
        val routineId = w.routineId ?: return false
        val routineShape = routineRepository.getExercisesForRoutine(routineId)
            .sortedBy { it.orderIndex }
            .map { ExerciseShape(it.exerciseId, routineRepository.getSetsForRoutineExercise(it.id).size) }
        // Counts COMPLETED sets only, and drops exercises left with none: the save transaction
        // purges uncompleted sets first, so comparing raw counts would prompt about a "change"
        // that is about to be thrown away — e.g. an added-but-never-performed set.
        val loggedShape = workoutRepository.getExercisesForWorkout(w.id)
            .sortedBy { it.orderIndex }
            .map { we -> ExerciseShape(we.exerciseId, workoutRepository.getSetsForWorkoutExercise(we.id).count { it.isCompleted }) }
            .filter { it.setCount > 0 }
        return RoutineStructureDiffer.isStructurallyChanged(routineShape, loggedShape)
    }

    /**
     * Runs §5.1.8's save transaction. [structureChoice] is null when no prompt was owed.
     *
     * Deliberately launched on [viewModelScope] rather than awaited from the composition: the
     * screen's scope dies on Activity recreation (rotation, "don't keep activities", a locale
     * change), which would cancel the save part-way. The ViewModel outlives that on its
     * NavBackStackEntry, and [SaveState] rides along with it so a recreated screen sees the save
     * still running instead of an enabled Save button inviting a second run.
     */
    fun save(structureChoice: RoutineStructureChoice?) {
        val w = workout
        if (w == null) {
            _saveState.value = SaveState.Failed
            return
        }
        // Blocks re-entry while genuinely in flight or already resolved (Saved/Discarded — the
        // screen should already be navigating away by then); Failed is the one non-Idle state
        // that must NOT block, because it's retryable and the screen's Save button is enabled for
        // it (`isSaving = saveState is Saving`). Gating on "!= Idle" made Failed a dead window
        // instead: clearSaveError() only fires after the error snackbar finishes showing (~4s),
        // during which the button looked live but every tap here was silently swallowed —
        // including the user's answer to the Update-Routine prompt.
        val current = _saveState.value
        if (current != SaveState.Idle && current != SaveState.Failed) return
        _saveState.value = SaveState.Saving
        val state = _uiState.value
        viewModelScope.launch {
            _saveState.value = runCatching {
                workoutFinisher.finish(
                    workout = w,
                    title = state.title,
                    notes = state.notes,
                    startedAt = state.startedAtMillis,
                    durationSeconds = state.durationSeconds,
                    updateRoutineValues = state.updateRoutineValues,
                    structureChoice = structureChoice,
                )
            }.fold(
                onSuccess = { SaveState.Saved(it) },
                // The whole transaction rolled back, so the workout is still IN_PROGRESS and
                // retrying is safe — hence Idle-able rather than terminal.
                onFailure = {
                    logger.e(TAG, "save: workoutFinisher.finish failed for workout $workoutId", it)
                    SaveState.Failed
                },
            )
        }
    }

    /** Dismisses a failure so the Save button becomes live again. */
    fun clearSaveError() {
        if (_saveState.value == SaveState.Failed) _saveState.value = SaveState.Idle
    }

    /**
     * §5.1.8's "no completed sets → offer Discard" branch. The dialog offered this from the start
     * but nothing ever deleted the row, so the user returned to a workout they believed was gone —
     * still IN_PROGRESS, still holding the mini-bar, still blocking a new workout from starting.
     */
    fun discard() {
        val current = _saveState.value
        if (current != SaveState.Idle && current != SaveState.Failed) return // see save()'s guard
        _saveState.value = SaveState.Saving
        viewModelScope.launch {
            _saveState.value = runCatching { workoutRepository.deleteById(workoutId) }
                .fold(
                    onSuccess = { SaveState.Discarded },
                    onFailure = {
                        logger.e(TAG, "discard: failed to delete workout $workoutId", it)
                        SaveState.Failed
                    },
                )
        }
    }

    companion object {
        const val WORKOUT_ID_ARG = "workoutId"
        private const val TAG = "FinishWorkoutViewModel"
    }
}

/** Where the save stands, held here so it survives the Activity recreation that kills the screen. */
sealed interface SaveState {
    data object Idle : SaveState
    data object Saving : SaveState
    data class Saved(val result: FinishResult) : SaveState
    data object Discarded : SaveState
    data object Failed : SaveState
}

data class FinishWorkoutUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val notes: String = "",
    val startedAtMillis: Long = 0L,
    val durationSeconds: Int = 0,
    val isRoutineBased: Boolean = false,
    val updateRoutineValues: Boolean = true,
    /** §5.1.8: "Finish with unfinished sets -> dialog 'You have N incomplete sets — they will be discarded'". */
    val incompleteSetCount: Int = 0,
    val completedSetCount: Int = 0,
    /** The workout row could not be loaded — nothing to save; the screen shows an error, not a blank. */
    val isMissing: Boolean = false,
)
