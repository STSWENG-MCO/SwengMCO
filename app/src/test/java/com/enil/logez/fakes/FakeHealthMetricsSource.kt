package com.enil.logez.fakes

import com.enil.logez.core.wellness.DailyStepCount
import com.enil.logez.core.wellness.DailyTotals
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.HealthMetricsSource
import com.enil.logez.core.wellness.HeartRateSample
import java.time.Instant
import java.time.LocalDate

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). */
class FakeHealthMetricsSource(
    private val availabilityValue: HealthConnectAvailability = HealthConnectAvailability.Unavailable,
    private val permissionsGranted: Boolean = false,
    /** Overrides [permissionsGranted] with an exact partial grant; null means all-or-nothing per [permissionsGranted]. */
    private val grantedTypesOverride: Set<HealthDataType>? = null,
    private val totals: DailyTotals = DailyTotals(steps = 0L, caloriesBurned = null),
    private val latestHeartRate: HeartRateSample? = null,
    /** Mutable so a test can simulate readings that reach Health Connect after a workout was saved. */
    var heartRateSamples: List<HeartRateSample> = emptyList(),
    private val stepsHistory: List<DailyStepCount> = emptyList(),
    private val throwOnReadHeartRateSamples: Throwable? = null,
    private val throwOnReadLatestHeartRate: Throwable? = null,
    private val throwOnRevoke: Throwable? = null,
) : HealthMetricsSource {
    override val requiredPermissions: Set<String> = setOf("fake.permission.READ_STEPS", "fake.permission.READ_HEART_RATE")

    /** Every (start, end) window this fake was asked for -- lets a test assert the exact query range used. */
    val queriedRanges = mutableListOf<Pair<Instant, Instant>>()

    /** Every (start, end) date range [readStepsHistory] was asked for. */
    val queriedStepsRanges = mutableListOf<Pair<LocalDate, LocalDate>>()

    /** How many times [readLatestHeartRate] was called -- lets a test confirm a poller actually polls repeatedly. */
    var readLatestHeartRateCallCount = 0
        private set

    override fun availability(): HealthConnectAvailability = availabilityValue

    override fun permissionFor(type: HealthDataType): String = "fake.permission.READ_${type.name}"
    /** How many times [revokeAllPermissions] was called. */
    var revokeCallCount = 0
        private set

    // Mirrors the real source: nothing is granted while Health Connect is not usable.
    override suspend fun grantedTypes(): Set<HealthDataType> {
        if (availabilityValue != HealthConnectAvailability.Available) return emptySet()
        return grantedTypesOverride ?: if (permissionsGranted) HealthDataType.entries.toSet() else emptySet()
    }

    // Mirrors the real source: an ungranted type reads as 0 steps / null calories.
    override suspend fun readTodayTotals(): DailyTotals {
        val granted = grantedTypes()
        return DailyTotals(
            steps = if (HealthDataType.STEPS in granted) totals.steps else 0L,
            caloriesBurned = if (HealthDataType.CALORIES in granted) totals.caloriesBurned else null,
        )
    }

    override suspend fun revokeAllPermissions() {
        revokeCallCount++
        throwOnRevoke?.let { throw it }
    }

    override suspend fun readLatestHeartRate(withinSeconds: Long): HeartRateSample? {
        readLatestHeartRateCallCount++
        throwOnReadLatestHeartRate?.let { throw it }
        return latestHeartRate
    }

    override suspend fun readHeartRateSamples(start: Instant, end: Instant): List<HeartRateSample> {
        queriedRanges += start to end
        throwOnReadHeartRateSamples?.let { throw it }
        return heartRateSamples.filter { it.time >= start && it.time <= end }
    }

    override suspend fun readStepsHistory(start: LocalDate, end: LocalDate): List<DailyStepCount> {
        queriedStepsRanges += start to end
        return stepsHistory.filter { it.date >= start && it.date <= end }
    }
}
