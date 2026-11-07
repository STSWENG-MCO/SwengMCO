package com.enil.logez.feature.exercises

import com.enil.logez.core.domain.calc.ChartMetric
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** §8.9 display rules: 1-decimal after unit conversion, pace as m:ss per distance unit. */
class SummaryFormattersTest {
    @Test
    fun `the plan's own BEST_PACE vector - 288 s per km displays as 4-48 per km`() {
        assertEquals("4:48 /km", SummaryFormatters.formatMetricValue(ChartMetric.BEST_PACE, 288.0, WeightUnit.KG, DistanceUnit.KM))
    }

    @Test
    fun `pace converts to per-mile when the distance unit is miles`() {
        // 288 s/km x 1.609344 = 463.49 s/mi -> 7:43
        assertEquals("7:43 /mi", SummaryFormatters.formatMetricValue(ChartMetric.BEST_PACE, 288.0, WeightUnit.KG, DistanceUnit.MILES))
    }

    @Test
    fun `the plan's ONE_REP_MAX vector rounds to one decimal`() {
        assertEquals("106.7 kg", SummaryFormatters.formatMetricValue(ChartMetric.ONE_REP_MAX, 106.66666666666667, WeightUnit.KG, DistanceUnit.KM))
    }

    @Test
    fun `whole weights drop the trailing decimal`() {
        assertEquals("100 kg", SummaryFormatters.formatMetricValue(ChartMetric.HEAVIEST_WEIGHT, 100.0, WeightUnit.KG, DistanceUnit.KM))
    }

    @Test
    fun `weights convert to pounds`() {
        assertEquals("220.5 lb", SummaryFormatters.formatMetricValue(ChartMetric.HEAVIEST_WEIGHT, 100.0, WeightUnit.LB, DistanceUnit.KM))
    }

    @Test
    fun `rep metrics are bare integers`() {
        assertEquals("16", SummaryFormatters.formatMetricValue(ChartMetric.TOTAL_REPS, 16.0, WeightUnit.KG, DistanceUnit.KM))
    }

    @Test
    fun `times show m-ss under an hour and h-mm-ss from an hour up`() {
        assertEquals("1:30", SummaryFormatters.formatMetricValue(ChartMetric.BEST_TIME, 90.0, WeightUnit.KG, DistanceUnit.KM))
        assertEquals("1:01:40", SummaryFormatters.formatMetricValue(ChartMetric.BEST_TIME, 3700.0, WeightUnit.KG, DistanceUnit.KM))
    }

    @Test
    fun `distances display in the selected unit`() {
        assertEquals("5 km", SummaryFormatters.formatMetricValue(ChartMetric.LONGEST_DISTANCE, 5000.0, WeightUnit.KG, DistanceUnit.KM))
        assertEquals("3.1 mi", SummaryFormatters.formatMetricValue(ChartMetric.LONGEST_DISTANCE, 5000.0, WeightUnit.KG, DistanceUnit.MILES))
    }

    @Test
    fun `PR values format by their type's value kind`() {
        assertEquals("105 kg", SummaryFormatters.formatPrValue(PrType.HEAVIEST_WEIGHT, 105.0, WeightUnit.KG, DistanceUnit.KM))
        assertEquals("12", SummaryFormatters.formatPrValue(PrType.MOST_REPS_SET, 12.0, WeightUnit.KG, DistanceUnit.KM))
        assertEquals("2:05", SummaryFormatters.formatPrValue(PrType.BEST_TIME, 125.0, WeightUnit.KG, DistanceUnit.KM))
        assertEquals("10 km", SummaryFormatters.formatPrValue(PrType.LONGEST_DISTANCE, 10_000.0, WeightUnit.KG, DistanceUnit.KM))
        assertEquals("1350 kg", SummaryFormatters.formatPrValue(PrType.BEST_SESSION_VOLUME, 1350.0, WeightUnit.KG, DistanceUnit.KM))
    }
}
