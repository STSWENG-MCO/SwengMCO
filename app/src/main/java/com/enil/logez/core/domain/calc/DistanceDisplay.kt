package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.DistanceUnit

/**
 * The single meters <-> display-unit boundary for distance values, mirroring [WeightDisplay]'s
 * kg <-> display-unit convention. Storage/calculation is always canonical meters; these helpers
 * convert exactly once at the display/UI edge. Consolidated here (2026-09-19 debt audit) after the
 * meters-per-mile constant was independently re-declared in PaceCalculator, PreviousValueFormatter,
 * and SummaryFormatters.
 */
object DistanceDisplay {
    const val METERS_PER_KM = 1000.0

    /** International mile, exact by definition. */
    const val METERS_PER_MILE = 1609.344

    fun unitMeters(unit: DistanceUnit): Double = if (unit == DistanceUnit.MILES) METERS_PER_MILE else METERS_PER_KM

    fun toDisplay(meters: Double, unit: DistanceUnit): Double = meters / unitMeters(unit)
}
