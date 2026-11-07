package com.enil.logez.core.domain.repository

import com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity
import kotlinx.coroutines.flow.Flow

/** Persists heart-rate samples (from Health Connect) captured during a specific workout. */
interface WorkoutHeartRateSampleRepository {
    suspend fun insertAll(samples: List<WorkoutHeartRateSampleEntity>)
    suspend fun getForWorkout(workoutId: String): List<WorkoutHeartRateSampleEntity>
    fun observeForWorkout(workoutId: String): Flow<List<WorkoutHeartRateSampleEntity>>

    /** Deletes every saved heart-rate sample -- Settings > Data's Health Connect disconnect. */
    suspend fun deleteAll()
}
