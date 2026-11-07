package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.WeightUnit

/**
 * PHASE2_PLAN.md §8.10 — formats the live logger's PREVIOUS column. Purely a display formatter.
 * [format] returns the value line ("50 kg x 10"); [formatRpeLine] returns a second, optional line
 * (Owner, 2026-09-03: RPE renders on its own line under the value, not appended inline) so the two
 * can render as separate `Text`s in the PREVIOUS cell without any string-splitting in the UI layer.
 */
object PreviousValueFormatter {
    fun format(set: StatSet, type: ExerciseType, weightUnit: WeightUnit, distanceUnit: DistanceUnit): String {
        val base = when (type) {
            ExerciseType.WEIGHT_REPS, ExerciseType.BODYWEIGHT_WEIGHTED, ExerciseType.BODYWEIGHT_ASSISTED -> {
                val w = set.weightKg?.let { convertWeight(it, weightUnit) }
                val r = set.reps
                if (w != null && r != null) "${formatNumber(w)} ${weightUnit.label()} x $r" else "—"
            }
            ExerciseType.REPS_ONLY -> set.reps?.let { "$it reps" } ?: "—"
            ExerciseType.DURATION, ExerciseType.FLOORS_DURATION, ExerciseType.STEPS_DURATION ->
                set.durationSeconds?.let { formatDuration(it) } ?: "—"
            ExerciseType.WEIGHT_DURATION -> {
                val w = set.weightKg?.let { convertWeight(it, weightUnit) }
                val d = set.durationSeconds
                if (w != null && d != null) "${formatNumber(w)} ${weightUnit.label()} x ${formatDuration(d)}" else "—"
            }
            ExerciseType.DISTANCE_DURATION -> {
                val dist = set.distanceMeters?.let { convertDistance(it, distanceUnit) }
                val d = set.durationSeconds
                if (dist != null && d != null) "${formatNumber(dist)} ${distanceUnit.label()} / ${formatDuration(d)}" else "—"
            }
            ExerciseType.WEIGHT_DISTANCE -> {
                val w = set.weightKg?.let { convertWeight(it, weightUnit) }
                val dist = set.distanceMeters?.let { convertDistance(it, distanceUnit) }
                if (w != null && dist != null) "${formatNumber(w)} ${weightUnit.label()} x ${formatNumber(dist)} ${distanceUnit.label()}" else "—"
            }
        }
        return base
    }

    /** The PREVIOUS cell's second line, e.g. "RPE 8.5" — null when the previous set has no RPE. */
    fun formatRpeLine(set: StatSet): String? = set.rpe?.let { "RPE ${formatNumber(it)}" }

    private fun convertWeight(kg: Double, unit: WeightUnit) = WeightDisplay.toDisplay(kg, unit)
    private fun convertDistance(m: Double, unit: DistanceUnit) = DistanceDisplay.toDisplay(m, unit)
    // Locale.ROOT: the default-locale overload renders "42,5" on comma-decimal devices (the same
    // bug class M17's review caught in the plate sheet) — PREVIOUS must match the cells' dot style.
    private fun formatNumber(v: Double) = if (v == Math.floor(v)) v.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, v)
    private fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%d:%02d".format(m, s)
    }
    private fun WeightUnit.label() = if (this == WeightUnit.KG) "kg" else "lb"
    private fun DistanceUnit.label() = if (this == DistanceUnit.KM) "km" else "mi"
}
