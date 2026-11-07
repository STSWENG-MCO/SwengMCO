package com.enil.logez.core.data.dao

import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.entity.GoalDefinitionEntity
import com.enil.logez.core.domain.model.GoalMetric
import com.enil.logez.core.domain.model.GoalPeriod
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoalDaoTest : RoomDatabaseTestBase() {
    private val dao get() = database.goalDao()

    private fun goal(id: String, target: Double = 10_000.0, createdAt: Long = 0L) = GoalDefinitionEntity(
        id = id, metric = GoalMetric.VOLUME, period = GoalPeriod.WEEKLY, targetValue = target, createdAt = createdAt, updatedAt = createdAt,
    )

    @Test
    fun `upsert then getById round-trips`() = runTest {
        dao.upsert(goal("g1"))
        val loaded = dao.getById("g1")
        assertEquals(10_000.0, loaded!!.targetValue, 0.0)
    }

    @Test
    fun `upsert on an existing id replaces it, not duplicates it`() = runTest {
        dao.upsert(goal("g1", target = 1_000.0))
        dao.upsert(goal("g1", target = 2_000.0))
        assertEquals(1, dao.observeAll().first().size)
        assertEquals(2_000.0, dao.getById("g1")!!.targetValue, 0.0)
    }

    @Test
    fun `observeAll orders by createdAt ascending`() = runTest {
        dao.upsert(goal("newer", createdAt = 200L))
        dao.upsert(goal("older", createdAt = 100L))
        val all = dao.observeAll().first()
        assertEquals(listOf("older", "newer"), all.map { it.id })
    }

    @Test
    fun `deleteById removes the goal`() = runTest {
        dao.upsert(goal("g1"))
        dao.deleteById("g1")
        assertNull(dao.getById("g1"))
        assertEquals(0, dao.observeAll().first().size)
    }
}
