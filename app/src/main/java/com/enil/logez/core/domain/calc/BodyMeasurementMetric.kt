package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.MeasurementsTrackingMode
import com.enil.logez.core.domain.repository.BodyMeasurement

/** Which display/conversion a [BodyMeasurementMetric] needs -- lets the UI layer pick between
 * [WeightDisplay], [LengthDisplay], or a bare percent formatter without a second 17-branch `when`. */
enum class MetricKind { WEIGHT, LENGTH, PERCENT }

/** One entry per field on [BodyMeasurement] (minus [BodyMeasurement.date]/[BodyMeasurement.updatedAt]). */
enum class BodyMeasurementMetric(val kind: MetricKind) {
    WEIGHT(MetricKind.WEIGHT),
    LEAN_MASS(MetricKind.WEIGHT),
    FAT_PERCENT(MetricKind.PERCENT),
    NECK(MetricKind.LENGTH),
    SHOULDER(MetricKind.LENGTH),
    CHEST(MetricKind.LENGTH),
    LEFT_BICEP(MetricKind.LENGTH),
    RIGHT_BICEP(MetricKind.LENGTH),
    LEFT_FOREARM(MetricKind.LENGTH),
    RIGHT_FOREARM(MetricKind.LENGTH),
    ABDOMEN(MetricKind.LENGTH),
    WAIST(MetricKind.LENGTH),
    HIPS(MetricKind.LENGTH),
    LEFT_THIGH(MetricKind.LENGTH),
    RIGHT_THIGH(MetricKind.LENGTH),
    LEFT_CALF(MetricKind.LENGTH),
    RIGHT_CALF(MetricKind.LENGTH),
}

/** The Owner-curated "Simplified" set (2026-09-21) -- weight, body fat, and lean mass, the three
 * body-composition metrics most people track without a tape measure. An explicit, named set, not
 * derived from [MetricKind] -- kind and Simplified-membership are different axes that only
 * coincidentally align today (every non-LENGTH metric happens to also be a Simplified one). */
val SIMPLIFIED_METRICS: Set<BodyMeasurementMetric> = setOf(
    BodyMeasurementMetric.WEIGHT,
    BodyMeasurementMetric.LEAN_MASS,
    BodyMeasurementMetric.FAT_PERCENT,
)

/** The metrics the Measurements screen shows for entry/display in [mode]. */
fun visibleMetrics(mode: MeasurementsTrackingMode): List<BodyMeasurementMetric> =
    if (mode == MeasurementsTrackingMode.COMPLETE) BodyMeasurementMetric.entries else BodyMeasurementMetric.entries.filter { it in SIMPLIFIED_METRICS }

/** The one place a [BodyMeasurementMetric] maps to its field on [BodyMeasurement] -- a copy-paste
 * mistake across these 17 near-identical branches has exactly one spot to hide, and one test file
 * ([com.enil.logez.core.domain.calc.BodyMeasurementMetricTest]) that checks every branch. */
fun BodyMeasurement.valueFor(metric: BodyMeasurementMetric): Double? = when (metric) {
    BodyMeasurementMetric.WEIGHT -> weightKg
    BodyMeasurementMetric.LEAN_MASS -> leanMassKg
    BodyMeasurementMetric.FAT_PERCENT -> fatPercent
    BodyMeasurementMetric.NECK -> neckCm
    BodyMeasurementMetric.SHOULDER -> shoulderCm
    BodyMeasurementMetric.CHEST -> chestCm
    BodyMeasurementMetric.LEFT_BICEP -> leftBicepCm
    BodyMeasurementMetric.RIGHT_BICEP -> rightBicepCm
    BodyMeasurementMetric.LEFT_FOREARM -> leftForearmCm
    BodyMeasurementMetric.RIGHT_FOREARM -> rightForearmCm
    BodyMeasurementMetric.ABDOMEN -> abdomenCm
    BodyMeasurementMetric.WAIST -> waistCm
    BodyMeasurementMetric.HIPS -> hipsCm
    BodyMeasurementMetric.LEFT_THIGH -> leftThighCm
    BodyMeasurementMetric.RIGHT_THIGH -> rightThighCm
    BodyMeasurementMetric.LEFT_CALF -> leftCalfCm
    BodyMeasurementMetric.RIGHT_CALF -> rightCalfCm
}
