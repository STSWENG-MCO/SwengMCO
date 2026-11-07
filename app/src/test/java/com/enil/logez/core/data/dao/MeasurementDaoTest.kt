package com.enil.logez.core.data.dao

import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.entity.BodyMeasurementEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric 4.13's max supported SDK is 34; this app targets 36 (Fiterval precedent — same pin).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MeasurementDaoTest : RoomDatabaseTestBase() {
    private val dao get() = database.measurementDao()

    private fun entry(date: String, weightKg: Double?) = BodyMeasurementEntity(
        date = date, weightKg = weightKg, leanMassKg = null, fatPercent = null, neckCm = null,
        shoulderCm = null, chestCm = null, leftBicepCm = null, rightBicepCm = null, leftForearmCm = null,
        rightForearmCm = null, abdomenCm = null, waistCm = null, hipsCm = null, leftThighCm = null,
        rightThighCm = null, leftCalfCm = null, rightCalfCm = null, updatedAt = 0,
    )

    @Test
    fun `upsert by date REPLACEs rather than duplicating`() = runTest {
        dao.upsertMeasurement(entry("2026-08-01", 70.0))
        dao.upsertMeasurement(entry("2026-08-01", 71.0))

        assertEquals(71.0, dao.getByDate("2026-08-01")!!.weightKg)
    }

    @Test
    fun `getLatestWeightOnOrBefore finds the newest entry at or before the date, ignoring null weights`() = runTest {
        dao.upsertMeasurement(entry("2026-08-01", 70.0))
        dao.upsertMeasurement(entry("2026-08-15", 72.0))
        dao.upsertMeasurement(entry("2026-08-20", null)) // e.g. a waist-only entry — no weight logged that day

        assertEquals(72.0, dao.getLatestWeightOnOrBefore("2026-08-20")!!.weightKg)
        assertEquals(72.0, dao.getLatestWeightOnOrBefore("2026-08-15")!!.weightKg)
        assertEquals(70.0, dao.getLatestWeightOnOrBefore("2026-08-10")!!.weightKg)
        assertNull(dao.getLatestWeightOnOrBefore("2026-07-30"))
    }
}
