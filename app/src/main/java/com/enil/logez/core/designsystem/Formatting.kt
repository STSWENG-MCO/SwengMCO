package com.enil.logez.core.designsystem

import com.enil.logez.core.domain.calc.DistanceDisplay
import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.WeightUnit
import java.util.Locale

/**
 * Shared formatting utilities for the UI layer.
 *
 * Consolidates the 4× duplicated `formatTargetNumber`, 3× duplicated `formatVolume`,
 * and 2× duplicated `formatMmSs` functions that previously lived as private functions
 * in individual screens and ViewModels.
 */
object Formatting {
    /**
     * Formats a [Double] as a whole number when possible, otherwise with one decimal place.
     * Used for weight cells, target values, and set-table displays -- the Statistics-page
     * precision convention (Owner request, 2026-09-23): every decimal statistic there stops at
     * tenths.
     *
     * Examples: `152.0` → `"152"`, `152.5` → `"152.5"`, `0.0` → `"0"`
     *
     * The non-whole branch used to fall back to the raw `Double.toString()`, which only ever
     * *looked* like one decimal place for values a user had typed directly (e.g. "152.5" parsed
     * back to `152.5`) -- any value that had gone through actual floating-point arithmetic first
     * (a unit conversion, a sum) could carry a long tail (`152.53000000001`) straight to the
     * screen. Rounds explicitly instead, matching [AnalyticsFormatters.oneDecimal]/
     * [SummaryFormatters.oneDecimal]'s already-correct shape.
     */
    fun wholeOrOneDecimal(value: Double): String {
        val rounded = Math.round(value * 10.0) / 10.0
        return if (rounded == Math.floor(rounded)) rounded.toLong().toString() else "%.1f".format(Locale.ROOT, rounded)
    }

    /**
     * Formats a [Double] as a whole number when possible, otherwise with up to two decimal places
     * (trailing zeros trimmed) -- the walk/run precision convention (Owner request, 2026-09-23),
     * one decimal place more permissive than [wholeOrOneDecimal]'s Statistics-page convention,
     * since a GPS-accumulated distance is naturally noisier than a typed strength target.
     *
     * Examples: `152.0` → `"152"`, `152.5` → `"152.5"`, `152.539` → `"152.54"`
     */
    fun twoDecimals(value: Double): String {
        val rounded = Math.round(value * 100.0) / 100.0
        return if (rounded == Math.floor(rounded)) {
            rounded.toLong().toString()
        } else {
            "%.2f".format(Locale.ROOT, rounded).trimEnd('0').trimEnd('.')
        }
    }

    /**
     * Formats a weight in kg with the "kg" suffix.
     * Whole numbers display without decimals; non-whole display one decimal.
     *
     * Examples: `152.0` → `"152kg"`, `152.5` → `"152.5kg"`
     */
    fun weightKg(kg: Double): String =
        if (kg == kg.toLong().toDouble()) "${kg.toLong()}kg" else "%.1fkg".format(Locale.ROOT, kg)

    /**
     * Formats a weight in kg with the "kg" suffix — identical to [weightKg].
     * Named distinctly for call sites that previously used `formatVolumeShort`.
     */
    fun weightKgShort(kg: Double): String = weightKg(kg)

    /**
     * A weight in the user's display unit, suffixed. Storage is always kg; this is where a stored
     * figure becomes text a user reads. A "kg" suffix on a value shown to a pounds user was a 2.2x
     * discrepancy that read as a wrong total — worst in the logger, where the set cells already
     * showed pounds two rows below a header still in kilograms.
     */
    fun weight(kg: Double, unit: WeightUnit): String =
        WeightDisplay.format(WeightDisplay.toDisplay(kg, unit)) + if (unit == WeightUnit.KG) "kg" else "lb"

    /**
     * Formats total seconds as `M:SS` (e.g., `90` → `"1:30"`, `0` → `"0:00"`).
     * Used for rest timers, duration displays, and time cells.
     */
    fun mmSs(totalSeconds: Int): String =
        "%d:%02d".format(Locale.ROOT, totalSeconds / 60, totalSeconds % 60)

    /**
     * Formats a distance in meters as kilometers with a "km" suffix, `Locale.ROOT`-safe (unlike
     * [weightKg]/[mmSs] above, which format with the default locale and can render non-ASCII
     * digits on some locales — a known, separately-tracked issue, not repeated here).
     *
     * Examples: `2000.0` -> `"2km"`, `2350.0` -> `"2.35km"`
     */
    fun distanceKm(meters: Double): String {
        val km = meters / 1000.0
        return if (km == km.toLong().toDouble()) "${km.toLong()}km" else "%.2fkm".format(Locale.ROOT, km)
    }

    /**
     * A distance in the user's display unit, suffixed -- [distanceKm]'s shape, unit-aware. Storage
     * is always meters; this is where a stored figure becomes text a user reads. Every
     * distance surface off the live tracking screen (History card, workout detail, Finish summary)
     * used to render km for a miles user while the pace right next to it said "/mi" (adversarial
     * review, 2026-09-23) -- the same 2.2x-style discrepancy [weight] closed for kg/lb the day before.
     */
    fun distance(meters: Double, unit: DistanceUnit): String {
        val display = DistanceDisplay.toDisplay(meters, unit)
        val suffix = if (unit == DistanceUnit.KM) "km" else "mi"
        return if (display == display.toLong().toDouble()) "${display.toLong()}$suffix" else "%.2f$suffix".format(Locale.ROOT, display)
    }

    /**
     * A clock-style elapsed time: `m:ss` under an hour, `h:mm:ss` from an hour up, `Locale.ROOT`.
     * The live tracking screen and the walk/run summary both read this, so a run's time looks the
     * same while it ticks and after it is saved.
     */
    fun elapsedClock(totalSeconds: Int): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
    }

    /** `M:SS` per km/mile from [PaceCalculator.paceSecondsPerUnit] -- reuses [mmSs]'s exact shape. */
    fun pace(secondsPerUnit: Double): String = mmSs(secondsPerUnit.toLong().toInt())
}

/** Convenience top-level aliases so call sites read naturally. */
fun formatTargetNumber(value: Double): String = Formatting.wholeOrOneDecimal(value)
fun formatTwoDecimals(value: Double): String = Formatting.twoDecimals(value)
fun formatWeightKg(kg: Double): String = Formatting.weightKg(kg)
fun formatWeightKgShort(kg: Double): String = Formatting.weightKgShort(kg)
fun formatWeight(kg: Double, unit: WeightUnit): String = Formatting.weight(kg, unit)
fun formatMmSs(totalSeconds: Int): String = Formatting.mmSs(totalSeconds)
fun formatDistanceKm(meters: Double): String = Formatting.distanceKm(meters)
fun formatDistance(meters: Double, unit: DistanceUnit): String = Formatting.distance(meters, unit)
fun formatPace(secondsPerUnit: Double): String = Formatting.pace(secondsPerUnit)
fun formatElapsedClock(totalSeconds: Int): String = Formatting.elapsedClock(totalSeconds)

/**
 * Parses a number the user typed, accepting a comma as the decimal separator. A phone set to a
 * comma-decimal language shows "," on its number keyboard, and plain String.toDoubleOrNull()
 * silently discarded "32,5" (Play-readiness audit, 2026-09-25): the field looked filled in but
 * nothing was saved. Every free-typed decimal field goes through this instead.
 */
fun parseDecimalInput(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
