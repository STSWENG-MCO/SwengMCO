package com.enil.logez.core.data.repository

import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.dao.ExerciseDao
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ExerciseRepositoryImpl @Inject constructor(
    private val dao: ExerciseDao,
    private val clock: Clock,
) : ExerciseRepository {
    override fun observeActive(): Flow<List<Exercise>> = dao.observeAllActive().map { list -> list.map { it.toDomain() } }
    override fun observeById(id: String): Flow<Exercise?> = dao.observeById(id).map { it?.toDomain() }
    override suspend fun getById(id: String): Exercise? = dao.getById(id)?.toDomain()
    override suspend fun getAllActive(): List<Exercise> = dao.getAllActive().map { it.toDomain() }

    override suspend fun upsertCustom(exercise: Exercise) = dao.upsert(exercise.toEntity())
    override suspend fun softDelete(id: String) = dao.softDelete(id, clock.now().toEpochMilliseconds())

    override suspend fun count(): Int = dao.count()
}

private fun ExerciseEntity.toDomain() = Exercise(
    id = id,
    name = name,
    exerciseType = exerciseType,
    primaryMuscleGroup = primaryMuscleGroup,
    secondaryMuscleGroups = secondaryMuscleGroups,
    equipment = equipment,
    instructions = instructions,
    mediaPath = mediaPath,
    isCustom = isCustom,
    isBodyweightVolumeEligible = isBodyweightVolumeEligible,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    muscleHeads = muscleHeads,
)

private fun Exercise.toEntity() = ExerciseEntity(
    id = id,
    name = name,
    exerciseType = exerciseType,
    primaryMuscleGroup = primaryMuscleGroup,
    secondaryMuscleGroups = secondaryMuscleGroups,
    equipment = equipment,
    instructions = instructions,
    mediaPath = mediaPath,
    isCustom = isCustom,
    isBodyweightVolumeEligible = isBodyweightVolumeEligible,
    isDeleted = isDeleted,
    createdAt = createdAt,
    updatedAt = updatedAt,
    muscleHeads = muscleHeads,
)
