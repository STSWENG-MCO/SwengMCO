package com.enil.logez.core.wellness

import java.time.Instant
import java.time.LocalDate

/** Whether Health Connect can actually be used on this device right now. */
sealed interface HealthConnectAvailability {
    /** No Health Connect provider installed, or the platform doesn't support it at all. */
    data object Unavailable : HealthConnectAvailability

    /** A Health Connect provider is present but needs a Play Store update before it can be used. */
    data object UpdateRequired : HealthConnectAvailability

    /** Ready to read from. */
    data object Available : HealthConnectAvailability
}

/**
 * The three Health Connect record types LogEZ reads. Health Connect's permission screen lets the
 * user tick any subset of them, so every feature checks the one type it needs rather than
 * requiring all three (see [HealthMetricsSource.grantedTypes]).
 */
enum class HealthDataType { STEPS, CALORIES, HEART_RATE }

/**
 * Today's all-day totals, as Health Connect's own aggregate already reports them. [steps] is 0
 * and [caloriesBurned] null for a type the user has not granted, so callers gate on
 * [HealthMetricsSource.grantedTypes] before showing either.
 */
data class DailyTotals(val steps: Long, val caloriesBurned: Double?)

/** One Health-Connect-sourced heart-rate reading, plain-typed (no `Energy`-style unit wrapper -- `bpm` is already a beats-per-minute count). */
data class HeartRateSample(val time: Instant, val bpm: Long)

/** One calendar day's step total, as Health Connect's own per-day aggregate reports it. */
data class DailyStepCount(val date: LocalDate, val steps: Long)

/**
 * M21e/M21f. `HealthConnectMetricsSource` is the production binding (talks to the real Health
 * Connect app over local Binder IPC, same no-INTERNET-risk shape as `FusedLocationSource` --
 * confirmed via direct AAR/manifest inspection, decisions.md 2026-09-10); `FakeHealthMetricsSource`
 * (test source set) drives ViewModel/controller tests. Read-only -- matches both milestones' scope;
 * any write-back (M21i) is a separate, later milestone.
 */
interface HealthMetricsSource {
    /** The exact permission strings the UI's permission launcher must request. */
    val requiredPermissions: Set<String>

    fun availability(): HealthConnectAvailability

    /**
     * Which of the three data types the user has granted. Replaced an all-three-or-nothing check
     * on 2026-09-25 (Play-readiness audit): a user who granted only steps saw nothing at all,
     * and Health Connect's own screen offers exactly that choice. Empty when Health Connect is
     * not [HealthConnectAvailability.Available].
     */
    suspend fun grantedTypes(): Set<HealthDataType>

    /**
     * Today's local-calendar-day totals, reading only the granted types: Health Connect rejects
     * an aggregate that names a metric the app may not read, so asking for both with one granted
     * failed the whole call. Zero steps / null calories if a type is not granted or has nothing
     * for today yet.
     */
    suspend fun readTodayTotals(): DailyTotals

    /**
     * The single most recent heart-rate sample within the last [withinSeconds], or null if none --
     * drives the live scorecard. Returns the whole [HeartRateSample] (not just the bpm) so the UI
     * can show how stale it actually is -- a wearable's readings reach Health Connect through a
     * multi-hop sync (watch -> its companion app -> Health Connect), not in real time, so what's
     * "latest" here can genuinely be several minutes old even while the watch face itself shows a
     * fresher on-wrist reading. [withinSeconds] default widened from M21f's original 60s (Owner
     * report 2026-09-12: BPM often went blank entirely, or showed a number that looked wrong next
     * to the watch, purely because that sync lag routinely exceeds one minute) -- 5 minutes is
     * still "recent enough to mean something" without going blank on every ordinary sync gap.
     */
    suspend fun readLatestHeartRate(withinSeconds: Long = 300): HeartRateSample?

    /**
     * M21f: every heart-rate sample whose own time lies in [start]..[end], oldest first. Samples
     * are selected by their timestamps, not by which record holds them: a record that began
     * before [start] still contributes its later samples (2026-09-26, see HeartRateReadWindow).
     */
    suspend fun readHeartRateSamples(start: Instant, end: Instant): List<HeartRateSample>

    /**
     * One entry per calendar day in [start]..[end] inclusive that Health Connect actually has data
     * for -- drives the Statistics steps chart. Health Connect itself caps how far back a normal
     * read grant can see (its own permission screen says "the app can read new data and data from
     * the past 30 days"), so callers should not ask for a wider range expecting more to come back.
     */
    suspend fun readStepsHistory(start: LocalDate, end: LocalDate): List<DailyStepCount>

    /**
     * Withdraws every Health Connect permission LogEZ holds, the "Disconnect" half of Settings >
     * Data's disconnect-and-delete action. A no-op when Health Connect is not available.
     */
    suspend fun revokeAllPermissions()

    /** The user granted access again after a disconnect in this session; reads may resume. */
    fun onPermissionsRegranted() {}

    /**
     * True after a disconnect in this session: Health Connect only drops the grants when the
     * process ends, so LogEZ treats access as gone until then and says so (2026-09-26).
     */
    val accessEndsOnRestart: Boolean get() = false

    /** The permission string Health Connect uses for reading [type]. */
    fun permissionFor(type: HealthDataType): String
}

/** True when Health Connect is usable and the user has granted [type]. */
suspend fun HealthMetricsSource.canRead(type: HealthDataType): Boolean =
    availability() == HealthConnectAvailability.Available && type in grantedTypes()
