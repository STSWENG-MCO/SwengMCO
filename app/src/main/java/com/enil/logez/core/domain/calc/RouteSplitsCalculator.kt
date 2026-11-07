package com.enil.logez.core.domain.calc

import com.enil.logez.core.common.GeoDistance
import com.enil.logez.core.domain.model.DistanceUnit
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * One per-km or per-mile split of a tracked walk/run.
 *
 * @property number 1-based; the partial split at the end carries the next number.
 * @property distanceMeters a full unit, or what was left after the last full one.
 * @property averageBpm mean of the heart-rate samples inside the split, or null with none.
 */
data class RouteSplit(
    val number: Int,
    val distanceMeters: Double,
    val durationSeconds: Int,
    val paceSecondsPerUnit: Double,
    val isPartial: Boolean,
    val averageBpm: Long?,
)

/**
 * Splits and a pace-over-time series, rebuilt after the fact from a saved route and the time each
 * of its points was recorded (`activity_tracks.route_times`, saved from 2026-09-26 on).
 *
 * Distance is re-measured along the decoded route, then scaled to the distance the tracker saved.
 * The saved route is rounded to about a metre per point, and on a walk's short hops that rounding
 * adds roughly 0.5% of length; unscaled, a walk shown as 4.98 km could list five full km splits.
 * Time runs from the first accepted GPS fix, which is also where the distance starts counting.
 */
object RouteSplitsCalculator {
    /** A trailing piece shorter than this is left out rather than shown as a "0.01 km" split. */
    const val MIN_PARTIAL_SPLIT_METERS = 50.0

    /** The pace series: each point is the pace over this much time up to it, which smooths GPS jitter. */
    const val PACE_WINDOW_SECONDS = 60

    /** How often the pace series takes a point. */
    const val PACE_STEP_SECONDS = 15

    /** A window covering less ground than this (standing at a crossing) gets no pace point. */
    const val MIN_PACE_WINDOW_METERS = 20.0

    /**
     * Cumulative meters at each point, [points] being (lat, lng), scaled so the total equals
     * [trackedDistanceMeters] when it is given and positive (see the class doc).
     */
    fun cumulativeMeters(points: List<Pair<Double, Double>>, trackedDistanceMeters: Double? = null): DoubleArray {
        val cumulative = DoubleArray(points.size)
        for (i in 1 until points.size) {
            val (lat1, lng1) = points[i - 1]
            val (lat2, lng2) = points[i]
            cumulative[i] = cumulative[i - 1] + GeoDistance.metersBetween(lat1, lng1, lat2, lng2)
        }
        val measured = cumulative.lastOrNull() ?: 0.0
        if (trackedDistanceMeters != null && trackedDistanceMeters > 0.0 && measured > 0.0) {
            val scale = trackedDistanceMeters / measured
            for (i in cumulative.indices) cumulative[i] *= scale
        }
        return cumulative
    }

    /** Empty unless [timesSeconds] has one entry per point and there are at least two points. */
    fun splits(
        points: List<Pair<Double, Double>>,
        timesSeconds: List<Int>,
        unit: DistanceUnit,
        startedAtMillis: Long,
        heartRate: List<Pair<Long, Long>> = emptyList(),
        trackedDistanceMeters: Double? = null,
    ): List<RouteSplit> {
        if (points.size < 2 || timesSeconds.size != points.size) return emptyList()
        val times = monotonic(timesSeconds)
        val cumulative = cumulativeMeters(points, trackedDistanceMeters)
        val unitMeters = DistanceDisplay.unitMeters(unit)
        val total = cumulative.last()

        val result = mutableListOf<RouteSplit>()
        var startDistance = 0.0
        var startTime = times.first().toDouble()
        var number = 1
        while (startDistance + unitMeters <= total) {
            val boundary = startDistance + unitMeters
            val boundaryTime = timeAtDistance(cumulative, times, boundary)
            result += split(number, unitMeters, startTime, boundaryTime, unitMeters, isPartial = false, startedAtMillis, heartRate)
            startDistance = boundary
            startTime = boundaryTime
            number++
        }
        val remaining = total - startDistance
        val endTime = times.last().toDouble()
        if (remaining >= MIN_PARTIAL_SPLIT_METERS && endTime > startTime) {
            result += split(number, remaining, startTime, endTime, unitMeters, isPartial = true, startedAtMillis, heartRate)
        }
        return result
    }

    /**
     * (epochMillis, seconds per unit) every [PACE_STEP_SECONDS], each the pace over the preceding
     * [PACE_WINDOW_SECONDS]. Empty when fewer than two points would result, so callers never draw
     * a one-point chart.
     */
    fun paceSeries(
        points: List<Pair<Double, Double>>,
        timesSeconds: List<Int>,
        unit: DistanceUnit,
        startedAtMillis: Long,
        trackedDistanceMeters: Double? = null,
    ): List<Pair<Long, Double>> {
        if (points.size < 2 || timesSeconds.size != points.size) return emptyList()
        val times = monotonic(timesSeconds)
        val cumulative = cumulativeMeters(points, trackedDistanceMeters)
        val unitMeters = DistanceDisplay.unitMeters(unit)
        val series = mutableListOf<Pair<Long, Double>>()
        var t = times.first() + PACE_WINDOW_SECONDS
        while (t <= times.last()) {
            val covered = distanceAtTime(cumulative, times, t.toDouble()) -
                distanceAtTime(cumulative, times, (t - PACE_WINDOW_SECONDS).toDouble())
            if (covered >= MIN_PACE_WINDOW_METERS) {
                series += (startedAtMillis + t * 1000L) to PACE_WINDOW_SECONDS / (covered / unitMeters)
            }
            t += PACE_STEP_SECONDS
        }
        return if (series.size >= 2) series else emptyList()
    }

    private fun split(
        number: Int,
        distanceMeters: Double,
        startTime: Double,
        endTime: Double,
        unitMeters: Double,
        isPartial: Boolean,
        startedAtMillis: Long,
        heartRate: List<Pair<Long, Long>>,
    ): RouteSplit {
        val seconds = endTime - startTime
        val fromMillis = startedAtMillis + (startTime * 1000).roundToLong()
        val toMillis = startedAtMillis + (endTime * 1000).roundToLong()
        val bpm = heartRate.filter { it.first in fromMillis until toMillis }.map { it.second }
        return RouteSplit(
            number = number,
            distanceMeters = distanceMeters,
            durationSeconds = seconds.roundToInt(),
            paceSecondsPerUnit = seconds / (distanceMeters / unitMeters),
            isPartial = isPartial,
            averageBpm = bpm.takeIf { it.isNotEmpty() }?.average()?.roundToLong(),
        )
    }

    /** Times should already only grow; a clock step backwards is flattened rather than trusted. */
    private fun monotonic(times: List<Int>): List<Int> {
        var highest = Int.MIN_VALUE
        return times.map { t -> maxOf(t, highest).also { highest = it } }
    }

    private fun timeAtDistance(cumulative: DoubleArray, times: List<Int>, meters: Double): Double {
        val i = (1 until cumulative.size).firstOrNull { cumulative[it] >= meters } ?: return times.last().toDouble()
        val span = cumulative[i] - cumulative[i - 1]
        val fraction = if (span > 0.0) (meters - cumulative[i - 1]) / span else 1.0
        return times[i - 1] + fraction * (times[i] - times[i - 1])
    }

    private fun distanceAtTime(cumulative: DoubleArray, times: List<Int>, seconds: Double): Double {
        if (seconds <= times.first()) return 0.0
        if (seconds >= times.last()) return cumulative.last()
        val i = (1 until times.size).first { times[it] >= seconds }
        val span = (times[i] - times[i - 1]).toDouble()
        val fraction = if (span > 0.0) (seconds - times[i - 1]) / span else 1.0
        return cumulative[i - 1] + fraction * (cumulative[i] - cumulative[i - 1])
    }
}
