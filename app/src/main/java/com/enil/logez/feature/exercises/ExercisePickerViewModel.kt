package com.enil.logez.feature.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.1.9 "Exercise picker sheet" — the Exercise Library (§5.2) rendered in
 * selection mode. Reuses [applyFiltersAndSort]/[LibraryFilters] from the Library verbatim (one
 * implementation, two modes, per the plan's own instruction), only adding a multi-select
 * accumulator on top for the Routine Builder / Live Logger's "+ Add Exercise" flow. Replace mode
 * (single-select) doesn't use [selectedExercises] at all — the caller reacts to a row tap directly.
 */
@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {
    private val filters = MutableStateFlow(LibraryFilters())
    private val recentUsage = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val selected = MutableStateFlow<List<Exercise>>(emptyList())

    val uiState: StateFlow<ExercisePickerUiState> = combine(
        exerciseRepository.observeActive(),
        filters,
        recentUsage,
        selected,
    ) { exercises, f, usage, selectedExercises ->
        ExercisePickerUiState(
            isLoading = false,
            searchQuery = f.searchQuery,
            equipmentFilter = f.equipmentFilter,
            muscleFilter = f.muscleFilter,
            exercises = applyFiltersAndSort(exercises, f, usage),
            selectedIds = selectedExercises.map { it.id }.toSet(),
            selectedCount = selectedExercises.size,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ExercisePickerUiState())

    init {
        viewModelScope.launch {
            recentUsage.value = workoutRepository.getRecentUsageTimestamps()
        }
    }

    fun onSearchQueryChange(query: String) {
        filters.update { it.copy(searchQuery = query) }
    }

    fun onEquipmentFilterChange(equipment: Equipment?) {
        filters.update { it.copy(equipmentFilter = equipment) }
    }

    fun onMuscleFilterChange(muscle: MuscleGroup?) {
        filters.update { it.copy(muscleFilter = muscle) }
    }

    /** Add mode: tap toggles selection; commit order is preserved (§5.1.9). */
    fun toggleSelected(exercise: Exercise) {
        selected.update { current ->
            if (current.any { it.id == exercise.id }) current.filterNot { it.id == exercise.id } else current + exercise
        }
    }

    fun selectedExercises(): List<Exercise> = selected.value

    /** Called when the sheet closes (commit or cancel) so the next open starts fresh. */
    fun onSheetClosed() {
        selected.value = emptyList()
        filters.value = LibraryFilters()
    }
}

data class ExercisePickerUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val equipmentFilter: Equipment? = null,
    val muscleFilter: MuscleGroup? = null,
    val exercises: List<Exercise> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val selectedCount: Int = 0,
)
