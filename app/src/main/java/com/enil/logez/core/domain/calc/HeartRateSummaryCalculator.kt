package com.enil.logez.core.domain.calc

import kotlin.math.roundToLong

/**
 * Average / maximum heart rate and time in each zone for one finished workout, from the
 * Health Connect samples `WorkoutFinisher` saved for it.
 *
 * @property samples the samples actually used: inside the workout's window, one per timestamp,
 *   oldest first. The summary's heart-rate chart draws exactly these, so the chart and the
 *   numbers above it can't disagree.
 * @property zoneSeconds seconds credited to each zone, every zone present (0 when none), or null
 *   when no max heart rate is set in Settings. There is no age setting to estimate one from.
 */
data class HeartRateSummary(
    val averageBpm: Long,
    val maxBpm: Long,
    val samples: List<Pair<Long, Long>>,
    val zoneSeconds: Map<HeartRateZone, Int>?,
    val maxHeartRateSetting: Int?,
)

object HeartRateSummaryCalculator {
    /**
     * The most one sample can stand for. A watch syncs unevenly, so a sample can be followed by
     * a long gap; without a cap, one reading taken just before the watch stopped syncing would
     * be credited with every minute until the next one, skewing the average and the zones.
     */
    const val MAX_CREDITED_GAP_MILLIS = 120_000L

    /**
     * Null when no sample falls inside [windowStartMillis]..[windowEndMillis]. Readings saved
     * before 2026-09-26 came from whole Health Connect records, whose samples can run past either
     * end of the workout; any outside the window are dropped here. Two sources reporting the same
     * instant are averaged into one sample.
     *
     * The average is weighted by time: each sample counts for the time until the next one (or
     * the window's end), capped at [MAX_CREDITED_GAP_MILLIS]. A plain mean would let a burst of
     * closely spaced readings outweigh a long, steady stretch.
     */
    fun summarize(
        samples: List<Pair<Long, Long>>,
        windowStartMillis: Long,
        windowEndMillis: Long,
        maxHeartRateBpm: Int?,
    ): HeartRateSummary? {
        val inWindow = samples
            .filter { (recordedAt, bpm) -> recordedAt in windowStartMillis..windowEndMillis && bpm > 0 }
            .groupBy { it.first }
            .map { (recordedAt, same) -> recordedAt to same.map { it.second }.average().roundToLong() }
            .sortedBy { it.first }
        if (inWindow.isEmpty()) return null

        val weights = inWindow.indices.map { i ->
            val next = if (i < inWindow.lastIndex) inWindow[i + 1].first else windowEndMillis
            (next - inWindow[i].first).coerceIn(0L, MAX_CREDITED_GAP_MILLIS)
        }
        val totalWeight = weights.sum()
        val average = if (totalWeight > 0L) {
            inWindow.indices.sumOf { inWindow[it].second.toDouble() * weights[it] } / totalWeight
        } else {
            inWindow.map { it.second }.average()
        }

        val zoneSeconds = if (maxHeartRateBpm != null && maxHeartRateBpm > 0) {
            val millis = HeartRateZone.entries.associateWith { 0L }.toMutableMap()
            inWindow.forEachIndexed { i, (_, bpm) ->
                val zone = HeartRateZoneCalculator.zoneFor(bpm, maxHeartRateBpm) ?: return@forEachIndexed
                millis[zone] = millis.getValue(zone) + weights[i]
            }
            millis.mapValues { (_, ms) -> (ms / 1000.0).roundToLong().toInt() }
        } else {
            null
        }

        return HeartRateSummary(
            averageBpm = average.roundToLong(),
            maxBpm = inWindow.maxOf { it.second },
            samples = inWindow,
            zoneSeconds = zoneSeconds,
            maxHeartRateSetting = maxHeartRateBpm?.takeIf { it > 0 },
        )
    }
}
