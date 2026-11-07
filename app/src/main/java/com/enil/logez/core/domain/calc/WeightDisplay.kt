package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.WeightUnit
import java.util.Locale

/**
 * M18 — the single kg ↔ display-unit boundary for weight INPUT cells (logger set rows, builder
 * target rows). Storage is always canonical kg (WeightUnit doc: "Display-only — canonical storage
 * is always kg"); these helpers convert exactly once at the UI edge, in each direction.
 *
 * Precision choice (documented per the M18 brief): entered LB values are stored at FULL Double
 * precision via the exact legal definition 1 lb = 0.45359237 kg — no rounding on store, so
 * "100" lb stores 45.359237 kg. The display path formats to at most two decimals ([format],
 * Locale.ROOT so the string round-trips through String.toDoubleOrNull on comma-decimal locales),
 * which absorbs any last-ulp float drift: an untouched 45.359237 kg re-displays as "100", never
 * "99.9…". The input cells additionally keep the user's own typed string and only re-derive it
 * when the stored kg changes underneath them, so conversion never fights active typing.
 */
object WeightDisplay {

    /** Exact by definition (international avoirdupois pound). */
    const val KG_PER_LB = 0.45359237

    fun toDisplay(kg: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.LB) kg / KG_PER_LB else kg

    fun toKg(display: Double, unit: WeightUnit): Double =
        if (unit == WeightUnit.LB) display * KG_PER_LB else display

    /** Whole numbers bare, else up to two decimals with trailing zeros trimmed, dot separator always. */
    fun format(value: Double): String {
        if (value.isNaN() || value.isInfinite()) return ""
        if (value == Math.floor(value)) return value.toLong().toString()
        return "%.2f".format(Locale.ROOT, value).trimEnd('0').trimEnd('.')
    }
}
