package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.LengthUnit
import org.junit.Assert.assertEquals
import org.junit.Test

/** The single cm ↔ display-unit boundary for body-measurement input cells. */
class LengthDisplayTest {

    // --- Conversions ---

    @Test
    fun `CM passes through unchanged in both directions`() {
        assertEquals(100.0, LengthDisplay.toDisplay(100.0, LengthUnit.CM), 0.0)
        assertEquals(62.5, LengthDisplay.toCm(62.5, LengthUnit.CM), 0.0)
    }

    @Test
    fun `typing 100 in stores the exact legal definition in cm`() {
        // 1 in = 2.54 cm exactly (international inch), so 100 in = 254 cm.
        assertEquals(254.0, LengthDisplay.toCm(100.0, LengthUnit.IN), 1e-12)
    }

    @Test
    fun `a stored 254 cm displays as one hundred inches`() {
        assertEquals(100.0, LengthDisplay.toDisplay(254.0, LengthUnit.IN), 1e-9)
    }

    // --- The full round trip the cells perform (type -> store -> re-display) ---

    @Test
    fun `100 in round-trips through cm storage back to the string 100`() {
        val storedCm = LengthDisplay.toCm(100.0, LengthUnit.IN) // what the cell writes to Room
        assertEquals(254.0, storedCm, 1e-12)
        // What the cell shows when it re-derives its text from the stored cm.
        assertEquals("100", LengthDisplay.format(LengthDisplay.toDisplay(storedCm, LengthUnit.IN)))
    }

    @Test
    fun `common circumference values survive an inch round trip without drift`() {
        for (inches in listOf(12.0, 14.5, 34.0, 40.0, 22.5, 16.0)) {
            val storedCm = LengthDisplay.toCm(inches, LengthUnit.IN)
            assertEquals(LengthDisplay.format(inches), LengthDisplay.format(LengthDisplay.toDisplay(storedCm, LengthUnit.IN)))
        }
    }

    @Test
    fun `a cm-native value shown in inches converts at the boundary`() {
        // 100 cm = 39.3700787... in -> two-decimal display "39.37".
        assertEquals("39.37", LengthDisplay.format(LengthDisplay.toDisplay(100.0, LengthUnit.IN)))
    }

    // --- format ---

    @Test
    fun `whole numbers render bare`() {
        assertEquals("100", LengthDisplay.format(100.0))
        assertEquals("0", LengthDisplay.format(0.0))
    }

    @Test
    fun `fractions render with up to two decimals and no trailing zeros`() {
        assertEquals("2.5", LengthDisplay.format(2.5))
        assertEquals("1.25", LengthDisplay.format(1.25))
        assertEquals("1.26", LengthDisplay.format(1.256)) // rounds, never truncates
    }

    @Test
    fun `format uses a dot separator regardless of locale and blanks non-finite input`() {
        // Locale.ROOT: the string must round-trip through String.toDoubleOrNull on comma locales.
        assertEquals("2.5", LengthDisplay.format(2.5))
        assertEquals("", LengthDisplay.format(Double.NaN))
        assertEquals("", LengthDisplay.format(Double.POSITIVE_INFINITY))
    }
}
