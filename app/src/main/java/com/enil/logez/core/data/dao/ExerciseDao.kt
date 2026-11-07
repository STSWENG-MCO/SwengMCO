package com.enil.logez.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.MuscleGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    /** Seed insert (PHASE2_PLAN.md §7.8): new IDs land, existing IDs are silently ignored — result[i] is -1 for a conflict. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(exercises: List<ExerciseEntity>): List<Long>

    /**
     * Seed correction-safe update (§7.8): name/muscles/equipment/instructions/eligibility/
     * isDeleted only — `exercise_type` is deliberately excluded (immutable after creation) and
     * this only ever touches seed rows (`is_custom = 0`), never user-created exercises.
     */
    @Query(
        """
        UPDATE exercises SET
            name = :name,
            primary_muscle_group = :primaryMuscleGroup,
            secondary_muscle_groups = :secondaryMuscleGroups,
            equipment = :equipment,
            instructions = :instructions,
            is_bodyweight_volume_eligible = :isBodyweightVolumeEligible,
            is_deleted = :isDeleted,
            updated_at = :updatedAt
        WHERE id = :id AND is_custom = 0
        """,
    )
    suspend fun updateSeedFields(
        id: String,
        name: String,
        primaryMuscleGroup: MuscleGroup,
        secondaryMuscleGroups: List<MuscleGroup>,
        equipment: Equipment,
        instructions: String,
        isBodyweightVolumeEligible: Boolean,
        isDeleted: Boolean,
        updatedAt: Long,
    )

    /** Custom-exercise create/edit (PHASE2_PLAN.md §5.2 — M2 consumer). */
    @Upsert
    suspend fun upsert(exercise: ExerciseEntity)

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE is_deleted = 0 ORDER BY name ASC")
    fun observeAllActive(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE is_deleted = 0 ORDER BY name ASC")
    suspend fun getAllActive(): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM exercises WHERE is_custom = 0")
    suspend fun seedCount(): Int

    /**
     * Soft delete (spine entity 1) — any exercise, seed or custom (Owner directive 2026-08-26).
     * For a seed row this is not necessarily permanent: a future seed-version bump's
     * [updateSeedFields]/[pruneRetiredSeeds] pass writes `is_deleted` from the seed file's own
     * value, so an exercise still present in that file would be un-deleted on the next bump. This
     * matches how editing a seed row already behaves ([CustomExerciseEditorViewModel] always
     * writes `is_custom = 1` on save, which *does* permanently exempt a row from seed-sync) — a
     * bare delete with no edit does not get that same exemption.
     */
    // media_path cleared too (2026-09-25 review): a deleted custom exercise kept its photo
    // forever, because the row still referenced it, so neither the orphan sweep nor the backup
    // ever let it go. Once unreferenced, MediaFileJanitor's next launch sweep deletes the file.
    @Query("UPDATE exercises SET is_deleted = 1, media_path = NULL, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, updatedAt: Long)

    /**
     * §7.9's library-swap step: any seed row (`is_custom = 0`) whose id is no longer in the
     * current seed file is retired — soft delete only, never hard delete, so a workout logged
     * against a superseded id (e.g. the old 21-entry placeholder set) keeps resolving by id on
     * its History/Detail screens even though the exercise no longer appears in Browse/Create.
     */
    @Query("UPDATE exercises SET is_deleted = 1, updated_at = :updatedAt WHERE is_custom = 0 AND id NOT IN (:currentSeedIds)")
    suspend fun pruneRetiredSeeds(currentSeedIds: List<String>, updatedAt: Long)
}
