package com.enil.logez.core.data.repository

import com.enil.logez.core.data.dao.WellnessDao
import com.enil.logez.core.data.entity.DailyWellnessTotalEntity
import com.enil.logez.core.domain.repository.DailyWellnessTotal
import com.enil.logez.core.domain.repository.WellnessRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class WellnessRepositoryImpl @Inject constructor(
    private val dao: WellnessDao,
) : WellnessRepository {
    override suspend fun upsert(total: DailyWellnessTotal) = dao.upsert(total.toEntity())
    override suspend fun getByDate(date: String): DailyWellnessTotal? = dao.getByDate(date)?.toDomain()
    override fun observeByDate(date: String): Flow<DailyWellnessTotal?> = dao.observeByDate(date).map { it?.toDomain() }
    override suspend fun deleteAll() = dao.deleteAll()
}

private fun DailyWellnessTotalEntity.toDomain() = DailyWellnessTotal(
    date = date, steps = steps, caloriesBurned = caloriesBurned, updatedAt = updatedAt,
)

private fun DailyWellnessTotal.toEntity() = DailyWellnessTotalEntity(
    date = date, steps = steps, caloriesBurned = caloriesBurned, updatedAt = updatedAt,
)
