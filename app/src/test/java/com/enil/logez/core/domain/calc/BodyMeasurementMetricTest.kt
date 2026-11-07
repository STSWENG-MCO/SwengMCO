package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.MeasurementsTrackingMode
import com.enil.logez.core.domain.repository.BodyMeasurement
import org.junit.Assert.assertEquals
import org.junit.Test

/** Every [BodyMeasurementMetric] must read its own, distinct field -- a copy-paste mistake in
 * [valueFor]'s 17-branch `when` swaps two metrics' values, which only a per-field distinct-value
 * fixture like this one can actually catch (equal placeholder values would hide the bug). */
class BodyMeasurementMetricTest {

    // Every field gets its own value, one per index, so a copy-paste swap between any two
    // branches produces a wrong number rather than accidentally matching.
    private val entry = BodyMeasurement(
        date = "2026-09-20",
        weightKg = 1.0,
        leanMassKg = 2.0,
        fatPercent = 3.0,
        neckCm = 4.0,
        shoulderCm = 5.0,
        chestCm = 6.0,
        leftBicepCm = 7.0,
        rightBicepCm = 8.0,
        leftForearmCm = 9.0,
        rightForearmCm = 10.0,
        abdomenCm = 11.0,
        waistCm = 12.0,
        hipsCm = 13.0,
        leftThighCm = 14.0,
        rightThighCm = 15.0,
        leftCalfCm = 16.0,
        rightCalfCm = 17.0,
        updatedAt = 0L,
    )

    @Test
    fun `each metric reads its own field`() {
        assertEquals(1.0, entry.valueFor(BodyMeasurementMetric.WEIGHT))
        assertEquals(2.0, entry.valueFor(BodyMeasurementMetric.LEAN_MASS))
        assertEquals(3.0, entry.valueFor(BodyMeasurementMetric.FAT_PERCENT))
        assertEquals(4.0, entry.valueFor(BodyMeasurementMetric.NECK))
        assertEquals(5.0, entry.valueFor(BodyMeasurementMetric.SHOULDER))
        assertEquals(6.0, entry.valueFor(BodyMeasurementMetric.CHEST))
        assertEquals(7.0, entry.valueFor(BodyMeasurementMetric.LEFT_BICEP))
        assertEquals(8.0, entry.valueFor(BodyMeasurementMetric.RIGHT_BICEP))
        assertEquals(9.0, entry.valueFor(BodyMeasurementMetric.LEFT_FOREARM))
        assertEquals(10.0, entry.valueFor(BodyMeasurementMetric.RIGHT_FOREARM))
        assertEquals(11.0, entry.valueFor(BodyMeasurementMetric.ABDOMEN))
        assertEquals(12.0, entry.valueFor(BodyMeasurementMetric.WAIST))
        assertEquals(13.0, entry.valueFor(BodyMeasurementMetric.HIPS))
        assertEquals(14.0, entry.valueFor(BodyMeasurementMetric.LEFT_THIGH))
        assertEquals(15.0, entry.valueFor(BodyMeasurementMetric.RIGHT_THIGH))
        assertEquals(16.0, entry.valueFor(BodyMeasurementMetric.LEFT_CALF))
        assertEquals(17.0, entry.valueFor(BodyMeasurementMetric.RIGHT_CALF))
    }

    @Test
    fun `every metric is covered exactly once, matching the entry's own field count`() {
        assertEquals(BodyMeasurementMetric.entries.size, BodyMeasurementMetric.entries.toSet().size)
        assertEquals(17, BodyMeasurementMetric.entries.size)
    }

    @Test
    fun `a null field returns null, not a default of zero`() {
        val blank = entry.copy(waistCm = null)
        assertEquals(null, blank.valueFor(BodyMeasurementMetric.WAIST))
        // A sibling field on the same entry is untouched by the copy.
        assertEquals(12.0, entry.valueFor(BodyMeasurementMetric.WAIST))
    }

    @Test
    fun `kind groups metrics correctly for unit-conversion routing`() {
        assertEquals(MetricKind.WEIGHT, BodyMeasurementMetric.WEIGHT.kind)
        assertEquals(MetricKind.WEIGHT, BodyMeasurementMetric.LEAN_MASS.kind)
        assertEquals(MetricKind.PERCENT, BodyMeasurementMetric.FAT_PERCENT.kind)
        val lengthMetrics = BodyMeasurementMetric.entries.filter { it.kind == MetricKind.LENGTH }
        assertEquals(14, lengthMetrics.size)
    }

    @Test
    fun `SIMPLIFIED_METRICS is exactly weight, lean mass, and fat percent`() {
        assertEquals(
            setOf(BodyMeasurementMetric.WEIGHT, BodyMeasurementMetric.LEAN_MASS, BodyMeasurementMetric.FAT_PERCENT),
            SIMPLIFIED_METRICS,
        )
    }

    @Test
    fun `visibleMetrics returns all 17 for COMPLETE and only the Simplified 3 for SIMPLIFIED`() {
        assertEquals(17, visibleMetrics(MeasurementsTrackingMode.COMPLETE).size)
        assertEquals(3, visibleMetrics(MeasurementsTrackingMode.SIMPLIFIED).size)
        assertEquals(SIMPLIFIED_METRICS, visibleMetrics(MeasurementsTrackingMode.SIMPLIFIED).toSet())
    }
}
