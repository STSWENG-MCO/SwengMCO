package com.enil.logez.core.wellness

import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeHealthMetricsSource
import java.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [liveHeartRateFlow] is a **cold** flow -- these tests collect a bounded number of emissions via
 * `.take(n).toList()` under `runTest`'s virtual time, never leaving an uncollected/uncancelled
 * poller running past the test. A leaked eager poller (started outside of collection, e.g. from a
 * ViewModel's `init` block instead of a cold flow) is exactly what hung `WorkoutLoggerViewModelTest`
 * before this was redesigned -- see `liveHeartRateFlow`'s own doc comment for the full story.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveHeartRateMonitorTest {
    @Test
    fun `emits the latest heart rate on every poll while available and granted`() = runTest {
        val sample = HeartRateSample(time = Instant.ofEpochSecond(1_000), bpm = 92L)
        val source = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            latestHeartRate = sample,
        )

        val emissions = liveHeartRateFlow(source, pollIntervalMs = 5_000L).take(3).toList()

        assertEquals(listOf(sample, sample, sample), emissions)
        assertEquals(3, source.readLatestHeartRateCallCount)
    }

    @Test
    fun `emits null on every poll when Health Connect isn't available -- graceful degrade, not a crash`() = runTest {
        val source = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Unavailable, permissionsGranted = false)

        val emissions = liveHeartRateFlow(source, pollIntervalMs = 1_000L).take(2).toList()

        assertEquals(listOf(null, null), emissions)
    }

    @Test
    fun `emits null when available but not yet granted`() = runTest {
        val source = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = false,
            latestHeartRate = HeartRateSample(time = Instant.ofEpochSecond(1_000), bpm = 100L),
        )

        val first = liveHeartRateFlow(source, pollIntervalMs = 1_000L).take(1).toList().single()

        assertNull(first)
    }

    @Test
    fun `a transient read failure degrades to null for that tick instead of terminating the flow`() = runTest {
        val source = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            latestHeartRate = HeartRateSample(time = Instant.ofEpochSecond(1_000), bpm = 88L),
            throwOnReadLatestHeartRate = IllegalStateException("Health Connect IPC hiccup"),
        )

        val emissions = liveHeartRateFlow(source, pollIntervalMs = 1_000L).take(2).toList()

        assertEquals(listOf(null, null), emissions)
    }

    @Test
    fun `stops polling as soon as collection stops -- a cold flow, not a leaked background poller`() = runTest {
        val source = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            latestHeartRate = HeartRateSample(time = Instant.ofEpochSecond(1_000), bpm = 70L),
        )

        liveHeartRateFlow(source, pollIntervalMs = 1_000L).take(4).toList()

        assertEquals("take(4) must poll exactly 4 times, never more", 4, source.readLatestHeartRateCallCount)
    }
}

/** [liveHeartRateHistoryFlow] is the live-tracking screen's historical chart source -- same cold,
 * never-crash, re-query-the-whole-window shape as [liveHeartRateFlow] above, tested the same way. */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveHeartRateHistoryFlowTest {
    @Test
    fun `re-queries the full window since the run started on every poll`() = runTest {
        val samples = listOf(
            HeartRateSample(time = Instant.ofEpochMilli(1_000_000L), bpm = 120L),
            HeartRateSample(time = Instant.ofEpochMilli(1_010_000L), bpm = 125L),
        )
        val source = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            heartRateSamples = samples,
        )
        val clock = FakeClock(currentMillis = 1_020_000L)

        val emissions = liveHeartRateHistoryFlow(source, startedAtMillis = 1_000_000L, clock = clock, pollIntervalMs = 5_000L)
            .take(2).toList()

        assertEquals(listOf(samples, samples), emissions)
        val expectedRange = Instant.ofEpochMilli(1_000_000L) to Instant.ofEpochMilli(1_020_000L)
        assertEquals(listOf(expectedRange, expectedRange), source.queriedRanges)
    }

    @Test
    fun `emits an empty list on every poll when Health Connect isn't available -- graceful degrade, not a crash`() = runTest {
        val source = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Unavailable, permissionsGranted = false)
        val clock = FakeClock(currentMillis = 1_000_000L)

        val emissions = liveHeartRateHistoryFlow(source, startedAtMillis = 900_000L, clock = clock, pollIntervalMs = 1_000L)
            .take(2).toList()

        assertEquals(listOf(emptyList<HeartRateSample>(), emptyList()), emissions)
        assertTrue("must never query Health Connect when it isn't available", source.queriedRanges.isEmpty())
    }

    @Test
    fun `a transient read failure degrades to an empty list for that tick instead of terminating the flow`() = runTest {
        val source = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            heartRateSamples = listOf(HeartRateSample(time = Instant.ofEpochMilli(1_000_000L), bpm = 88L)),
            throwOnReadHeartRateSamples = IllegalStateException("Health Connect IPC hiccup"),
        )
        val clock = FakeClock(currentMillis = 1_000_000L)

        val emissions = liveHeartRateHistoryFlow(source, startedAtMillis = 900_000L, clock = clock, pollIntervalMs = 1_000L)
            .take(2).toList()

        assertEquals(listOf(emptyList<HeartRateSample>(), emptyList()), emissions)
    }

    @Test
    fun `stops polling as soon as collection stops -- a cold flow, not a leaked background poller`() = runTest {
        val source = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            permissionsGranted = true,
            heartRateSamples = listOf(HeartRateSample(time = Instant.ofEpochMilli(1_000_000L), bpm = 70L)),
        )
        val clock = FakeClock(currentMillis = 1_000_000L)

        liveHeartRateHistoryFlow(source, startedAtMillis = 900_000L, clock = clock, pollIntervalMs = 1_000L).take(4).toList()

        assertEquals("take(4) must poll exactly 4 times, never more", 4, source.queriedRanges.size)
    }

    @Test
    fun `the access flow says whether heart rate is unavailable, not allowed, or allowed`() = runTest {
        assertEquals(listOf(HeartRateAccess.UNAVAILABLE), heartRateAccessFlow(FakeHealthMetricsSource()).take(1).toList())
        val stepsOnly = FakeHealthMetricsSource(
            availabilityValue = HealthConnectAvailability.Available,
            grantedTypesOverride = setOf(HealthDataType.STEPS),
        )
        assertEquals(listOf(HeartRateAccess.NOT_GRANTED), heartRateAccessFlow(stepsOnly).take(1).toList())
        val all = FakeHealthMetricsSource(availabilityValue = HealthConnectAvailability.Available, permissionsGranted = true)
        assertEquals(listOf(HeartRateAccess.GRANTED), heartRateAccessFlow(all).take(1).toList())
    }
}
