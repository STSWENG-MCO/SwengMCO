package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.WarmupStep

/**
 * PHASE2_PLAN.md §5.1.6 — the Warm-up Calculator's ladder generator (M18). Pure math, no Android
 * imports: the logger feeds it a working weight, the persisted Warmup Method, and (for barbells)
 * the user's plate equipment, and inserts what comes back as WARMUP rows.
 *
 * Rounding rules (§5.1.6 "rounding preferences"):
 *  - Barbell: each percent weight is rounded to the NEAREST total the equipment can actually load
 *    (bar + 2 × a sum of owned plate denominations). This delegates to [PlateCalculator.solve] —
 *    the same closest-achievable DP the Plate Calculator sheet uses — so a warm-up weight is never
 *    one the user would then have to plate-calculate around. A step below the bar clamps to the
 *    bar itself (solve's belowBar result already reports the bar as the achieved weight). The bar
 *    used is the FIRST (lightest) owned bar — the same default the Plate Calculator sheet opens
 *    with, so the two calculators agree about what "loadable" means out of the box.
 *  - Non-barbell: round to the nearest [NON_BARBELL_INCREMENT_KG] (2.5 kg — dumbbell racks' usual
 *    step, §5.1.6's default). When rounding would hit 0 the RAW percent weight is returned
 *    unrounded instead: a 0 kg warm-up row is meaningless, and forcing a full 2.5 kg increment
 *    could exceed a very light working weight — the tiny raw value is the honest answer.
 *
 * Quarter-kg discipline: the barbell path runs entirely inside [PlateCalculator]'s integer
 * quarter-kg arithmetic (raw percents are rounded onto that grid on entry, results come back off
 * it), and 2.5 is itself a quarter-kg multiple, so every emitted weight composes exactly with the
 * Plate Calculator — no Double error can make a warm-up weight unloadable.
 */
object WarmupCalculator {

    /** §5.1.6 default dumbbell/other rounding increment. */
    const val NON_BARBELL_INCREMENT_KG = 2.5

    /** One generated warm-up row: the rounded weight plus the method step's reps. */
    data class WarmupSetPlan(val weightKg: Double, val reps: Int)

    /**
     * @param workingWeightKg the first working (non-WARMUP) set's weight; non-positive → empty.
     * @param method the persisted Warmup Method (percent × reps rows); empty → empty.
     * @param barbell whether the exercise's equipment is a barbell (plate-loadable).
     * @param equipment owned bars/plates — used only when [barbell]; null falls back to the
     *   non-barbell increment (no equipment to define "loadable").
     */
    fun generate(
        workingWeightKg: Double,
        method: List<WarmupStep>,
        barbell: Boolean,
        equipment: PlateEquipment?,
    ): List<WarmupSetPlan> {
        if (workingWeightKg <= 0.0 || workingWeightKg.isNaN()) return emptyList()
        return method.map { step ->
            val raw = workingWeightKg * step.percent
            val rounded = if (barbell && equipment != null) {
                roundToLoadable(raw, equipment)
            } else {
                roundToIncrement(raw)
            }
            WarmupSetPlan(weightKg = rounded, reps = step.reps)
        }
    }

    /**
     * Nearest bar + 2×plates total. [PlateCalculator.solve]'s achievedKg IS that answer: for a
     * below-bar target it reports the bar alone (the clamp), otherwise the closest achievable
     * total at or around the target — DP-optimal, not greedy.
     */
    private fun roundToLoadable(rawKg: Double, equipment: PlateEquipment): Double {
        val bar = equipment.barsKg.firstOrNull() ?: return roundToIncrement(rawKg)
        return PlateCalculator.solve(rawKg, bar, equipment.platesKg).achievedKg
    }

    private fun roundToIncrement(rawKg: Double): Double {
        val rounded = Math.round(rawKg / NON_BARBELL_INCREMENT_KG) * NON_BARBELL_INCREMENT_KG
        // Documented choice: a step that rounds to zero keeps its raw (tiny) weight — see class doc.
        return if (rounded <= 0.0) rawKg else rounded
    }
}
