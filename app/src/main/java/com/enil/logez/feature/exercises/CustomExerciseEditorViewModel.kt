package com.enil.logez.feature.exercises

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.media.ExerciseMediaStore
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.MuscleHead
import com.enil.logez.core.domain.model.availableHeads
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.enil.logez.core.data.media.MediaFileCleaner

/**
 * PHASE2_PLAN.md §5.2 "Custom exercise creation" / "Edit". [exerciseId] absent (or the value is
 * null) = create mode; present = edit mode, where [ExerciseType] becomes immutable — "greyed with
 * a 'Type can't be changed' hint" (§5.2, library-analytics.md §1).
 *
 * Edit mode works on any exercise, seed or custom (Owner directive 2026-08-26). `instructions` is
 * a real editable field (any exercise's how-to can be added/changed from the editor); this screen
 * still has no field for `isBodyweightVolumeEligible`, so [save] must carry that one forward from
 * the loaded row rather than the old create-mode default it used to hardcode — a seeded bodyweight
 * movement (Pull-Up, Dip, ...) saving with it silently reset to `false` would corrupt that
 * exercise's volume math on every workout logged against it from then on. [save] always writes
 * `isCustom = true`, regardless of what was loaded — this is what permanently exempts an edited
 * seed row from a future seed-file sync (see `ExerciseDao.kt`'s `updateSeedFields`/
 * `pruneRetiredSeeds` KDoc), so an edit (instructions included) is never silently reverted later.
 */
@HiltViewModel
class CustomExerciseEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val mediaStore: ExerciseMediaStore,
    private val clock: Clock,
    private val mediaFileCleaner: MediaFileCleaner = MediaFileCleaner.NoOp,
) : ViewModel() {
    private val editingId: String? = savedStateHandle.get<String>(EXERCISE_ID_ARG)
    val isEditMode: Boolean = editingId != null

    private var loadedCreatedAt: Long = clock.now().toEpochMilliseconds()
    private var loadedIsBodyweightVolumeEligible: Boolean = false
    /** The image the exercise had when the editor opened, so a replaced one can be deleted on save. */
    private var loadedMediaPath: String? = null

    private val _uiState = MutableStateFlow(
        CustomExerciseEditorUiState(
            isEditMode = isEditMode,
            isLoading = isEditMode,
            name = savedStateHandle.get<String>(PREFILL_NAME_ARG).orEmpty(),
        ),
    )
    val uiState: StateFlow<CustomExerciseEditorUiState> = _uiState.asStateFlow()

    init {
        val id = editingId
        if (id != null) {
            viewModelScope.launch {
                val existing = exerciseRepository.getById(id)
                if (existing != null) {
                    loadedCreatedAt = existing.createdAt
                    loadedIsBodyweightVolumeEligible = existing.isBodyweightVolumeEligible
                    loadedMediaPath = existing.mediaPath
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            name = existing.name,
                            equipment = existing.equipment,
                            primaryMuscleGroup = existing.primaryMuscleGroup,
                            secondaryMuscleGroups = existing.secondaryMuscleGroups.toSet(),
                            exerciseType = existing.exerciseType,
                            mediaPath = existing.mediaPath,
                            muscleHeads = existing.muscleHeads.toSet(),
                            instructions = existing.instructions,
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, loadError = true) }
                }
            }
        }
    }

    fun onNameChange(name: String) = _uiState.update { it.copy(name = name, nameError = false) }

    fun onEquipmentChange(equipment: Equipment) = _uiState.update { it.copy(equipment = equipment) }

    /**
     * Changing the primary group clears any picked heads — a head belongs to exactly one group,
     * and the new group may not even have any (or the same ones). Re-selecting the *same* group
     * the dropdown already shows (the menu's `onSelect` fires unconditionally on every tap, with
     * no equality check) must NOT clear it — nothing actually changed, so a stray re-tap on an
     * already-selected item shouldn't silently wipe heads the user already picked.
     */
    fun onPrimaryMuscleChange(muscle: MuscleGroup) = _uiState.update {
        if (muscle == it.primaryMuscleGroup) return@update it
        it.copy(primaryMuscleGroup = muscle, secondaryMuscleGroups = it.secondaryMuscleGroups - muscle, muscleHeads = emptySet())
    }

    /** A checklist, not a single pick (Owner feedback) — an exercise can work more than one head of the same group. */
    fun onMuscleHeadToggle(head: MuscleHead) = _uiState.update {
        it.copy(muscleHeads = if (head in it.muscleHeads) it.muscleHeads - head else it.muscleHeads + head)
    }

    fun onSecondaryMuscleToggle(muscle: MuscleGroup) = _uiState.update {
        if (muscle == it.primaryMuscleGroup) return@update it
        val updated = if (muscle in it.secondaryMuscleGroups) it.secondaryMuscleGroups - muscle else it.secondaryMuscleGroups + muscle
        it.copy(secondaryMuscleGroups = updated)
    }

    /** No-op in edit mode — exercise type is immutable after creation (§5.2). */
    fun onExerciseTypeChange(type: ExerciseType) {
        if (isEditMode) return
        _uiState.update { it.copy(exerciseType = type) }
    }

    fun onInstructionsChange(value: String) = _uiState.update { it.copy(instructions = value) }

    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            val path = mediaStore.copyToAppStorage(uri)
            if (path != null) _uiState.update { it.copy(mediaPath = path) }
        }
    }

    fun save(onSaved: () -> Unit) {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(nameError = true) }
            return
        }
        viewModelScope.launch {
            val exercise = Exercise(
                id = editingId ?: UUID.randomUUID().toString(),
                name = state.name.trim(),
                exerciseType = state.exerciseType,
                primaryMuscleGroup = state.primaryMuscleGroup,
                secondaryMuscleGroups = state.secondaryMuscleGroups.toList(),
                equipment = state.equipment,
                instructions = state.instructions.trim(),
                mediaPath = state.mediaPath,
                isCustom = true,
                isBodyweightVolumeEligible = loadedIsBodyweightVolumeEligible,
                isDeleted = false,
                createdAt = loadedCreatedAt,
                updatedAt = clock.now().toEpochMilliseconds(),
                // Guards against a stale head surviving a group change some other path missed --
                // every element must actually belong to the current group's own list.
                muscleHeads = state.muscleHeads.filter { it in state.primaryMuscleGroup.availableHeads },
            )
            exerciseRepository.upsertCustom(exercise)
            // The replaced image is unreferenced once the new row has committed. Images picked and
            // then abandoned before saving are left to the launch-time orphan sweep.
            loadedMediaPath?.takeIf { it != state.mediaPath }?.let { mediaFileCleaner.deleteIfUnreferenced(it) }
            onSaved()
        }
    }

    companion object {
        const val EXERCISE_ID_ARG = "exerciseId"
        const val PREFILL_NAME_ARG = "prefillName"
    }
}

data class CustomExerciseEditorUiState(
    val isEditMode: Boolean = false,
    val isLoading: Boolean = false,
    val loadError: Boolean = false,
    val name: String = "",
    val nameError: Boolean = false,
    val equipment: Equipment = Equipment.NONE,
    val primaryMuscleGroup: MuscleGroup = MuscleGroup.OTHER,
    val secondaryMuscleGroups: Set<MuscleGroup> = emptySet(),
    val exerciseType: ExerciseType = ExerciseType.WEIGHT_REPS,
    val mediaPath: String? = null,
    val muscleHeads: Set<MuscleHead> = emptySet(),
    val instructions: String = "",
)
