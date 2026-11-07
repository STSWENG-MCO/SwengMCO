package com.enil.logez.feature.analytics

import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.WeightUnit

/**
 * Display formatting for the dashboard's workout-level values. Same locale-safe whole-number
 * convention as SummaryFormatters/PreviousValueFormatter: math on raw kg/seconds, conversion and
 * rounding at display time only, and no string-based ".0" trims (they fail on comma locales).
 */
object AnalyticsFormatters {
    fun volume(rawKg: Double, unit: WeightUnit): String =
        "${oneDecimal(WeightDisplay.toDisplay(rawKg, unit))} ${if (unit == WeightUnit.KG) "kg" else "lb"}"

    /** Whole hours+minutes for card totals — "5h 32m", "45m". Second precision earns nothing at week scale. */
    fun durationHoursMinutes(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    fun count(raw: Double): String = Math.round(raw).toString()

    /** Bar readout for one weekly value of [metric]. */
    fun metricValue(metric: TrainingMetric, raw: Double, unit: WeightUnit): String = when (metric) {
        TrainingMetric.VOLUME -> volume(raw, unit)
        TrainingMetric.REPS, TrainingMetric.FREQUENCY -> count(raw)
        TrainingMetric.DURATION -> durationHoursMinutes(raw.toLong())
    }

    /** Y-axis labels: bare numbers; duration in whole minutes so the axis stays readable. */
    fun axisLabel(metric: TrainingMetric, raw: Double, unit: WeightUnit): String = when (metric) {
        TrainingMetric.VOLUME -> oneDecimal(WeightDisplay.toDisplay(raw, unit))
        TrainingMetric.REPS, TrainingMetric.FREQUENCY -> count(raw)
        TrainingMetric.DURATION -> (raw.toLong() / 60).toString()
    }

    /** Locale.ROOT: the default-locale "%.1f" overload renders "12,5" on a comma-decimal device
     *  (the same bug class PreviousValueFormatter/SummaryFormatters guard against). */
    private fun oneDecimal(v: Double): String {
        val rounded = Math.round(v * 10.0) / 10.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, rounded)
    }
}
