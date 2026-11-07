package com.enil.logez.feature.exercises

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * PHASE2_PLAN.md §5.2 "Exercise Library" — search + equipment/muscle filters + the three-tier
 * sort (recently-used, then never-used custom, then the rest alphabetically). Filtering/sorting
 * runs in Kotlin over the full active list (~400 rows) rather than in SQL — small enough dataset
 * that this stays simple and correct instead of fighting the JSON-encoded secondaryMuscleGroups
 * column with a LIKE match.
 */
@HiltViewModel
class ExerciseLibraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val clock: Clock,
) : ViewModel() {
    // §5.2 Analytics dashboard: a muscle row taps through to the library pre-filtered to that
    // muscle. The arg only seeds the filter — the user can clear or change it like any other.
    private val filters = MutableStateFlow(
        LibraryFilters(muscleFilter = savedStateHandle.get<String>("muscle")?.let { runCatching { MuscleGroup.valueOf(it) }.getOrNull() }),
    )
    private val recentUsage = MutableStateFlow<Map<String, Long>>(emptyMap())

    val uiState: StateFlow<ExerciseLibraryUiState> = combine(
        exerciseRepository.observeActive(),
        filters,
        recentUsage,
    ) { exercises, f, usage ->
        ExerciseLibraryUiState(
            isLoading = false,
            searchQuery = f.searchQuery,
            equipmentFilter = f.equipmentFilter,
            muscleFilter = f.muscleFilter,
            exercises = applyFiltersAndSort(exercises, f, usage),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExerciseLibraryUiState())

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

    /** §5.2: any exercise can be duplicated into a custom copy with no history attached. */
    fun duplicateExercise(exercise: Exercise) {
        viewModelScope.launch {
            val now = clock.now().toEpochMilliseconds()
            exerciseRepository.upsertCustom(
                exercise.copy(
                    id = UUID.randomUUID().toString(),
                    isCustom = true,
                    isBodyweightVolumeEligible = false,
                    isDeleted = false,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /** §5.2, widened 2026-08-26 to any exercise (seed or custom) — soft delete keeps history queryable. */
    fun deleteExercise(exercise: Exercise) {
        viewModelScope.launch { exerciseRepository.softDelete(exercise.id) }
    }
}

internal data class LibraryFilters(
    val searchQuery: String = "",
    val equipmentFilter: Equipment? = null,
    val muscleFilter: MuscleGroup? = null,
)

data class ExerciseLibraryUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val equipmentFilter: Equipment? = null,
    val muscleFilter: MuscleGroup? = null,
    val exercises: List<Exercise> = emptyList(),
)

/** §5.2 sort: recently-used first (most recent first), then never-used custom, then the rest — alphabetical within each tier. */
internal fun applyFiltersAndSort(
    exercises: List<Exercise>,
    filters: LibraryFilters,
    recentUsage: Map<String, Long>,
): List<Exercise> {
    val query = filters.searchQuery.trim()
    val filtered = exercises.filter { e ->
        (query.isBlank() || e.name.contains(query, ignoreCase = true)) &&
            (filters.equipmentFilter == null || e.equipment == filters.equipmentFilter) &&
            (filters.muscleFilter == null || e.primaryMuscleGroup == filters.muscleFilter || filters.muscleFilter in e.secondaryMuscleGroups)
    }
    return filtered.sortedWith(
        compareBy(
            { tier(it, recentUsage) },
            { if (tier(it, recentUsage) == 0) -(recentUsage[it.id] ?: 0L) else 0L },
            { it.name },
        ),
    )
}

private fun tier(e: Exercise, recentUsage: Map<String, Long>): Int = when {
    recentUsage.containsKey(e.id) -> 0
    e.isCustom -> 1
    else -> 2
}
