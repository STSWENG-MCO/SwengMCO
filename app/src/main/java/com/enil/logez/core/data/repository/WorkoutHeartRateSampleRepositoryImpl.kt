package com.enil.logez.core.data.repository

import com.enil.logez.core.data.dao.WorkoutHeartRateSampleDao
import com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity
import com.enil.logez.core.domain.repository.WorkoutHeartRateSampleRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class WorkoutHeartRateSampleRepositoryImpl @Inject constructor(
    private val dao: WorkoutHeartRateSampleDao,
) : WorkoutHeartRateSampleRepository {
    override suspend fun insertAll(samples: List<WorkoutHeartRateSampleEntity>) = dao.insertAll(samples)
    override suspend fun getForWorkout(workoutId: String): List<WorkoutHeartRateSampleEntity> = dao.getForWorkout(workoutId)
    override fun observeForWorkout(workoutId: String): Flow<List<WorkoutHeartRateSampleEntity>> = dao.observeForWorkout(workoutId)
    override suspend fun deleteAll() = dao.deleteAll()
}
