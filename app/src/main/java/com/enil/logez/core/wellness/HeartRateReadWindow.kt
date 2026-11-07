package com.enil.logez.core.wellness

import java.time.Duration
import java.time.Instant

/**
 * How LogEZ turns "heart rate between A and B" into a Health Connect query (2026-09-26).
 *
 * Health Connect's `readRecords` matches a time range on each record's **start** time only
 * (`start_time >= start AND start_time < end`, AOSP `RecordHelper.getReadTableWhereClause`; not
 * stated in the public docs), then returns every sample of each matched record. A heart-rate
 * record is a series that can span minutes or a whole watch workout, so asking for exactly A..B
 * silently drops any record that began before A, even when most of its samples fall inside.
 * That is how a run could show no heart rate at all while Health Connect held plenty for it.
 *
 * So the query starts [RECORD_START_LOOKBACK] earlier, and the samples are then kept by their own
 * timestamps with [samplesWithin].
 */
object HeartRateReadWindow {
    /**
     * How far before the wanted window a record may have started and still be fetched. Samsung
     * Health's record lengths aren't documented; three hours covers a watch workout started well
     * before the phone's own session, at the cost of reading a few hours of records per query.
     */
    val RECORD_START_LOOKBACK: Duration = Duration.ofHours(3)

    fun queryStart(start: Instant): Instant = start.minus(RECORD_START_LOOKBACK)

    /** The samples whose own time lies in [start]..[end] inclusive, oldest first. */
    fun samplesWithin(samples: List<HeartRateSample>, start: Instant, end: Instant): List<HeartRateSample> =
        samples.filter { !it.time.isBefore(start) && !it.time.isAfter(end) }.sortedBy { it.time }

    /** One page of a Health Connect heart-rate read: every sample of the page's records, and the next page's token. */
    data class Page(val samples: List<HeartRateSample>, val nextPageToken: String?)

    /** A whole read: the samples in the window, and whether [maxPages] cut it short. */
    data class Result(val samples: List<HeartRateSample>, val truncated: Boolean)

    /**
     * Reads every page for [start]..[end] through [fetch], which receives the widened query start
     * and the page token. The caller asks Health Connect for newest records first, so if
     * [maxPages] ever cuts the read short it drops the oldest look-back records, never the
     * workout's own recent readings.
     */
    suspend fun collect(
        start: Instant,
        end: Instant,
        maxPages: Int,
        fetch: suspend (queryStart: Instant, pageToken: String?) -> Page,
    ): Result {
        if (!end.isAfter(start)) return Result(emptyList(), truncated = false)
        val samples = mutableListOf<HeartRateSample>()
        var token: String? = null
        var pages = 0
        do {
            val page = fetch(queryStart(start), token)
            samples += page.samples
            token = page.nextPageToken
            pages++
        } while (token != null && pages < maxPages)
        return Result(samplesWithin(samples, start, end), truncated = token != null)
    }
}
