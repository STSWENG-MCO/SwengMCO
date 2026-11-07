package com.enil.logez.fakes

import com.enil.logez.core.data.entity.GoalDefinitionEntity
import com.enil.logez.core.domain.repository.GoalRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). */
class FakeGoalRepository(
    initial: List<GoalDefinitionEntity> = emptyList(),
) : GoalRepository {
    private val state = MutableStateFlow(initial.associateBy { it.id })

    override fun observeAll(): Flow<List<GoalDefinitionEntity>> = state.map { it.values.sortedBy { g -> g.createdAt } }
    override suspend fun getById(id: String): GoalDefinitionEntity? = state.value[id]
    override suspend fun upsert(goal: GoalDefinitionEntity) { state.update { it + (goal.id to goal) } }
    override suspend fun deleteById(id: String) { state.update { it - id } }
}
