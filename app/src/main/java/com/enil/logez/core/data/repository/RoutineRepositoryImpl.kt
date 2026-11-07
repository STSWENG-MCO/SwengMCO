package com.enil.logez.core.data.repository

import com.enil.logez.core.data.dao.RoutineDao
import com.enil.logez.core.data.dao.RoutineExercisePreviewRow
import com.enil.logez.core.data.dao.RoutineRoundCountRow
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.domain.repository.RoutineRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class RoutineRepositoryImpl @Inject constructor(
    private val dao: RoutineDao,
) : RoutineRepository {
    override fun observeFolders(): Flow<List<RoutineFolderEntity>> = dao.observeFolders()
    override suspend fun getFolderById(id: String) = dao.getFolderById(id)
    override suspend fun createFolderAtTop(folder: RoutineFolderEntity) = dao.createFolderAtTop(folder)
    override suspend fun renameFolder(id: String, name: String, updatedAt: Long) = dao.renameFolder(id, name, updatedAt)
    override suspend fun deleteFolder(folder: RoutineFolderEntity) = dao.deleteFolder(folder)
    override suspend fun reorderFolders(orderedIds: List<String>) = dao.reorderFolders(orderedIds)

    override fun observeAllRoutines(): Flow<List<RoutineEntity>> = dao.observeAllRoutines()
    override fun observeRoutinesInFolder(folderId: String?): Flow<List<RoutineEntity>> = dao.observeRoutinesInFolder(folderId)
    override fun observeRoutineById(id: String): Flow<RoutineEntity?> = dao.observeRoutineById(id)
    override suspend fun getRoutineById(id: String) = dao.getRoutineById(id)
    override suspend fun deleteRoutineById(id: String) = dao.deleteRoutineById(id)
    override suspend fun reorderRoutines(orderedIds: List<String>) = dao.reorderRoutines(orderedIds)
    override suspend fun moveRoutineToFolder(id: String, folderId: String?, updatedAt: Long) =
        dao.moveRoutineToFolder(id, folderId, updatedAt)

    override fun observeExercisesForRoutine(routineId: String): Flow<List<RoutineExerciseEntity>> =
        dao.observeExercisesForRoutine(routineId)

    override suspend fun getExercisesForRoutine(routineId: String): List<RoutineExerciseEntity> =
        dao.getExercisesForRoutine(routineId)

    override suspend fun getSetsForRoutineExercise(routineExerciseId: String): List<RoutineSetEntity> =
        dao.getSetsForRoutineExercise(routineExerciseId)

    override fun observeRoutineExercisePreviews(): Flow<List<RoutineExercisePreviewRow>> =
        dao.observeRoutineExercisePreviews()

    override fun observeRoutineRoundCounts(): Flow<List<RoutineRoundCountRow>> =
        dao.observeRoutineRoundCounts()

    override suspend fun insertFullRoutine(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
        sets: List<RoutineSetEntity>,
    ) = dao.insertFullRoutine(routine, exercises, sets)

    override suspend fun createRoutineAtTop(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
        sets: List<RoutineSetEntity>,
    ) = dao.createRoutineAtTop(routine, exercises, sets)

    override suspend fun updateRoutineStructure(
        routine: RoutineEntity,
        exercises: List<RoutineExerciseEntity>,
        sets: List<RoutineSetEntity>,
    ) = dao.updateRoutineStructure(routine, exercises, sets)

    override suspend fun updateRoutineSetTargets(
        id: String,
        targetWeightKg: Double?,
        targetReps: Int?,
        targetDurationSeconds: Int?,
        targetDistanceMeters: Double?,
    ) = dao.updateRoutineSetTargets(id, targetWeightKg, targetReps, targetDurationSeconds, targetDistanceMeters)
}
