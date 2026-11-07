package com.enil.logez.core.domain.repository

import com.enil.logez.core.data.entity.ActivityTrackEntity
import kotlinx.coroutines.flow.Flow

/** Persists GPS route/distance data for a tracked walk/run, keyed by the workout set it belongs to. */
interface ActivityTrackRepository {
    suspend fun upsert(track: ActivityTrackEntity)
    suspend fun getByWorkoutSetId(workoutSetId: String): ActivityTrackEntity?
    fun observeByWorkoutSetId(workoutSetId: String): Flow<ActivityTrackEntity?>
}
