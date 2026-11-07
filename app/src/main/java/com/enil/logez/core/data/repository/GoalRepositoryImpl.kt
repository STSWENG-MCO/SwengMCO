package com.enil.logez.core.data.repository

import com.enil.logez.core.data.dao.GoalDao
import com.enil.logez.core.data.entity.GoalDefinitionEntity
import com.enil.logez.core.domain.repository.GoalRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GoalRepositoryImpl @Inject constructor(
    private val dao: GoalDao,
) : GoalRepository {
    override fun observeAll(): Flow<List<GoalDefinitionEntity>> = dao.observeAll()
    override suspend fun getById(id: String): GoalDefinitionEntity? = dao.getById(id)
    override suspend fun upsert(goal: GoalDefinitionEntity) = dao.upsert(goal)
    override suspend fun deleteById(id: String) = dao.deleteById(id)
}
