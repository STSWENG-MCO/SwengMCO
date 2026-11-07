package com.enil.logez.core.data.repository

import com.enil.logez.core.data.dao.ActivityTrackDao
import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.domain.repository.ActivityTrackRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ActivityTrackRepositoryImpl @Inject constructor(
    private val dao: ActivityTrackDao,
) : ActivityTrackRepository {
    override suspend fun upsert(track: ActivityTrackEntity) = dao.upsert(track)
    override suspend fun getByWorkoutSetId(workoutSetId: String): ActivityTrackEntity? = dao.getByWorkoutSetId(workoutSetId)
    override fun observeByWorkoutSetId(workoutSetId: String): Flow<ActivityTrackEntity?> = dao.observeByWorkoutSetId(workoutSetId)
}
