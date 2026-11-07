package com.enil.logez.fakes

import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.domain.repository.ActivityTrackRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). */
class FakeActivityTrackRepository(initial: List<ActivityTrackEntity> = emptyList()) : ActivityTrackRepository {
    private val tracksState = MutableStateFlow(initial.associateBy { it.workoutSetId })

    override suspend fun upsert(track: ActivityTrackEntity) {
        tracksState.value = tracksState.value + (track.workoutSetId to track)
    }

    override suspend fun getByWorkoutSetId(workoutSetId: String): ActivityTrackEntity? = tracksState.value[workoutSetId]

    override fun observeByWorkoutSetId(workoutSetId: String): Flow<ActivityTrackEntity?> =
        tracksState.map { it[workoutSetId] }
}
