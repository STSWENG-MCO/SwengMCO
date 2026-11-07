package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.repository.BodyMeasurement
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class BodyMeasurementChartTest {

    /** Noon UTC 2026-08-22 -- resolves to 2026-08-22 in every plausible test JVM zone. */
    private val today = LocalDate.parse("2026-08-22")

    private fun entry(date: String, waistCm: Double?) = BodyMeasurement(
        date = date, weightKg = null, leanMassKg = null, fatPercent = null,
        neckCm = null, shoulderCm = null, chestCm = null,
        leftBicepCm = null, rightBicepCm = null, leftForearmCm = null, rightForearmCm = null,
        abdomenCm = null, waistCm = waistCm, hipsCm = null,
        leftThighCm = null, rightThighCm = null, leftCalfCm = null, rightCalfCm = null,
        updatedAt = 0L,
    )

    @Test
    fun `all-time keeps every dated entry, sorted ascending`() {
        val entries = listOf(entry("2026-08-20", 80.0), entry("2020-01-01", 90.0), entry("2026-08-22", 85.0))
        val points = BodyMeasurementChart.points(BodyMeasurementMetric.WAIST, entries, ChartRange.ALL_TIME, today)
        assertEquals(listOf("2020-01-01", "2026-08-20", "2026-08-22"), points.map { it.date })
        assertEquals(listOf(90.0, 80.0, 85.0), points.map { it.value })
    }

    @Test
    fun `last 30 days excludes an entry just outside the window`() {
        val entries = listOf(entry("2026-08-22", 80.0), entry("2026-07-23", 79.0)) // 30 days back is 2026-07-24
        val points = BodyMeasurementChart.points(BodyMeasurementMetric.WAIST, entries, ChartRange.LAST_30_DAYS, today)
        assertEquals(listOf("2026-08-22"), points.map { it.date })
    }

    @Test
    fun `last 3 months boundary is inclusive at exactly 3 months back plus one day`() {
        // ChartAggregator.window(LAST_3_MONTHS) = today.minusMonths(3).plusDays(1)..today
        val entries = listOf(entry("2026-05-23", 80.0), entry("2026-05-22", 79.0))
        val points = BodyMeasurementChart.points(BodyMeasurementMetric.WAIST, entries, ChartRange.LAST_3_MONTHS, today)
        assertEquals(listOf("2026-05-23"), points.map { it.date })
    }

    @Test
    fun `last year boundary is inclusive at exactly one year back plus one day`() {
        val entries = listOf(entry("2025-08-23", 80.0), entry("2025-08-22", 79.0))
        val points = BodyMeasurementChart.points(BodyMeasurementMetric.WAIST, entries, ChartRange.LAST_YEAR, today)
        assertEquals(listOf("2025-08-23"), points.map { it.date })
    }

    @Test
    fun `entries with a null value for the selected metric are dropped, not zeroed`() {
        val entries = listOf(entry("2026-08-20", null), entry("2026-08-21", 80.0))
        val points = BodyMeasurementChart.points(BodyMeasurementMetric.WAIST, entries, ChartRange.ALL_TIME, today)
        assertEquals(listOf("2026-08-21"), points.map { it.date })
    }

    @Test
    fun `a different metric on the same entries reads independently`() {
        val entries = listOf(
            BodyMeasurement(
                date = "2026-08-20", weightKg = 70.0, leanMassKg = null, fatPercent = null,
                neckCm = null, shoulderCm = null, chestCm = null,
                leftBicepCm = null, rightBicepCm = null, leftForearmCm = null, rightForearmCm = null,
                abdomenCm = null, waistCm = 80.0, hipsCm = null,
                leftThighCm = null, rightThighCm = null, leftCalfCm = null, rightCalfCm = null,
                updatedAt = 0L,
            ),
        )
        val weightPoints = BodyMeasurementChart.points(BodyMeasurementMetric.WEIGHT, entries, ChartRange.ALL_TIME, today)
        val waistPoints = BodyMeasurementChart.points(BodyMeasurementMetric.WAIST, entries, ChartRange.ALL_TIME, today)
        assertEquals(70.0, weightPoints.single().value, 0.0)
        assertEquals(80.0, waistPoints.single().value, 0.0)
    }

    @Test
    fun `empty input produces an empty list, for every range`() {
        for (range in ChartRange.entries) {
            assertEquals(emptyList<BodyMeasurementPoint>(), BodyMeasurementChart.points(BodyMeasurementMetric.WAIST, emptyList(), range, today))
        }
    }
}
