package com.enil.logez.core.domain.repository

import com.enil.logez.core.data.dao.RoutineExercisePreviewRow
import com.enil.logez.core.data.dao.RoutineRoundCountRow
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import kotlinx.coroutines.flow.Flow

/** Routines and the folders that organize them. */
interface RoutineRepository {
    fun observeFolders(): Flow<List<RoutineFolderEntity>>
    suspend fun getFolderById(id: String): RoutineFolderEntity?
    suspend fun createFolderAtTop(folder: RoutineFolderEntity)
    suspend fun renameFolder(id: String, name: String, updatedAt: Long)
    suspend fun deleteFolder(folder: RoutineFolderEntity)
    suspend fun reorderFolders(orderedIds: List<String>)

    fun observeAllRoutines(): Flow<List<RoutineEntity>>
    fun observeRoutinesInFolder(folderId: String?): Flow<List<RoutineEntity>>
    fun observeRoutineById(id: String): Flow<RoutineEntity?>
    suspend fun getRoutineById(id: String): RoutineEntity?
    suspend fun deleteRoutineById(id: String)
    suspend fun reorderRoutines(orderedIds: List<String>)
    suspend fun moveRoutineToFolder(id: String, folderId: String?, updatedAt: Long)

    fun observeExercisesForRoutine(routineId: String): Flow<List<RoutineExerciseEntity>>
    suspend fun getExercisesForRoutine(routineId: String): List<RoutineExerciseEntity>
    suspend fun getSetsForRoutineExercise(routineExerciseId: String): List<RoutineSetEntity>
    fun observeRoutineExercisePreviews(): Flow<List<RoutineExercisePreviewRow>>

    /** M11: routineId -> round count (largest per-exercise set count) for circuit card previews. */
    fun observeRoutineRoundCounts(): Flow<List<RoutineRoundCountRow>>

    suspend fun insertFullRoutine(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
        sets: List<RoutineSetEntity>,
    )

    /** Routine Builder create-mode save: new routine inserted at the top of its bucket. */
    suspend fun createRoutineAtTop(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
        sets: List<RoutineSetEntity>,
    )

    /** Routine Builder edit-mode save: full structural replace, position unchanged. */
    suspend fun updateRoutineStructure(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
        sets: List<RoutineSetEntity>,
    )

    /** §8.10 "Update Routine Values" (M4c): in-place target refresh, no structural churn, rep-ranges untouched. */
    suspend fun updateRoutineSetTargets(
        id: String,
        targetWeightKg: Double?,
        targetReps: Int?,
        targetDurationSeconds: Int?,
        targetDistanceMeters: Double?,
    )
}
