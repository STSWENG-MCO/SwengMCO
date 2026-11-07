package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.DistanceUnit

/**
 * Live pace for the GPS activity-tracking screen -- minutes:seconds per km/mile, the
 * distance-tracking convention (as opposed to speed) every mainstream running app shows.
 */
object PaceCalculator {
    /** Meters below this render no pace at all (an honest "not enough data yet" rather than a
     * wildly noisy number over the first few GPS fixes -- same threshold-gated-absence rule as
     * every other Health Connect/GPS-dependent stat in this app). */
    private const val MIN_METERS_FOR_PACE = 50.0

    /** Seconds-per-unit-distance, or null before [MIN_METERS_FOR_PACE] has accumulated. */
    fun paceSecondsPerUnit(distanceMeters: Double, elapsedSeconds: Int, unit: DistanceUnit): Double? {
        if (distanceMeters < MIN_METERS_FOR_PACE) return null
        return elapsedSeconds / (distanceMeters / DistanceDisplay.unitMeters(unit))
    }

    /**
     * Meters below this render no windowed pace point at all. Deliberately much smaller than
     * [MIN_METERS_FOR_PACE]: that constant gates the *whole session's* cumulative distance before
     * showing a live pace number, but [windowedPaceSecondsPerUnit] is called on a single short
     * (tens-of-seconds) segment of a pace-history chart. Reusing the 50m cumulative floor there
     * silently dropped almost every real point -- 50m in a 15-second window needs >=12 km/h,
     * faster than typical walking (~5 km/h) and most jogging (~10 km/h) -- so the chart stayed
     * empty for the exact walk/run use case it exists for (adversarial review, 2026-09-22).
     */
    private const val MIN_METERS_FOR_WINDOWED_PACE = 8.0

    /**
     * Same math as [paceSecondsPerUnit], for one short delta window (e.g. between two consecutive
     * samples on a pace-history chart) rather than the session's cumulative total -- see
     * [MIN_METERS_FOR_WINDOWED_PACE]'s own doc comment for why the two need different minimums.
     */
    fun windowedPaceSecondsPerUnit(deltaMeters: Double, deltaSeconds: Int, unit: DistanceUnit): Double? {
        if (deltaMeters < MIN_METERS_FOR_WINDOWED_PACE || deltaSeconds <= 0) return null
        return deltaSeconds / (deltaMeters / DistanceDisplay.unitMeters(unit))
    }
}
