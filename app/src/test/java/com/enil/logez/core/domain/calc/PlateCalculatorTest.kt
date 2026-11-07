package com.enil.logez.core.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M17 §5.1.5 Plate Calculator engine. Every expected value is a literal transcribed from working
 * the loading out by hand — never recomputed with the production formula (§10.1). The default
 * denomination set below mirrors [com.enil.logez.core.domain.model.PlateEquipment]'s default,
 * written out literally so a model-default change breaks this suite loudly instead of silently.
 */
class PlateCalculatorTest {

    private val defaultPlates = listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0)

    // --- Exact solves ---

    @Test
    fun `102_5 on a 20 bar loads 25 - 15 - 1_25 per side exactly`() {
        val result = PlateCalculator.solve(102.5, 20.0, defaultPlates)
        assertEquals(listOf(25.0, 15.0, 1.25), result.perSideKg)
        assertEquals(102.5, result.achievedKg, 0.0)
        assertTrue(result.exact)
        assertFalse(result.belowBar)
    }

    @Test
    fun `60 on a 20 bar is a single 20 per side`() {
        val result = PlateCalculator.solve(60.0, 20.0, defaultPlates)
        assertEquals(listOf(20.0), result.perSideKg)
        assertEquals(60.0, result.achievedKg, 0.0)
        assertTrue(result.exact)
    }

    @Test
    fun `fractional stacking - 22_5 on a 20 bar is one 1_25 per side`() {
        val result = PlateCalculator.solve(22.5, 20.0, defaultPlates)
        assertEquals(listOf(1.25), result.perSideKg)
        assertEquals(22.5, result.achievedKg, 0.0)
        assertTrue(result.exact)
    }

    @Test
    fun `target equal to the bar is exact with nothing loaded`() {
        val result = PlateCalculator.solve(20.0, 20.0, defaultPlates)
        assertEquals(emptyList<Double>(), result.perSideKg)
        assertEquals(20.0, result.achievedKg, 0.0)
        assertTrue(result.exact)
        assertFalse(result.belowBar)
    }

    @Test
    fun `heavy exact load stacks heaviest-first`() {
        // 170 on a 20 bar: 75 per side = 25 + 25 + 25.
        val result = PlateCalculator.solve(170.0, 20.0, defaultPlates)
        assertEquals(listOf(25.0, 25.0, 25.0), result.perSideKg)
        assertTrue(result.exact)
    }

    // --- Closest-achievable fallback ---

    @Test
    fun `101 on a 20 bar falls back to the closest achievable 100`() {
        val result = PlateCalculator.solve(101.0, 20.0, defaultPlates)
        assertEquals(listOf(25.0, 15.0), result.perSideKg)
        assertEquals(100.0, result.achievedKg, 0.0)
        assertFalse(result.exact)
        assertFalse(result.belowBar)
    }

    @Test
    fun `closest above wins when it is nearer than closest below`() {
        // Only 5s owned, 20 bar, target 29: totals step by 10 (20, 30, 40...).
        // 30 is 1 away, 20 is 9 away -> 30 via one 5 per side.
        val result = PlateCalculator.solve(29.0, 20.0, listOf(5.0))
        assertEquals(listOf(5.0), result.perSideKg)
        assertEquals(30.0, result.achievedKg, 0.0)
        assertFalse(result.exact)
    }

    @Test
    fun `equidistant closest weights tie toward the lower total`() {
        // Only 5s owned, 20 bar, target 25: 20 and 30 are both 5 away -> keep 20 (bar alone).
        val result = PlateCalculator.solve(25.0, 20.0, listOf(5.0))
        assertEquals(emptyList<Double>(), result.perSideKg)
        assertEquals(20.0, result.achievedKg, 0.0)
        assertFalse(result.exact)
        assertFalse(result.belowBar)
    }

    // --- Sub-bar and degenerate equipment ---

    @Test
    fun `target below the bar signals bar-alone`() {
        val result = PlateCalculator.solve(15.0, 20.0, defaultPlates)
        assertTrue(result.belowBar)
        assertEquals(emptyList<Double>(), result.perSideKg)
        assertEquals(20.0, result.achievedKg, 0.0)
        assertFalse(result.exact)
    }

    @Test
    fun `no plate denominations makes the bar the closest achievable weight`() {
        val result = PlateCalculator.solve(100.0, 20.0, emptyList())
        assertEquals(emptyList<Double>(), result.perSideKg)
        assertEquals(20.0, result.achievedKg, 0.0)
        assertFalse(result.exact)
        assertFalse(result.belowBar)
    }

    // --- Optimality beyond greedy ---

    @Test
    fun `pathological denominations beat greedy - 3 plus 3 reaches the 6 per side that greedy misses`() {
        // Plates of 4 and 3, 20 bar, target 32 -> 6 per side. Greedy heaviest-first loads a 4,
        // cannot fit anything else (6 - 4 = 2), and would stop at 28. The DP finds 3 + 3 exactly.
        val result = PlateCalculator.solve(32.0, 20.0, listOf(4.0, 3.0))
        assertEquals(listOf(3.0, 3.0), result.perSideKg)
        assertEquals(32.0, result.achievedKg, 0.0)
        assertTrue(result.exact)
    }

    @Test
    fun `default denominations still load like greedy where greedy is right`() {
        // 142.5 on a 20 bar: 61.25 per side = 25 + 25 + 10 + 1.25 (fewest plates, heaviest first).
        val result = PlateCalculator.solve(142.5, 20.0, defaultPlates)
        assertEquals(listOf(25.0, 25.0, 10.0, 1.25), result.perSideKg)
        assertTrue(result.exact)
    }

    // --- Quarter-kg entry rounding ---

    @Test
    fun `roundToQuarterKg snaps to the nearest quarter`() {
        assertEquals(1.0, PlateCalculator.roundToQuarterKg(1.1), 0.0)
        assertEquals(1.25, PlateCalculator.roundToQuarterKg(1.13), 0.0)
        assertEquals(2.5, PlateCalculator.roundToQuarterKg(2.5), 0.0)
        assertEquals(20.0, PlateCalculator.roundToQuarterKg(20.0), 0.0)
    }

    @Test
    fun `an off-grid denomination is rounded to the quarter grid before solving`() {
        // 1.1 rounds to 1.0, so 22 on a 20 bar is exactly one 1 kg plate per side.
        val result = PlateCalculator.solve(22.0, 20.0, listOf(1.1))
        assertEquals(listOf(1.0), result.perSideKg)
        assertEquals(22.0, result.achievedKg, 0.0)
        assertTrue(result.exact)
    }

    @Test
    fun `duplicate and non-positive denominations are ignored, not double-counted`() {
        val result = PlateCalculator.solve(30.0, 20.0, listOf(5.0, 5.0, 0.0, -2.5))
        assertEquals(listOf(5.0), result.perSideKg)
        assertEquals(30.0, result.achievedKg, 0.0)
        assertTrue(result.exact)
    }

    @Test
    fun `long 1_25 stacks accumulate with no float drift`() {
        // 32.5 per side out of pure 1.25s = 26 plates; any Double accumulation would drift off 85.
        val result = PlateCalculator.solve(85.0, 20.0, listOf(1.25))
        assertEquals(26, result.perSideKg.size)
        assertTrue(result.perSideKg.all { it == 1.25 })
        assertEquals(85.0, result.achievedKg, 0.0)
        assertTrue(result.exact)
    }
}
