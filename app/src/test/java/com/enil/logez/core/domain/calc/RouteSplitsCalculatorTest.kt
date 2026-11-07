package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.DistanceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSplitsCalculatorTest {
    private val startedAt = 10_000_000L

    /** Points due north every 100 m along a meridian, so distances are exact enough to reason about. */
    private fun northbound(count: Int): List<Pair<Double, Double>> {
        val degreesPer100m = 100.0 / 111_195.0 // GeoDistance's mean-earth-radius metres per degree
        return (0 until count).map { (14.6 + it * degreesPer100m) to 121.06 }
    }

    @Test
    fun `a run of 2_5 km gives two full km splits and a partial, with their own paces`() {
        val points = northbound(26) // 0 to 2,500 m
        // 6:00/km for the first km (36 s per 100 m), then 5:00/km (30 s per 100 m).
        val times = points.indices.map { i -> if (i <= 10) i * 36 else 360 + (i - 10) * 30 }
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt)

        assertEquals(3, splits.size)
        assertEquals(360, splits[0].durationSeconds)
        assertEquals(360.0, splits[0].paceSecondsPerUnit, 1.0)
        assertEquals(300, splits[1].durationSeconds)
        assertFalse(splits[1].isPartial)
        assertTrue(splits[2].isPartial)
        assertEquals(500.0, splits[2].distanceMeters, 2.0)
        assertEquals(150, splits[2].durationSeconds)
        assertEquals(300.0, splits[2].paceSecondsPerUnit, 2.0)
    }

    @Test
    fun `a trailing piece under 50 m is left out rather than shown as a tiny split`() {
        val points = northbound(11) + ((14.6 + 1030.0 / 111_195.0) to 121.06) // 1,030 m
        val times = points.indices.map { it * 30 }
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt)
        assertEquals(1, splits.size)
        assertFalse(splits[0].isPartial)
    }

    @Test
    fun `mile splits use 1609 m`() {
        val points = northbound(34) // 3,300 m, just over two miles
        val times = points.indices.map { it * 30 }
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.MILES, startedAt)
        assertEquals(3, splits.size) // two full miles and a partial of ~81 m
        assertEquals(483, splits[0].durationSeconds) // 1609.344 m at 30 s per 100 m
    }

    @Test
    fun `each split's heart rate averages only the samples inside it`() {
        val points = northbound(21)
        val times = points.indices.map { it * 30 } // 300 s per km
        val heartRate = listOf(
            (startedAt + 100_000) to 140L, (startedAt + 200_000) to 150L, // first km
            (startedAt + 400_000) to 170L, // second km
        )
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt, heartRate)
        assertEquals(145L, splits[0].averageBpm)
        assertEquals(170L, splits[1].averageBpm)
    }

    @Test
    fun `no splits without a time for every point, as for a run tracked before times were saved`() {
        val points = northbound(21)
        assertTrue(RouteSplitsCalculator.splits(points, emptyList(), DistanceUnit.KM, startedAt).isEmpty())
        assertTrue(RouteSplitsCalculator.splits(points, listOf(0, 30), DistanceUnit.KM, startedAt).isEmpty())
        assertTrue(RouteSplitsCalculator.paceSeries(points, emptyList(), DistanceUnit.KM, startedAt).isEmpty())
    }

    @Test
    fun `the pace series is the pace over the preceding minute, timestamped from the workout start`() {
        val points = northbound(21)
        val times = points.indices.map { it * 30 } // steady 5:00/km
        val series = RouteSplitsCalculator.paceSeries(points, times, DistanceUnit.KM, startedAt)
        assertTrue(series.size >= 2)
        assertEquals(startedAt + 60_000L, series.first().first)
        series.forEach { (_, pace) -> assertEquals(300.0, pace, 1.0) }
    }

    @Test
    fun `standing still produces no pace point for that minute`() {
        val points = northbound(3) + northbound(3).last() + northbound(3).last()
        val times = listOf(0, 30, 60, 120, 180) // 200 m, then a two-minute stop
        val series = RouteSplitsCalculator.paceSeries(points, times, DistanceUnit.KM, startedAt)
        assertTrue(series.none { it.first == startedAt + 180_000L })
    }

    @Test
    fun `a partial split has no heart rate when no sample falls in it`() {
        val points = northbound(16)
        val times = points.indices.map { it * 30 }
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt, listOf((startedAt + 10_000) to 120L))
        assertNull(splits.last().averageBpm)
    }

    @Test
    fun `splits are scaled to the distance the tracker saved, so they sum to the headline distance`() {
        val points = northbound(21) // measures 2,000 m
        val times = points.indices.map { it * 30 }
        // The tracker saved 1,990 m (the rounded route measured 0.5% long): two splits become one full and a partial.
        val splits = RouteSplitsCalculator.splits(points, times, DistanceUnit.KM, startedAt, trackedDistanceMeters = 1_990.0)
        assertEquals(2, splits.size)
        assertTrue(splits[1].isPartial)
        assertEquals(990.0, splits[1].distanceMeters, 1.0)
        assertEquals(1_990.0, RouteSplitsCalculator.cumulativeMeters(points, 1_990.0).last(), 1e-6)
    }
}
