package com.enil.logez.fakes

import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2) — mirrors the DAO's delete-then-insert-per-exercise rebuild. */
class FakePersonalRecordsRepository(initial: List<PersonalRecordEntity> = emptyList()) : PersonalRecordsRepository {
    private val state = MutableStateFlow(initial)

    val all: List<PersonalRecordEntity> get() = state.value

    override suspend fun rebuildFor(exerciseId: String, records: List<PersonalRecordEntity>) {
        state.value = state.value.filterNot { it.exerciseId == exerciseId } + records
    }

    override suspend fun getForWorkout(workoutId: String): List<PersonalRecordEntity> =
        state.value.filter { it.workoutId == workoutId }

    override suspend fun getWorkoutIdsWithRecords(): Set<String> = state.value.map { it.workoutId }.toSet()

    override suspend fun getAchievedBetween(fromMillis: Long, untilMillis: Long): List<PersonalRecordEntity> =
        state.value.filter { it.achievedAt >= fromMillis && it.achievedAt < untilMillis }

    override suspend fun getForExercise(exerciseId: String): List<PersonalRecordEntity> =
        state.value.filter { it.exerciseId == exerciseId }

    override fun observeForWorkout(workoutId: String): Flow<List<PersonalRecordEntity>> =
        state.map { list -> list.filter { it.workoutId == workoutId } }

    override fun observeForExercise(exerciseId: String): Flow<List<PersonalRecordEntity>> =
        state.map { list -> list.filter { it.exerciseId == exerciseId } }
}
