package com.enil.logez.core.wellness

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateReadWindowTest {
    private val start = Instant.parse("2026-09-26T06:00:00Z")
    private val end = start.plus(Duration.ofMinutes(30))

    @Test
    fun `the query starts well before the window, so a record begun earlier is still fetched`() {
        assertEquals(start.minus(Duration.ofHours(3)), HeartRateReadWindow.queryStart(start))
    }

    @Test
    fun `samples are kept by their own time, whichever record they came from`() {
        val samples = listOf(
            HeartRateSample(start.minusSeconds(60), 100), // from a record begun before the run: dropped
            HeartRateSample(start, 110), // the boundary itself is inside
            HeartRateSample(start.plusSeconds(600), 140),
            HeartRateSample(end, 150),
            HeartRateSample(end.plusSeconds(1), 90), // after the run: dropped
        )
        assertEquals(listOf(110L, 140L, 150L), HeartRateReadWindow.samplesWithin(samples.shuffled(), start, end).map { it.bpm })
    }

    /**
     * Stands in for Health Connect: returns only records whose START falls in the query range,
     * newest first, [pageSize] records per page, which is how AOSP's RecordHelper filters.
     */
    private class FakeHealthConnect(private val records: List<Pair<Instant, List<HeartRateSample>>>, private val end: Instant, private val pageSize: Int) {
        var pagesServed = 0
        fun page(queryStart: Instant, token: String?): HeartRateReadWindow.Page {
            pagesServed++
            val matching = records.filter { (recordStart, _) -> !recordStart.isBefore(queryStart) && recordStart.isBefore(end) }
                .sortedByDescending { it.first }
            val from = token?.toInt() ?: 0
            val slice = matching.drop(from).take(pageSize)
            val next = if (from + pageSize < matching.size) (from + pageSize).toString() else null
            return HeartRateReadWindow.Page(slice.flatMap { it.second }, next)
        }
    }

    @Test
    fun `a watch record that began before the run still gives its in-run readings`() = kotlinx.coroutines.test.runTest {
        // One record from 10 minutes before the run to 5 minutes into it, as a watch workout started early writes.
        val early = start.minus(Duration.ofMinutes(10))
        val record = early to (0..29).map { HeartRateSample(early.plusSeconds(it * 30L), 120L + it) }
        val hc = FakeHealthConnect(listOf(record), end, pageSize = 10)

        val result = HeartRateReadWindow.collect(start, end, maxPages = 10, fetch = hc::page)

        // Samples at 0..30 s steps from early: the ones from `start` on are 10 minutes in (index 20+).
        assertEquals((20..29).map { 120L + it }, result.samples.map { it.bpm })
        // Asking for exactly start..end, as the old code did, would have matched no record at all.
        assertEquals(0, FakeHealthConnect(listOf(record), end, 10).page(start, null).samples.size)
    }

    @Test
    fun `every page is read, and a page cap drops the oldest look-back records, not the run's`() = kotlinx.coroutines.test.runTest {
        val records = (0 until 6).map { i ->
            val recordStart = start.minus(Duration.ofMinutes(150)).plus(Duration.ofMinutes(i * 30L))
            recordStart to listOf(HeartRateSample(recordStart.plusSeconds(10), 100L + i))
        }
        val all = FakeHealthConnect(records, end, pageSize = 2)
        val full = HeartRateReadWindow.collect(start.minus(Duration.ofMinutes(150)), end, maxPages = 10, fetch = all::page)
        assertEquals(3, all.pagesServed)
        assertEquals(false, full.truncated)
        assertEquals(6, full.samples.size)

        val capped = HeartRateReadWindow.collect(start.minus(Duration.ofMinutes(150)), end, maxPages = 1, fetch = FakeHealthConnect(records, end, pageSize = 2)::page)
        assertEquals(true, capped.truncated)
        assertEquals(listOf(104L, 105L), capped.samples.map { it.bpm }) // the two newest records survive
    }
}
