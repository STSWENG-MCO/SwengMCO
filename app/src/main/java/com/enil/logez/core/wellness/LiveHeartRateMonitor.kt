package com.enil.logez.core.wellness

import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.common.Clock
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/** M21f default poll cadence for [liveHeartRateFlow]. */
private const val DEFAULT_POLL_INTERVAL_MS = 10_000L

/**
 * M21f. Polls [healthMetricsSource] for the latest heart-rate sample at [pollIntervalMs] while
 * something is actively collecting -- a **cold** flow, same spine-rule shape as
 * `WorkoutSessionController`'s `elapsedSecondsFlow`/`restRemainingMillisFlow`/
 * `inlineTimerSecondsFlow` (`flow { while(true) {...; delay(...)} }`), not a stateful
 * start()/stop()-lifecycle object. That distinction is load-bearing, not stylistic: an eagerly-
 * started self-ticking coroutine (e.g. kicked off from a ViewModel's `init` block) begins the
 * instant the ViewModel is *constructed* -- including in a plain unit test with no real Compose
 * collector ever watching the value -- which is exactly the "unbounded ticker hangs the test
 * suite" bug `WorkoutLoggerViewModel`'s own doc comment warns about (and which an earlier version
 * of this exact function reproduced: `LiveHeartRateMonitor.start()` called from `init {}` hung
 * `WorkoutLoggerViewModelTest` indefinitely, since nothing in that test ever collects the flow to
 * make it stop). A cold flow sidesteps this entirely: it costs nothing until collected, and
 * cancels itself the instant the collector goes away (a screen leaving composition, or a test that
 * never subscribes at all) -- no explicit lifecycle wiring needed on the ViewModel's part.
 * Silently emits null -- never crashes, never nags -- whenever Health Connect isn't available or
 * the permission isn't granted, matching the Profile card's own graceful-degrade rule.
 *
 * Emits the whole [HeartRateSample], not just the bpm -- see [HealthMetricsSource.readLatestHeartRate]'s
 * own doc comment for why a caller showing this live needs the timestamp, not just the number.
 */
fun liveHeartRateFlow(
    healthMetricsSource: HealthMetricsSource,
    pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    logger: AppLogger = AppLogger.NoOp,
): Flow<HeartRateSample?> = flow {
    while (true) {
        val sample = try {
            if (healthMetricsSource.canRead(HealthDataType.HEART_RATE)) {
                healthMetricsSource.readLatestHeartRate()
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The doc comment above promises this flow "never crashes, never nags" -- that promise
            // was only actually enforced by the availability/permission check, not by anything
            // guarding the read itself. A transient Health Connect IPC failure degrades to this
            // tick emitting null, same as "not granted," instead of terminating the flow.
            logger.e("LiveHeartRateMonitor", "readLatestHeartRate failed; emitting null for this tick", e)
            null
        }
        emit(sample)
        delay(pollIntervalMs)
    }
}

/**
 * The live-tracking screen's historical heart-rate chart: every sample Health Connect has
 * recorded since [startedAtMillis], re-queried in full on each tick rather than accumulated
 * locally -- Health Connect (not this poll's own cadence) is the true record of what a wearable
 * actually captured, and its multi-hop sync can backfill an earlier gap after the fact, which a
 * locally-accumulated list would miss entirely. Same cold-flow, silent-degrade shape as
 * [liveHeartRateFlow] -- see its own doc comment for why that matters for testability.
 */
fun liveHeartRateHistoryFlow(
    healthMetricsSource: HealthMetricsSource,
    startedAtMillis: Long,
    clock: Clock,
    pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
    logger: AppLogger = AppLogger.NoOp,
): Flow<List<HeartRateSample>> = flow {
    while (true) {
        val samples = try {
            if (healthMetricsSource.canRead(HealthDataType.HEART_RATE)) {
                healthMetricsSource.readHeartRateSamples(
                    Instant.ofEpochMilli(startedAtMillis),
                    Instant.ofEpochMilli(clock.now().toEpochMilliseconds()),
                )
            } else {
                emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e("LiveHeartRateMonitor", "readHeartRateSamples failed; emitting empty history for this tick", e)
            emptyList()
        }
        emit(samples)
        delay(pollIntervalMs)
    }
}

/** Whether heart rate can be read at all, so the tracking screen can say why it has none. */
enum class HeartRateAccess {
    /** Health Connect isn't usable on this phone and isn't installable (or its install state is unknown). */
    UNAVAILABLE,

    /** Health Connect is present but needs a Play Store update, or can be installed (Android 9-13). */
    NEEDS_INSTALL_OR_UPDATE,

    /** Health Connect works, but the user hasn't allowed LogEZ to read heart rate. */
    NOT_GRANTED,

    /** Reads are allowed; an empty chart then just means nothing has synced yet. */
    GRANTED,
}

/**
 * The tracking screen's heart-rate access state, re-checked every [pollIntervalMs] so allowing the
 * permission mid-run (from the screen's own button or from Health Connect's settings) shows up
 * without leaving the screen. Before 2026-09-26 "not allowed", "nothing synced yet" and "the read
 * failed" all showed the same "No heart rate yet" message, so a user couldn't tell a missing
 * permission from a watch that simply hadn't synced. Cold, like the flows above.
 */
fun heartRateAccessFlow(
    healthMetricsSource: HealthMetricsSource,
    pollIntervalMs: Long = ACCESS_POLL_INTERVAL_MS,
): Flow<HeartRateAccess> = flow {
    while (true) {
        val availability = healthMetricsSource.availability()
        val access = when {
            availability != HealthConnectAvailability.Available && availability.canInstallOrUpdate() -> HeartRateAccess.NEEDS_INSTALL_OR_UPDATE
            availability != HealthConnectAvailability.Available -> HeartRateAccess.UNAVAILABLE
            HealthDataType.HEART_RATE in healthMetricsSource.grantedTypes() -> HeartRateAccess.GRANTED
            else -> HeartRateAccess.NOT_GRANTED
        }
        emit(access)
        delay(pollIntervalMs)
    }
}.distinctUntilChanged()

private const val ACCESS_POLL_INTERVAL_MS = 15_000L
