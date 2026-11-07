package com.enil.logez.core.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateSummaryCalculatorTest {
    private val start = 1_000_000L
    private val end = start + 600_000L // a 10-minute workout

    @Test
    fun `no sample inside the window means no summary`() {
        assertNull(HeartRateSummaryCalculator.summarize(emptyList(), start, end, 190))
        assertNull(HeartRateSummaryCalculator.summarize(listOf((start - 1) to 120L, (end + 1) to 150L), start, end, 190))
    }

    @Test
    fun `samples outside the window are dropped before anything is computed`() {
        val summary = HeartRateSummaryCalculator.summarize(
            listOf((start - 30_000) to 180L, start to 120L, (start + 60_000) to 130L, (end + 30_000) to 190L),
            start, end, 190,
        )!!
        assertEquals(130L, summary.maxBpm) // the 180 and 190 fell outside
        assertEquals(listOf(start to 120L, (start + 60_000) to 130L), summary.samples)
    }

    @Test
    fun `the average is weighted by time, so a burst of readings can't outweigh a long steady stretch`() {
        // 100 bpm held for 5 minutes (one reading), then a burst of 160s in the last 5 minutes.
        val samples = listOf(start to 100L) + (0 until 10).map { (start + 300_000 + it * 30_000L) to 160L }
        val summary = HeartRateSummaryCalculator.summarize(samples, start, end, null)!!
        // The 100 counts for only 2 minutes (the gap cap), the 160s for 5: (100*120 + 160*300) / 420.
        assertEquals(143L, summary.averageBpm)
        assertEquals(160L, summary.maxBpm)
    }

    @Test
    fun `two sources reporting the same instant count once, averaged`() {
        val summary = HeartRateSummaryCalculator.summarize(listOf(start to 120L, start to 130L), start, end, null)!!
        assertEquals(listOf(start to 125L), summary.samples)
    }

    @Test
    fun `zone time follows each sample's zone, and every zone is present`() {
        // max 200: 110 is zone 1 (<60%), 150 zone 3 (70-80%), 185 zone 5 (>=90%).
        val samples = listOf(start to 110L, (start + 60_000) to 150L, (start + 180_000) to 185L)
        val zones = HeartRateSummaryCalculator.summarize(samples, start, start + 240_000, 200)!!.zoneSeconds!!
        assertEquals(60, zones[HeartRateZone.ZONE_1])
        assertEquals(0, zones[HeartRateZone.ZONE_2])
        assertEquals(120, zones[HeartRateZone.ZONE_3])
        assertEquals(0, zones[HeartRateZone.ZONE_4])
        assertEquals(60, zones[HeartRateZone.ZONE_5])
    }

    @Test
    fun `without a max heart rate there are no zones, but average and max still show`() {
        val summary = HeartRateSummaryCalculator.summarize(listOf(start to 120L, (start + 60_000) to 140L), start, end, null)!!
        assertNull(summary.zoneSeconds)
        assertNull(summary.maxHeartRateSetting)
        assertEquals(140L, summary.maxBpm)
    }
}
