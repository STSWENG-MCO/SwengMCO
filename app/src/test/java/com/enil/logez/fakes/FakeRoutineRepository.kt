package com.enil.logez.fakes

import com.enil.logez.core.data.dao.RoutineExercisePreviewRow
import com.enil.logez.core.data.dao.RoutineRoundCountRow
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2) — mirrors the DAO's top-of-bucket insert/reorder semantics. */
class FakeRoutineRepository(
    folders: List<RoutineFolderEntity> = emptyList(),
    routines: List<RoutineEntity> = emptyList(),
    exercises: List<RoutineExerciseEntity> = emptyList(),
    sets: List<RoutineSetEntity> = emptyList(),
    previewRows: List<RoutineExercisePreviewRow> = emptyList(),
) : RoutineRepository {
    private val foldersState = MutableStateFlow(folders.associateBy { it.id })
    private val routinesState = MutableStateFlow(routines.associateBy { it.id })
    private val exercisesState = MutableStateFlow(exercises)
    private val setsState = MutableStateFlow(sets)
    private val previewRowsState = MutableStateFlow(previewRows)

    override fun observeFolders(): Flow<List<RoutineFolderEntity>> = foldersState.map { it.values.sortedBy { f -> f.orderIndex } }
    override suspend fun getFolderById(id: String): RoutineFolderEntity? = foldersState.value[id]

    override suspend fun createFolderAtTop(folder: RoutineFolderEntity) {
        foldersState.update { map -> map.mapValues { (_, f) -> f.copy(orderIndex = f.orderIndex + 1) } + (folder.id to folder.copy(orderIndex = 0)) }
    }

    override suspend fun renameFolder(id: String, name: String, updatedAt: Long) {
        foldersState.update { map -> map[id]?.let { map + (id to it.copy(name = name, updatedAt = updatedAt)) } ?: map }
    }

    override suspend fun deleteFolder(folder: RoutineFolderEntity) {
        foldersState.update { it - folder.id }
        routinesState.update { map -> map.mapValues { (_, r) -> if (r.folderId == folder.id) r.copy(folderId = null) else r } }
    }

    override suspend fun reorderFolders(orderedIds: List<String>) {
        foldersState.update { map -> map.mapValues { (id, f) -> orderedIds.indexOf(id).let { i -> if (i >= 0) f.copy(orderIndex = i) else f } } }
    }

    override fun observeAllRoutines(): Flow<List<RoutineEntity>> = routinesState.map { it.values.sortedBy { r -> r.orderIndex } }

    override fun observeRoutinesInFolder(folderId: String?): Flow<List<RoutineEntity>> =
        routinesState.map { it.values.filter { r -> r.folderId == folderId }.sortedBy { r -> r.orderIndex } }

    override fun observeRoutineById(id: String): Flow<RoutineEntity?> = routinesState.map { it[id] }
    override suspend fun getRoutineById(id: String): RoutineEntity? = routinesState.value[id]

    override suspend fun deleteRoutineById(id: String) {
        routinesState.update { it - id }
        val orphanedExerciseIds = exercisesState.value.filter { it.routineId == id }.map { it.id }.toSet()
        exercisesState.update { list -> list.filterNot { it.routineId == id } }
        setsState.update { list -> list.filterNot { it.routineExerciseId in orphanedExerciseIds } }
    }

    override suspend fun reorderRoutines(orderedIds: List<String>) {
        routinesState.update { map -> map.mapValues { (id, r) -> orderedIds.indexOf(id).let { i -> if (i >= 0) r.copy(orderIndex = i) else r } } }
    }

    override suspend fun moveRoutineToFolder(id: String, folderId: String?, updatedAt: Long) {
        routinesState.update { map ->
            val moving = map.getValue(id)
            val shifted = map.mapValues { (rid, r) -> if (r.folderId == folderId && rid != id) r.copy(orderIndex = r.orderIndex + 1) else r }
            shifted + (id to moving.copy(folderId = folderId, orderIndex = 0, updatedAt = updatedAt))
        }
    }

    override fun observeExercisesForRoutine(routineId: String): Flow<List<RoutineExerciseEntity>> =
        exercisesState.map { list -> list.filter { it.routineId == routineId }.sortedBy { it.orderIndex } }

    override suspend fun getExercisesForRoutine(routineId: String): List<RoutineExerciseEntity> =
        exercisesState.value.filter { it.routineId == routineId }.sortedBy { it.orderIndex }

    override suspend fun getSetsForRoutineExercise(routineExerciseId: String): List<RoutineSetEntity> =
        setsState.value.filter { it.routineExerciseId == routineExerciseId }.sortedBy { it.orderIndex }

    override fun observeRoutineExercisePreviews(): Flow<List<RoutineExercisePreviewRow>> = previewRowsState

    /** Mirrors the DAO's MAX(per-exercise set count) per routine. */
    override fun observeRoutineRoundCounts(): Flow<List<RoutineRoundCountRow>> =
        kotlinx.coroutines.flow.combine(exercisesState, setsState) { exercises, sets ->
            exercises.groupBy { it.routineId }.map { (routineId, routineExercises) ->
                RoutineRoundCountRow(
                    routineId = routineId,
                    rounds = routineExercises.maxOf { re -> sets.count { it.routineExerciseId == re.id } },
                )
            }
        }

    override suspend fun insertFullRoutine(routine: RoutineEntity, exercises: List<RoutineExerciseEntity>, sets: List<RoutineSetEntity>) {
        routinesState.update { it + (routine.id to routine) }
        exercisesState.update { it + exercises }
        setsState.update { it + sets }
    }

    override suspend fun createRoutineAtTop(routine: RoutineEntity, exercises: List<RoutineExerciseEntity>, sets: List<RoutineSetEntity>) {
        routinesState.update { map ->
            val shifted = map.mapValues { (_, r) -> if (r.folderId == routine.folderId) r.copy(orderIndex = r.orderIndex + 1) else r }
            shifted + (routine.id to routine.copy(orderIndex = 0))
        }
        exercisesState.update { it + exercises }
        setsState.update { it + sets }
    }

    override suspend fun updateRoutineStructure(routine: RoutineEntity, exercises: List<RoutineExerciseEntity>, sets: List<RoutineSetEntity>) {
        val oldExerciseIds = exercisesState.value.filter { it.routineId == routine.id }.map { it.id }.toSet()
        setsState.update { list -> list.filterNot { it.routineExerciseId in oldExerciseIds } }
        exercisesState.update { list -> list.filterNot { it.routineId == routine.id } }
        routinesState.update { it + (routine.id to routine) }
        exercisesState.update { it + exercises }
        setsState.update { it + sets }
    }

    /** §8.10 values-only update — mirrors the real query in leaving the rep-range columns alone. */
    override suspend fun updateRoutineSetTargets(
        id: String,
        targetWeightKg: Double?,
        targetReps: Int?,
        targetDurationSeconds: Int?,
        targetDistanceMeters: Double?,
    ) {
        setsState.update { list ->
            list.map {
                if (it.id != id) {
                    it
                } else {
                    it.copy(
                        targetWeightKg = targetWeightKg,
                        targetReps = targetReps,
                        targetDurationSeconds = targetDurationSeconds,
                        targetDistanceMeters = targetDistanceMeters,
                    )
                }
            }
        }
    }
}
