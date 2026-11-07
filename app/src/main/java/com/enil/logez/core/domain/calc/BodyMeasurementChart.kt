package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.repository.BodyMeasurement
import java.time.LocalDate

/** One dated value for a single [BodyMeasurementMetric] -- [date] is the ISO `yyyy-MM-dd` string
 * straight from [BodyMeasurement.date], left as a String so the UI layer decides how to render it
 * (this codebase's [com.enil.logez.core.designsystem.LineChart] wants epoch millis, not this). */
data class BodyMeasurementPoint(val date: String, val value: Double)

/** Range-filters and flattens [BodyMeasurement] entries down to one metric's plottable points. */
object BodyMeasurementChart {
    fun points(
        metric: BodyMeasurementMetric,
        entries: List<BodyMeasurement>,
        range: ChartRange,
        today: LocalDate,
    ): List<BodyMeasurementPoint> {
        val window = ChartAggregator.window(range, today)
        return entries
            .asSequence()
            .filter { window == null || LocalDate.parse(it.date) in window }
            .mapNotNull { entry -> entry.valueFor(metric)?.let { BodyMeasurementPoint(entry.date, it) } }
            .sortedBy { it.date }
            .toList()
    }
}
