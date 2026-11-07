package com.enil.logez.core.domain.repository

import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.MuscleHead
import kotlinx.coroutines.flow.Flow

/** The exercise library: seeded exercises plus any user-created custom exercises. */
interface ExerciseRepository {
    fun observeActive(): Flow<List<Exercise>>
    fun observeById(id: String): Flow<Exercise?>
    suspend fun getById(id: String): Exercise?
    suspend fun getAllActive(): List<Exercise>

    /**
     * Create or edit (PHASE2_PLAN.md §5.2 — M2, widened 2026-08-26 to cover seeded exercises too).
     * Exercise type is immutable on edit. Editing any exercise — seed or custom — always writes
     * `isCustom = true`, permanently exempting that row from future seed-file syncs.
     */
    suspend fun upsertCustom(exercise: Exercise)

    /** Soft delete — any exercise, seed or custom. See [com.enil.logez.core.data.dao.ExerciseDao.softDelete] for the seed-resurrection caveat. */
    suspend fun softDelete(id: String)

    suspend fun count(): Int
}

/** Domain model mirroring [com.enil.logez.core.data.entity.ExerciseEntity] field-for-field. */
data class Exercise(
    val id: String,
    val name: String,
    val exerciseType: ExerciseType,
    val primaryMuscleGroup: MuscleGroup,
    val secondaryMuscleGroups: List<MuscleGroup>,
    val equipment: Equipment,
    val instructions: String,
    val mediaPath: String?,
    val isCustom: Boolean,
    val isBodyweightVolumeEligible: Boolean,
    val isDeleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    /** M8e — a checklist, not a single pick: an exercise can work more than one head of the same group. */
    val muscleHeads: List<MuscleHead> = emptyList(),
)
