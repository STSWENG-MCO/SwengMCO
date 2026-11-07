package com.enil.logez.core.wellness

import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity
import com.enil.logez.core.domain.repository.WorkoutHeartRateSampleRepository
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Copies a finished workout's heart rate from Health Connect into LogEZ's own table, adding only
 * readings it doesn't already hold (2026-09-26).
 *
 * A Galaxy Watch's continuous heart rate reaches Health Connect late: Samsung Health syncs it when
 * the watch reconnects or its home screen is opened, not as it is measured. Heart rate used to be
 * read exactly once, when the user tapped Save, so a run saved before that sync never got any.
 * This runs at Save and again whenever the workout's summary or History detail comes back to the
 * foreground, so readings that sync later are added then. Foreground only: Health Connect refuses
 * background reads of another app's data without an extra permission LogEZ doesn't hold.
 *
 * Never removes anything. A later read that returns less (data deleted in Health Connect, or
 * access withdrawn) leaves the saved readings as they are.
 */
class WorkoutHeartRateBackfill @Inject constructor(
    private val healthMetricsSource: HealthMetricsSource,
    private val heartRateSampleRepository: WorkoutHeartRateSampleRepository,
    private val logger: AppLogger,
) {
    /** How many new readings were saved; 0 when there was nothing new, no access, or a failure. */
    suspend fun backfill(workoutId: String, startedAtMillis: Long, endedAtMillis: Long): Int {
        if (endedAtMillis <= startedAtMillis) return 0
        return try {
            if (!healthMetricsSource.canRead(HealthDataType.HEART_RATE)) return 0
            val read = healthMetricsSource.readHeartRateSamples(Instant.ofEpochMilli(startedAtMillis), Instant.ofEpochMilli(endedAtMillis))
            if (read.isEmpty()) return 0
            val known = heartRateSampleRepository.getForWorkout(workoutId).map { it.recordedAt to it.bpm }.toSet()
            val fresh = read.map { it.time.toEpochMilli() to it.bpm }.distinct().filterNot { it in known }
            if (fresh.isEmpty()) return 0
            heartRateSampleRepository.insertAll(
                fresh.map { (recordedAt, bpm) ->
                    WorkoutHeartRateSampleEntity(id = UUID.randomUUID().toString(), workoutId = workoutId, recordedAt = recordedAt, bpm = bpm)
                },
            )
            fresh.size
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort: a Health Connect or Room failure here must never break the screen or the
            // save that triggered it.
            logger.e(TAG, "Heart-rate backfill failed for workout $workoutId", e)
            0
        }
    }

    private companion object {
        const val TAG = "WorkoutHeartRateBackfill"
    }
}
