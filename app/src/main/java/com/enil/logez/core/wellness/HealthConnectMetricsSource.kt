package com.enil.logez.core.wellness

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import javax.inject.Inject
import com.enil.logez.core.common.AppLogger
import kotlinx.coroutines.CancellationException

/**
 * M21e (steps + calories) + M21f (heart rate). Wraps `HealthConnectClient`, the same
 * local-IPC-to-an-already-installed-app shape as `FusedLocationSource` -- confirmed via direct
 * AAR/manifest inspection (decisions.md 2026-09-10), not assumed from the library's category. Uses
 * real wall-clock time directly (`LocalDate.now()`/`Instant.now()`), not the injected `Clock` --
 * same exemption as `FusedLocationSource`'s `SystemClock` use: this is a thin framework-boundary
 * wrapper around a live external data source, not testable business logic, so it isn't unit-tested
 * directly (see `FakeHealthMetricsSource` for what drives ViewModel/controller tests).
 *
 * Calories root-caused and fixed 2026-09-11 (M21g), after M21e originally cut it from scope. The
 * real bug: `Energy.kilocalories`/`.getKilocalories()` genuinely exist in the connect-client 1.1.0
 * AAR's bytecode -- `javap` shows a clean, unmangled, public `getKilocalories()` -- but that member
 * is a deprecated-hidden legacy alias, invisible to Kotlin *source* resolution even though it's
 * still callable from raw bytecode/Java; Kotlin's compiler reads visibility from the artifact's own
 * `@Metadata` annotation, not the JVM access flags, so `javap` alone can't tell public-and-live apart
 * from public-bytecode-but-hidden. The real, currently-resolvable accessor is `inKilocalories`
 * (matching this library's `inXxx` convention used by its other unit-wrapper classes). Confirmed
 * empirically, not guessed: `.kilocalories` reproduced the exact "unresolved reference" failure
 * against this same real AAR, `.inKilocalories` compiled clean on the first try.
 */
class HealthConnectMetricsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) : HealthMetricsSource {
    private val permissionByType: Map<HealthDataType, String> = mapOf(
        HealthDataType.STEPS to HealthPermission.getReadPermission(StepsRecord::class),
        HealthDataType.HEART_RATE to HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthDataType.CALORIES to HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    override val requiredPermissions: Set<String> = permissionByType.values.toSet()

    /**
     * Set once [revokeAllPermissions] succeeds. On Android 14+ Health Connect applies that revoke
     * through Context.revokeSelfPermissionsOnKill, so the grants only disappear when this process
     * ends (confirmed on an Android 15 emulator, 2026-09-25: still granted after the call,
     * revoked after a force-stop). Without this flag the Profile and Workout tabs kept reading
     * steps, and re-filled the cache the user had just deleted, until the app was next closed.
     * This source is a singleton, so the flag covers every reader for the rest of the process.
     * It is set before the revoke is attempted, so a revoke Health Connect refuses still stops
     * the reads for this session, and cleared by [onPermissionsRegranted] when the user connects
     * again (it used to block a same-session reconnect until restart, 2026-09-25 review).
     */
    @Volatile private var revokedInThisProcess = false

    override fun availability(): HealthConnectAvailability =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UpdateRequired
            else -> HealthConnectAvailability.Unavailable
        }

    // Health Connect calls can throw while its app updates or when a grant is withdrawn mid-read.
    // Every caller runs these from a bare viewModelScope.launch, where an escaped exception kills
    // the process, so each read answers "nothing to show" instead (2026-09-25 review).
    override suspend fun grantedTypes(): Set<HealthDataType> {
        if (revokedInThisProcess) return emptySet()
        if (availability() != HealthConnectAvailability.Available) return emptySet()
        val granted = safely("grantedTypes", emptySet()) { client().permissionController.getGrantedPermissions() }
        return permissionByType.filterValues { it in granted }.keys
    }

    override fun onPermissionsRegranted() {
        revokedInThisProcess = false
    }

    override val accessEndsOnRestart: Boolean get() = revokedInThisProcess

    override fun permissionFor(type: HealthDataType): String = permissionByType.getValue(type)

    private suspend fun <T> safely(what: String, fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.e(TAG, "Health Connect $what failed; showing nothing for it", e)
        fallback
    }

    override suspend fun readTodayTotals(): DailyTotals {
        val granted = grantedTypes()
        val metrics = buildSet {
            if (HealthDataType.STEPS in granted) add(StepsRecord.COUNT_TOTAL)
            if (HealthDataType.CALORIES in granted) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
        }
        if (metrics.isEmpty()) return DailyTotals(steps = 0L, caloriesBurned = null)
        val startOfToday = LocalDate.now().atStartOfDay()
        val result = safely("readTodayTotals", null) {
            client().aggregate(
                AggregateRequest(
                    metrics = metrics,
                    timeRangeFilter = TimeRangeFilter.between(startOfToday, LocalDateTime.now()),
                ),
            )
        } ?: return DailyTotals(steps = 0L, caloriesBurned = null)
        val steps: Long = if (StepsRecord.COUNT_TOTAL in metrics) result.get(StepsRecord.COUNT_TOTAL) ?: 0L else 0L
        val caloriesBurned: Double? =
            if (TotalCaloriesBurnedRecord.ENERGY_TOTAL in metrics) result.get(TotalCaloriesBurnedRecord.ENERGY_TOTAL)?.inKilocalories else null
        return DailyTotals(steps = steps, caloriesBurned = caloriesBurned)
    }

    /**
     * One small page of the newest records is enough for "the latest reading"; the strength
     * logger polls this every 10 seconds, so it doesn't page through the whole look-back.
     */
    override suspend fun readLatestHeartRate(withinSeconds: Long): HeartRateSample? {
        val now = Instant.now()
        val from = now.minusSeconds(withinSeconds)
        return HeartRateReadWindow.collect(from, now, maxPages = 1) { queryStart, _ ->
            fetchHeartRatePage(queryStart, now, pageToken = null, pageSize = LATEST_PAGE_SIZE)
        }.samples.lastOrNull()
    }

    /**
     * Records are fetched from [HeartRateReadWindow.queryStart] and kept by each sample's own time,
     * because Health Connect matches a range on record start time only (see [HeartRateReadWindow]).
     * Every page is read, up to [MAX_HEART_RATE_PAGES]; a single page used to be the whole answer.
     */
    override suspend fun readHeartRateSamples(start: Instant, end: Instant): List<HeartRateSample> {
        val result = HeartRateReadWindow.collect(start, end, MAX_HEART_RATE_PAGES) { queryStart, pageToken ->
            fetchHeartRatePage(queryStart, end, pageToken, pageSize = FULL_PAGE_SIZE)
        }
        if (result.truncated) {
            logger.e(TAG, "Heart-rate read for $start..$end stopped at $MAX_HEART_RATE_PAGES pages; oldest look-back records skipped", null)
        }
        return result.samples
    }

    /** Newest records first, so a page cap can only ever drop the oldest look-back records. */
    private suspend fun fetchHeartRatePage(queryStart: Instant, end: Instant, pageToken: String?, pageSize: Int): HeartRateReadWindow.Page {
        val response = client().readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(queryStart, end),
                ascendingOrder = false,
                pageSize = pageSize,
                pageToken = pageToken,
            ),
        )
        // HeartRateRecord.Sample.beatsPerMinute is a plain Long, not a units-wrapper class like
        // Energy -- confirmed via the same javap inspection that caught the calories blocker, so
        // this path doesn't carry the same interop risk.
        val samples = response.records.flatMap { record -> record.samples.map { HeartRateSample(time = it.time, bpm = it.beatsPerMinute) } }
        return HeartRateReadWindow.Page(samples, response.pageToken)
    }

    override suspend fun readStepsHistory(start: LocalDate, end: LocalDate): List<DailyStepCount> {
        val response = safely("readStepsHistory", emptyList()) {
            client().aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(start.atStartOfDay(), end.plusDays(1).atStartOfDay()),
                    timeRangeSlicer = Period.ofDays(1),
                ),
            )
        }
        return response.map { bucket ->
            DailyStepCount(date = bucket.startTime.toLocalDate(), steps = bucket.result.get(StepsRecord.COUNT_TOTAL) ?: 0L)
        }
    }

    override suspend fun revokeAllPermissions() {
        if (availability() != HealthConnectAvailability.Available) return
        revokedInThisProcess = true
        client().permissionController.revokeAllPermissions()
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    private companion object {
        const val TAG = "HealthConnectMetricsSource"

        /**
         * Up to 10,000 records across the three-hour look-back plus the workout; far more than a
         * watch writes (Samsung Health writes a few dozen series records per hour). Reads come
         * newest first, so hitting it would only lose the oldest look-back records, and is logged.
         */
        const val MAX_HEART_RATE_PAGES = 10
        const val FULL_PAGE_SIZE = 1000
        const val LATEST_PAGE_SIZE = 50
    }
}
