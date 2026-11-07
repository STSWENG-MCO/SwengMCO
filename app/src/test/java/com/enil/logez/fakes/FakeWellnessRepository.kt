package com.enil.logez.fakes

import com.enil.logez.core.domain.repository.DailyWellnessTotal
import com.enil.logez.core.domain.repository.WellnessRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). */
class FakeWellnessRepository(initial: List<DailyWellnessTotal> = emptyList()) : WellnessRepository {
    private val state = MutableStateFlow(initial.associateBy { it.date })

    val all: List<DailyWellnessTotal> get() = state.value.values.toList()

    override suspend fun upsert(total: DailyWellnessTotal) {
        state.value = state.value + (total.date to total)
    }

    override suspend fun getByDate(date: String): DailyWellnessTotal? = state.value[date]

    override fun observeByDate(date: String): Flow<DailyWellnessTotal?> = state.map { it[date] }

    override suspend fun deleteAll() {
        state.value = emptyMap()
    }
}
