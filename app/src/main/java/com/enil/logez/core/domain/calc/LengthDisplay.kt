package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.LengthUnit
import java.util.Locale

/**
 * The cm ↔ display-unit boundary for body-measurement input cells (Measurements entry form).
 * Storage is always canonical cm (LengthUnit doc: "Display-only — canonical storage is always
 * cm"); these helpers convert exactly once at the UI edge, in each direction, mirroring
 * [WeightDisplay]'s own kg-boundary shape.
 */
object LengthDisplay {

    /** Exact by definition (international inch). */
    const val CM_PER_IN = 2.54

    fun toDisplay(cm: Double, unit: LengthUnit): Double =
        if (unit == LengthUnit.IN) cm / CM_PER_IN else cm

    fun toCm(display: Double, unit: LengthUnit): Double =
        if (unit == LengthUnit.IN) display * CM_PER_IN else display

    /** Whole numbers bare, else up to two decimals with trailing zeros trimmed, dot separator always. */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return ""
        if (value == Math.floor(value)) return value.toLong().toString()
        return "%.2f".format(Locale.ROOT, value).trimEnd('0').trimEnd('.')
    }
}
