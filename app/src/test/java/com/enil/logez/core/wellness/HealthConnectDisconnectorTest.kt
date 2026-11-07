package com.enil.logez.core.wellness

import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity
import com.enil.logez.core.domain.WidgetRefresher
import com.enil.logez.core.domain.repository.DailyWellnessTotal
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeWellnessRepository
import com.enil.logez.fakes.FakeWorkoutHeartRateSampleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthConnectDisconnectorTest {
    private val wellnessRepo = FakeWellnessRepository(
        listOf(DailyWellnessTotal(date = "2026-09-25", steps = 4_000L, caloriesBurned = 1_800.0, updatedAt = 1L)),
    )
    private val heartRateRepo = FakeWorkoutHeartRateSampleRepository(
        listOf(WorkoutHeartRateSampleEntity(id = "s1", workoutId = "w1", recordedAt = 1L, bpm = 120L)),
    )
    private var widgetRefreshes = 0
    private val widgetRefresher = object : WidgetRefresher {
        override suspend fun refresh() { widgetRefreshes++ }
    }

    private fun disconnector(source: FakeHealthMetricsSource) =
        HealthConnectDisconnector(source, wellnessRepo, heartRateRepo, widgetRefresher, AppLogger.NoOp)

    @Test
    fun `revokes access, deletes both local copies and repaints the widget`() = runTest {
        val source = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Available, permissionsGranted = true)

        disconnector(source).disconnectAndDelete()

        assertEquals(1, source.revokeCallCount)
        assertTrue(wellnessRepo.all.isEmpty())
        assertTrue(heartRateRepo.all.isEmpty())
        assertEquals(1, widgetRefreshes)
    }

    /** Health Connect may be uninstalled or refuse; the user's delete request must still happen. */
    @Test
    fun `a failed revoke still deletes the local copies`() = runTest {
        val source = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            throwOnRevoke = IllegalStateException("Health Connect unavailable"),
        )

        disconnector(source).disconnectAndDelete()

        assertTrue(wellnessRepo.all.isEmpty())
        assertTrue(heartRateRepo.all.isEmpty())
    }
}
