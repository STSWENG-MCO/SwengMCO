package com.enil.logez.core.wellness

import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeWorkoutHeartRateSampleRepository
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutHeartRateBackfillTest {
    private val start = 1_000_000L
    private val end = start + 600_000L

    private fun source(vararg samples: Pair<Long, Long>, granted: Boolean = true) = FakeHealthMetricsSource(
        availabilityValue = HealthConnectAvailability.Available,
        permissionsGranted = granted,
        heartRateSamples = samples.map { (at, bpm) -> HeartRateSample(Instant.ofEpochMilli(at), bpm) },
    )

    @Test
    fun `saves the workout's readings the first time`() = runTest {
        val repo = FakeWorkoutHeartRateSampleRepository()
        val added = WorkoutHeartRateBackfill(source(start + 1_000 to 120L, start + 2_000 to 125L), repo, AppLogger.NoOp).backfill("w1", start, end)
        assertEquals(2, added)
        assertEquals(listOf(120L, 125L), repo.getForWorkout("w1").map { it.bpm })
    }

    @Test
    fun `a later look adds only the readings that arrived since, never duplicates`() = runTest {
        val repo = FakeWorkoutHeartRateSampleRepository(listOf(WorkoutHeartRateSampleEntity("old", "w1", start + 1_000, 120L)))
        val health = source(start + 1_000 to 120L, start + 300_000 to 150L)
        val added = WorkoutHeartRateBackfill(health, repo, AppLogger.NoOp).backfill("w1", start, end)
        assertEquals(1, added)
        assertEquals(2, repo.getForWorkout("w1").size)
        assertEquals(0, WorkoutHeartRateBackfill(health, repo, AppLogger.NoOp).backfill("w1", start, end))
    }

    @Test
    fun `nothing is read or removed without access, and a failing read changes nothing`() = runTest {
        val repo = FakeWorkoutHeartRateSampleRepository(listOf(WorkoutHeartRateSampleEntity("old", "w1", start + 1_000, 120L)))
        assertEquals(0, WorkoutHeartRateBackfill(source(start + 5_000 to 130L, granted = false), repo, AppLogger.NoOp).backfill("w1", start, end))
        val failing = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            throwOnReadHeartRateSamples = IllegalStateException("rate limited"),
        )
        assertEquals(0, WorkoutHeartRateBackfill(failing, repo, AppLogger.NoOp).backfill("w1", start, end))
        assertEquals(1, repo.getForWorkout("w1").size)
    }

    @Test
    fun `a workout with no duration has no window to read`() = runTest {
        val repo = FakeWorkoutHeartRateSampleRepository()
        assertEquals(0, WorkoutHeartRateBackfill(source(start to 120L), repo, AppLogger.NoOp).backfill("w1", start, start))
    }
}
