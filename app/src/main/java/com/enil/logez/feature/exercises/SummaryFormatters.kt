package com.enil.logez.feature.exercises

import com.enil.logez.core.domain.calc.ChartMetric
import com.enil.logez.core.domain.calc.DistanceDisplay
import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.WeightUnit

/**
 * Display formatting for the Summary tab's chart values and Personal Records list (§8.9: math on
 * raw kg/seconds/meters, "display-rounded to 1 decimal after unit conversion"; pace displayed
 * `m:ss /km` or `/mi` per DistanceUnit). Trailing ".0" is trimmed, matching the formatters the
 * Workout Detail and PREVIOUS-column surfaces already use.
 */
object SummaryFormatters {
    fun formatMetricValue(metric: ChartMetric, raw: Double, weightUnit: WeightUnit, distanceUnit: DistanceUnit): String =
        when (metric) {
            ChartMetric.HEAVIEST_WEIGHT, ChartMetric.ONE_REP_MAX,
            ChartMetric.BEST_SET_VOLUME, ChartMetric.SESSION_VOLUME,
            -> weight(raw, weightUnit)
            ChartMetric.TOTAL_REPS, ChartMetric.SESSION_REPS, ChartMetric.MOST_REPS_SET -> count(raw)
            ChartMetric.BEST_TIME, ChartMetric.LONGEST_TIME -> duration(raw.toInt())
            ChartMetric.LONGEST_DISTANCE -> distance(raw, distanceUnit)
            ChartMetric.BEST_PACE -> pace(raw, distanceUnit)
        }

    fun formatPrValue(prType: PrType, raw: Double, weightUnit: WeightUnit, distanceUnit: DistanceUnit): String =
        when (prType) {
            PrType.HEAVIEST_WEIGHT, PrType.BEST_1RM, PrType.BEST_SET_VOLUME, PrType.BEST_SESSION_VOLUME ->
                weight(raw, weightUnit)
            PrType.MOST_REPS_SET, PrType.MOST_SESSION_REPS -> count(raw)
            PrType.LONGEST_DISTANCE -> distance(raw, distanceUnit)
            PrType.BEST_TIME, PrType.LONGEST_TIME -> duration(raw.toInt())
        }

    /** Chart y-axis labels: the bare number, no unit suffix — the axis stays uncluttered. */
    fun axisLabel(metric: ChartMetric, raw: Double, weightUnit: WeightUnit, distanceUnit: DistanceUnit): String =
        when (metric) {
            ChartMetric.HEAVIEST_WEIGHT, ChartMetric.ONE_REP_MAX,
            ChartMetric.BEST_SET_VOLUME, ChartMetric.SESSION_VOLUME,
            -> oneDecimal(convertWeight(raw, weightUnit))
            ChartMetric.TOTAL_REPS, ChartMetric.SESSION_REPS, ChartMetric.MOST_REPS_SET -> count(raw)
            ChartMetric.BEST_TIME, ChartMetric.LONGEST_TIME -> duration(raw.toInt())
            ChartMetric.LONGEST_DISTANCE -> oneDecimal(convertDistance(raw, distanceUnit))
            ChartMetric.BEST_PACE -> mmss(convertPace(raw, distanceUnit).toInt())
        }

    private fun weight(rawKg: Double, unit: WeightUnit): String =
        "${oneDecimal(convertWeight(rawKg, unit))} ${if (unit == WeightUnit.KG) "kg" else "lb"}"

    private fun distance(rawMeters: Double, unit: DistanceUnit): String =
        "${oneDecimal(convertDistance(rawMeters, unit))} ${if (unit == DistanceUnit.KM) "km" else "mi"}"

    private fun pace(rawSecPerKm: Double, unit: DistanceUnit): String =
        "${mmss(convertPace(rawSecPerKm, unit).toInt())} ${if (unit == DistanceUnit.KM) "/km" else "/mi"}"

    private fun count(raw: Double): String = raw.toInt().toString()

    /** m:ss under an hour, h:mm:ss from an hour up. */
    fun duration(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun mmss(totalSeconds: Int): String = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

    private fun convertWeight(kg: Double, unit: WeightUnit): Double = WeightDisplay.toDisplay(kg, unit)

    private fun convertDistance(meters: Double, unit: DistanceUnit): Double = DistanceDisplay.toDisplay(meters, unit)

    private fun convertPace(secPerKm: Double, unit: DistanceUnit): Double =
        if (unit == DistanceUnit.MILES) secPerKm * (DistanceDisplay.METERS_PER_MILE / DistanceDisplay.METERS_PER_KM) else secPerKm

    /**
     * Round to 1 decimal (§8.9); whole results render without a decimal via a locale-independent
     * numeric check — a string ".0"-suffix trim silently fails on comma-decimal locales, where
     * "%.1f" yields "100,0" (matching PreviousValueFormatter/Workout Detail's whole-number rule).
     * The non-whole branch itself must stay Locale.ROOT for the same reason -- the default-locale
     * "%.1f" overload used here previously still rendered "12,5" on a comma-decimal device, only
     * the whole-number case had actually been fixed.
     */
    private fun oneDecimal(v: Double): String {
        val rounded = Math.round(v * 10.0) / 10.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, rounded)
    }
}
