package com.enil.logez.core.domain.repository

import com.enil.logez.core.data.entity.GoalDefinitionEntity
import kotlinx.coroutines.flow.Flow

/** CRUD access to user-defined training goals. */
interface GoalRepository {
    fun observeAll(): Flow<List<GoalDefinitionEntity>>
    suspend fun getById(id: String): GoalDefinitionEntity?
    suspend fun upsert(goal: GoalDefinitionEntity)
    suspend fun deleteById(id: String)
}
