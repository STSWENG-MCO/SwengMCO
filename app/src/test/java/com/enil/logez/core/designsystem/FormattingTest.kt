package com.enil.logez.core.designsystem

import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.WeightUnit
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Formatting.weightKg] and [Formatting.mmSs] formatted with the default locale until 2026-09-22,
 * so a comma-decimal device read "12,5kg" and "0:05" became "0:05" only by luck of the format
 * string. The locale tests below pin the default locale to one that would expose that and
 * restore it afterwards, so a regression fails here and not on a German phone.
 */
class FormattingTest {
    private fun <T> withDefaultLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try { block() } finally { Locale.setDefault(previous) }
    }

    @Test
    fun `weightKg uses a dot decimal separator whatever the device locale`() {
        withDefaultLocale(Locale.GERMANY) {
            assertEquals("12.5kg", Formatting.weightKg(12.5))
            assertEquals("100kg", Formatting.weightKg(100.0))
        }
    }

    @Test
    fun `mmSs pads seconds independent of the device locale`() {
        withDefaultLocale(Locale.GERMANY) {
            assertEquals("1:05", Formatting.mmSs(65))
            assertEquals("0:00", Formatting.mmSs(0))
        }
    }

    @Test
    fun `weight converts a stored kg figure to the display unit and suffixes it`() {
        assertEquals("100kg", Formatting.weight(100.0, WeightUnit.KG))
        assertEquals("220.46lb", Formatting.weight(100.0, WeightUnit.LB))
        assertEquals("0lb", Formatting.weight(0.0, WeightUnit.LB))
        assertEquals(Formatting.weight(60.0, WeightUnit.LB), formatWeight(60.0, WeightUnit.LB))
    }
    @Test
    fun `a whole number of kilometers has no decimal point`() {
        assertEquals("2km", Formatting.distanceKm(2000.0))
        assertEquals("0km", Formatting.distanceKm(0.0))
    }

    @Test
    fun `a fractional distance shows two decimal places`() {
        assertEquals("2.35km", Formatting.distanceKm(2350.0))
        assertEquals("0.15km", Formatting.distanceKm(150.0))
    }

    @Test
    fun `float drift just under a whole km still rounds to a clean value, not a garbled one`() {
        // 2999.999999m is a hair under 3km -- the whole-vs-decimal branch picks the decimal path
        // here (2.999999999 != 2.0), but the rounded *value* must still read as a sane "3.00km",
        // not an artifact of the drift (e.g. "2.99km" from truncation, or a trailing-digit glitch).
        assertEquals("3.00km", Formatting.distanceKm(2999.999999))
    }

    @Test
    fun `formatDistanceKm the top-level alias delegates to the same result`() {
        assertEquals(Formatting.distanceKm(1234.0), formatDistanceKm(1234.0))
    }

    @Test
    fun `distance converts a stored meters figure to the display unit and suffixes it`() {
        assertEquals("2.35km", Formatting.distance(2350.0, DistanceUnit.KM))
        assertEquals("1mi", Formatting.distance(1609.344, DistanceUnit.MILES))
        assertEquals("3.11mi", Formatting.distance(5000.0, DistanceUnit.MILES))
        assertEquals(Formatting.distance(5000.0, DistanceUnit.MILES), formatDistance(5000.0, DistanceUnit.MILES))
    }

    /**
     * wholeOrOneDecimal's non-whole branch used to fall back to the raw Double.toString(), which
     * only ever looked like one decimal place for a value a user had typed directly -- anything
     * that had gone through real floating-point arithmetic first (summed, converted) could carry
     * a long tail straight to a Statistics-page/target-value cell. Found during a 2026-09-23
     * decimal-precision sweep of the Statistics page and the walk/run screens.
     */
    @Test
    fun `wholeOrOneDecimal rounds a long floating-point tail down to one decimal`() {
        assertEquals("152.5", Formatting.wholeOrOneDecimal(152.5))
        assertEquals("152.5", Formatting.wholeOrOneDecimal(152.50000000001))
        assertEquals("152.5", Formatting.wholeOrOneDecimal(152.5399))
        assertEquals("152", Formatting.wholeOrOneDecimal(152.0))
    }

    @Test
    fun `wholeOrOneDecimal uses a dot decimal separator whatever the device locale`() {
        withDefaultLocale(Locale.GERMANY) {
            assertEquals("12.5", Formatting.wholeOrOneDecimal(12.5))
        }
    }

    /** [Formatting.twoDecimals] is the walk/run precision convention -- one decimal place more
     * permissive than [Formatting.wholeOrOneDecimal]'s Statistics-page convention, since a
     * GPS-accumulated distance is naturally noisier than a typed strength target. */
    @Test
    fun `twoDecimals rounds a long floating-point tail down to two decimals`() {
        assertEquals("152", Formatting.twoDecimals(152.0))
        assertEquals("152.5", Formatting.twoDecimals(152.5))
        assertEquals("3247.89", Formatting.twoDecimals(3247.8921336))
        assertEquals("152.54", Formatting.twoDecimals(152.539))
    }

    @Test
    fun `twoDecimals uses a dot decimal separator whatever the device locale`() {
        withDefaultLocale(Locale.GERMANY) {
            assertEquals("3247.89", Formatting.twoDecimals(3247.8921336))
        }
    }

    @Test
    fun `formatTwoDecimals the top-level alias delegates to the same result`() {
        assertEquals(Formatting.twoDecimals(3247.8921336), formatTwoDecimals(3247.8921336))
    }

    /** A comma-decimal keyboard types "32,5"; it used to be silently discarded (2026-09-25). */
    @Test
    fun `parseDecimalInput accepts a comma or a dot and ignores surrounding spaces`() {
        assertEquals(32.5, parseDecimalInput("32,5")!!, 0.0)
        assertEquals(32.5, parseDecimalInput(" 32.5 ")!!, 0.0)
        assertEquals(40.0, parseDecimalInput("40")!!, 0.0)
        assertEquals(null, parseDecimalInput(""))
        assertEquals(null, parseDecimalInput("1,2,3"))
    }
}
