package com.enil.logez.fakes

import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2: fakes, not mocks, for ViewModel tests). */
class FakeExerciseRepository(initial: List<Exercise> = emptyList()) : ExerciseRepository {
    private val exercises = MutableStateFlow(initial.associateBy { it.id })

    /** How many times [getById] was called — lets a test assert a lookup is batched per distinct
     *  exercise rather than repeated per set/block (an N+1 regression the debt audit flagged). */
    var getByIdCallCount = 0
        private set

    override fun observeActive(): Flow<List<Exercise>> =
        exercises.map { map -> map.values.filter { !it.isDeleted } }

    override fun observeById(id: String): Flow<Exercise?> = exercises.map { it[id] }

    override suspend fun getById(id: String): Exercise? {
        getByIdCallCount++
        return exercises.value[id]
    }

    override suspend fun getAllActive(): List<Exercise> = exercises.value.values.filter { !it.isDeleted }

    override suspend fun upsertCustom(exercise: Exercise) {
        exercises.update { it + (exercise.id to exercise) }
    }

    override suspend fun softDelete(id: String) {
        exercises.update { map ->
            val existing = map[id] ?: return@update map
            map + (id to existing.copy(isDeleted = true))
        }
    }

    override suspend fun count(): Int = exercises.value.size

    fun allIncludingDeleted(): List<Exercise> = exercises.value.values.toList()
}
