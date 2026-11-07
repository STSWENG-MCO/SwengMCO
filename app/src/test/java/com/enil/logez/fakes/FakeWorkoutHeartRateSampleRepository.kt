package com.enil.logez.fakes

import com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity
import com.enil.logez.core.domain.repository.WorkoutHeartRateSampleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). */
class FakeWorkoutHeartRateSampleRepository(initial: List<WorkoutHeartRateSampleEntity> = emptyList()) : WorkoutHeartRateSampleRepository {
    private val state = MutableStateFlow(initial)

    val all: List<WorkoutHeartRateSampleEntity> get() = state.value

    override suspend fun insertAll(samples: List<WorkoutHeartRateSampleEntity>) {
        state.value = state.value + samples
    }

    override suspend fun getForWorkout(workoutId: String): List<WorkoutHeartRateSampleEntity> =
        state.value.filter { it.workoutId == workoutId }.sortedBy { it.recordedAt }

    override fun observeForWorkout(workoutId: String): Flow<List<WorkoutHeartRateSampleEntity>> =
        state.map { list -> list.filter { it.workoutId == workoutId }.sortedBy { it.recordedAt } }

    override suspend fun deleteAll() {
        state.value = emptyList()
    }
}
