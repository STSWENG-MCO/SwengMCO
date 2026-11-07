package com.enil.logez.core.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    // --- Folders ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: RoutineFolderEntity)

    @Delete
    suspend fun deleteFolder(folder: RoutineFolderEntity)

    @Query("SELECT * FROM routine_folders ORDER BY order_index ASC")
    fun observeFolders(): Flow<List<RoutineFolderEntity>>

    @Query("SELECT * FROM routine_folders WHERE id = :id LIMIT 1")
    suspend fun getFolderById(id: String): RoutineFolderEntity?

    @Query("SELECT * FROM routine_folders ORDER BY order_index ASC")
    suspend fun getAllFoldersOnce(): List<RoutineFolderEntity>

    @Query("UPDATE routine_folders SET order_index = :orderIndex WHERE id = :id")
    suspend fun updateFolderOrderIndex(id: String, orderIndex: Int)

    @Query("UPDATE routine_folders SET name = :name, updated_at = :updatedAt WHERE id = :id")
    suspend fun renameFolder(id: String, name: String, updatedAt: Long)

    /** New folders insert at index 0 (see the entity doc comment) — shifts existing folders down. */
    @Transaction
    suspend fun createFolderAtTop(folder: RoutineFolderEntity) {
        getAllFoldersOnce().forEach { updateFolderOrderIndex(it.id, it.orderIndex + 1) }
        upsertFolder(folder)
    }

    @Transaction
    suspend fun reorderFolders(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> updateFolderOrderIndex(id, index) }
    }

    // --- Routines ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoutine(routine: RoutineEntity)

    @Update
    suspend fun updateRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutineById(id: String)

    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    suspend fun getRoutineById(id: String): RoutineEntity?

    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    fun observeRoutineById(id: String): Flow<RoutineEntity?>

    @Query("SELECT * FROM routines ORDER BY order_index ASC")
    fun observeAllRoutines(): Flow<List<RoutineEntity>>

    /** `IS`, not `=` — SQLite's `=` never matches NULL, which would silently hide every root (folderless) routine. */
    @Query("SELECT * FROM routines WHERE folder_id IS :folderId ORDER BY order_index ASC")
    fun observeRoutinesInFolder(folderId: String?): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE folder_id IS :folderId ORDER BY order_index ASC")
    suspend fun getRoutinesInFolderOnce(folderId: String?): List<RoutineEntity>

    @Query("UPDATE routines SET order_index = :orderIndex WHERE id = :id")
    suspend fun updateRoutineOrderIndex(id: String, orderIndex: Int)

    @Query("UPDATE routines SET folder_id = :folderId, updated_at = :updatedAt WHERE id = :id")
    suspend fun setRoutineFolder(id: String, folderId: String?, updatedAt: Long)

    @Transaction
    suspend fun reorderRoutines(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> updateRoutineOrderIndex(id, index) }
    }

    /** Moving into a bucket also re-homes the routine at the top of its new bucket (matches folder/routine creation convention). */
    @Transaction
    suspend fun moveRoutineToFolder(id: String, folderId: String?, updatedAt: Long) {
        getRoutinesInFolderOnce(folderId).forEach { updateRoutineOrderIndex(it.id, it.orderIndex + 1) }
        setRoutineFolder(id, folderId, updatedAt)
        updateRoutineOrderIndex(id, 0)
    }

    /** New/duplicated routines insert at the top of their bucket (folder or root) — same convention as folders. */
    @Transaction
    suspend fun createRoutineAtTop(routine: RoutineEntity, exercises: List<RoutineExerciseEntity>, sets: List<RoutineSetEntity>) {
        getRoutinesInFolderOnce(routine.folderId).forEach { updateRoutineOrderIndex(it.id, it.orderIndex + 1) }
        upsertRoutine(routine)
        insertRoutineExercises(exercises)
        insertRoutineSets(sets)
    }

    /** Routine Builder's edit-mode save: full structural replace, keeping the routine's existing position. */
    @Transaction
    suspend fun updateRoutineStructure(routine: RoutineEntity, exercises: List<RoutineExerciseEntity>, sets: List<RoutineSetEntity>) {
        deleteExercisesForRoutine(routine.id) // cascades routine_sets via FK
        upsertRoutine(routine)
        insertRoutineExercises(exercises)
        insertRoutineSets(sets)
    }

    /** One row per (routine, exercise) — grouped in Kotlin into "Bench Press, Incline DB Press, +3 more" card previews. */
    @Query(
        """
        SELECT re.routine_id AS routineId, e.name AS exerciseName, re.order_index AS orderIndex
        FROM routine_exercises re
        JOIN exercises e ON e.id = re.exercise_id
        ORDER BY re.routine_id ASC, re.order_index ASC
        """,
    )
    fun observeRoutineExercisePreviews(): Flow<List<RoutineExercisePreviewRow>>

    /**
     * M11: per-routine round count for circuit card previews — the largest per-exercise set count
     * (`MAX`, not `MIN`, so a routine whose exercises somehow drifted apart still reports the full
     * table the builder would repair it to). Rows exist for every routine; REGULAR consumers just
     * ignore them.
     */
    @Query(
        """
        SELECT routineId, MAX(setCount) AS rounds
        FROM (
            SELECT re.routine_id AS routineId, COUNT(rs.id) AS setCount
            FROM routine_exercises re
            LEFT JOIN routine_sets rs ON rs.routine_exercise_id = re.id
            GROUP BY re.id
        )
        GROUP BY routineId
        """,
    )
    fun observeRoutineRoundCounts(): Flow<List<RoutineRoundCountRow>>

    // --- Routine exercises / sets ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineExercises(exercises: List<RoutineExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutineSets(sets: List<RoutineSetEntity>)

    @Query("SELECT * FROM routine_exercises WHERE routine_id = :routineId ORDER BY order_index ASC")
    suspend fun getExercisesForRoutine(routineId: String): List<RoutineExerciseEntity>

    @Query("SELECT * FROM routine_exercises WHERE routine_id = :routineId ORDER BY order_index ASC")
    fun observeExercisesForRoutine(routineId: String): Flow<List<RoutineExerciseEntity>>

    @Query("SELECT * FROM routine_sets WHERE routine_exercise_id = :routineExerciseId ORDER BY order_index ASC")
    suspend fun getSetsForRoutineExercise(routineExerciseId: String): List<RoutineSetEntity>

    @Query("DELETE FROM routine_exercises WHERE routine_id = :routineId")
    suspend fun deleteExercisesForRoutine(routineId: String)

    /**
     * §8.10 "Update Routine Values" (M4c): a values-only, in-place target refresh. Deliberately
     * NOT `updateRoutineStructure`, which deletes and re-inserts every row and would mint new
     * ids for a save that changed no structure at all. Rep-range columns are intentionally
     * absent from the SET list — rep-range targets never auto-update.
     */
    @Query(
        """
        UPDATE routine_sets
        SET target_weight_kg = :targetWeightKg,
            target_reps = :targetReps,
            target_duration_seconds = :targetDurationSeconds,
            target_distance_meters = :targetDistanceMeters
        WHERE id = :id
        """,
    )
    suspend fun updateRoutineSetTargets(
        id: String,
        targetWeightKg: Double?,
        targetReps: Int?,
        targetDurationSeconds: Int?,
        targetDistanceMeters: Double?,
    )

    /**
     * Whole-structure insert: a routine plus its exercises and sets in one transaction.
     * Used by seed/test setup now; the routine builder's diff-based in-place update (only
     * touching what actually changed) is M3 work, landing alongside the builder UI it serves.
     */
    @Transaction
    suspend fun insertFullRoutine(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
        sets: List<RoutineSetEntity>,
    ) {
        upsertRoutine(routine)
        insertRoutineExercises(exercises)
        insertRoutineSets(sets)
    }
}

data class RoutineExercisePreviewRow(
    @ColumnInfo(name = "routineId") val routineId: String,
    @ColumnInfo(name = "exerciseName") val exerciseName: String,
    @ColumnInfo(name = "orderIndex") val orderIndex: Int,
)

/** M11: backs [RoutineDao.observeRoutineRoundCounts] — circuit card previews' "N rounds". */
data class RoutineRoundCountRow(
    @ColumnInfo(name = "routineId") val routineId: String,
    @ColumnInfo(name = "rounds") val rounds: Int,
)
