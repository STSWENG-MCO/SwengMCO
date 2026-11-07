package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** M18: the single kg ↔ display-unit boundary for weight input cells. */
class WeightDisplayTest {

    // --- Conversions ---

    @Test
    fun `KG passes through unchanged in both directions`() {
        assertEquals(100.0, WeightDisplay.toDisplay(100.0, WeightUnit.KG), 0.0)
        assertEquals(62.5, WeightDisplay.toKg(62.5, WeightUnit.KG), 0.0)
    }

    @Test
    fun `typing 100 lb stores the exact legal definition in kg`() {
        // 1 lb = 0.45359237 kg exactly (international avoirdupois pound), so 100 lb = 45.359237 kg.
        assertEquals(45.359237, WeightDisplay.toKg(100.0, WeightUnit.LB), 1e-12)
    }

    @Test
    fun `a stored 45_359237 kg displays as one hundred pounds`() {
        assertEquals(100.0, WeightDisplay.toDisplay(45.359237, WeightUnit.LB), 1e-9)
    }

    // --- The full round trip the cells perform (type → store → re-display) ---

    @Test
    fun `100 lb round-trips through kg storage back to the string 100`() {
        val storedKg = WeightDisplay.toKg(100.0, WeightUnit.LB) // what the cell writes to Room
        assertEquals(45.359237, storedKg, 1e-12)
        // What the cell shows when it re-derives its text from the stored kg.
        assertEquals("100", WeightDisplay.format(WeightDisplay.toDisplay(storedKg, WeightUnit.LB)))
    }

    @Test
    fun `common plate totals survive a lb round trip without drift`() {
        for (lb in listOf(45.0, 95.0, 135.0, 185.0, 225.0, 315.0, 2.5, 12.5)) {
            val storedKg = WeightDisplay.toKg(lb, WeightUnit.LB)
            assertEquals(WeightDisplay.format(lb), WeightDisplay.format(WeightDisplay.toDisplay(storedKg, WeightUnit.LB)))
        }
    }

    @Test
    fun `a kg-native value shown in lb converts at the boundary`() {
        // 100 kg = 220.462262... lb → two-decimal display "220.46".
        assertEquals("220.46", WeightDisplay.format(WeightDisplay.toDisplay(100.0, WeightUnit.LB)))
    }

    // --- format ---

    @Test
    fun `whole numbers render bare`() {
        assertEquals("100", WeightDisplay.format(100.0))
        assertEquals("0", WeightDisplay.format(0.0))
    }

    @Test
    fun `fractions render with up to two decimals and no trailing zeros`() {
        assertEquals("2.5", WeightDisplay.format(2.5))
        assertEquals("1.25", WeightDisplay.format(1.25))
        assertEquals("1.26", WeightDisplay.format(1.256)) // rounds, never truncates
    }

    @Test
    fun `format uses a dot separator regardless of locale and blanks non-finite input`() {
        // Locale.ROOT: the string must round-trip through String.toDoubleOrNull on comma locales.
        assertEquals("2.5", WeightDisplay.format(2.5))
        assertEquals("", WeightDisplay.format(Double.NaN))
        assertEquals("", WeightDisplay.format(Double.POSITIVE_INFINITY))
    }
}
