package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.calc.WarmupCalculator.WarmupSetPlan
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.WarmupStep
import com.enil.logez.core.domain.model.defaultWarmupMethod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M18 §5.1.6: the warm-up ladder generator, checked with literal hand-computed values. */
class WarmupCalculatorTest {

    private val defaultEquipment = PlateEquipment() // 20 kg bar; 1.25/2.5/5/10/15/20/25 plates

    @Test
    fun `100 kg barbell with the default method yields the canonical 40-60-80 ladder`() {
        // 40% = 40 kg = bar 20 + 2×10 (exact) · 60% = 60 = 20 + 2×20 · 80% = 80 = 20 + 2×(25+5).
        val plan = WarmupCalculator.generate(100.0, defaultWarmupMethod, barbell = true, equipment = defaultEquipment)
        assertEquals(
            listOf(
                WarmupSetPlan(weightKg = 40.0, reps = 5),
                WarmupSetPlan(weightKg = 60.0, reps = 5),
                WarmupSetPlan(weightKg = 80.0, reps = 3),
            ),
            plan,
        )
    }

    @Test
    fun `a barbell step the plates cannot load lands on the closest loadable weight`() {
        // Equipment: 20 kg bar, ONLY 5 kg plates → loadable totals are 20, 30, 40, 50, ...
        // Working 103 kg at 40% = 41.2 kg. Candidates: 40 (off by 1.2) vs 50 (off by 8.8) → 40.
        val sparse = PlateEquipment(barsKg = listOf(20.0), platesKg = listOf(5.0))
        val plan = WarmupCalculator.generate(103.0, listOf(WarmupStep(percent = 0.40, reps = 5)), barbell = true, equipment = sparse)
        assertEquals(listOf(WarmupSetPlan(weightKg = 40.0, reps = 5)), plan)
    }

    @Test
    fun `a barbell step below the bar clamps to the bar itself`() {
        // Working 40 kg at 40% = 16 kg < the 20 kg bar → the empty bar is the warm-up weight.
        val plan = WarmupCalculator.generate(40.0, listOf(WarmupStep(percent = 0.40, reps = 5)), barbell = true, equipment = defaultEquipment)
        assertEquals(listOf(WarmupSetPlan(weightKg = 20.0, reps = 5)), plan)
    }

    @Test
    fun `non-barbell steps round to the 2 point 5 kg grid`() {
        // Working 41 kg: 40% = 16.4 → 17.5 (16.4/2.5 = 6.56 → 7 increments) ·
        // 60% = 24.6 → 25 (9.84 → 10) · 80% = 32.8 → 32.5 (13.12 → 13).
        val plan = WarmupCalculator.generate(41.0, defaultWarmupMethod, barbell = false, equipment = null)
        assertEquals(
            listOf(
                WarmupSetPlan(weightKg = 17.5, reps = 5),
                WarmupSetPlan(weightKg = 25.0, reps = 5),
                WarmupSetPlan(weightKg = 32.5, reps = 3),
            ),
            plan,
        )
    }

    @Test
    fun `a non-barbell step that would round to zero keeps its raw percent weight`() {
        // Working 2 kg at 40% = 0.8 → nearest 2.5 increment is 0, which is meaningless — the raw
        // 0.8 kg is the documented answer (see the engine's class doc).
        val plan = WarmupCalculator.generate(2.0, listOf(WarmupStep(percent = 0.40, reps = 5)), barbell = false, equipment = null)
        assertEquals(1, plan.size)
        assertEquals(0.8, plan[0].weightKg, 1e-12)
    }

    @Test
    fun `a barbell exercise with no equipment falls back to the increment grid`() {
        // 100 kg at 40% = 40, already on the 2.5 grid.
        val plan = WarmupCalculator.generate(100.0, listOf(WarmupStep(percent = 0.40, reps = 5)), barbell = true, equipment = null)
        assertEquals(listOf(WarmupSetPlan(weightKg = 40.0, reps = 5)), plan)
    }

    @Test
    fun `an empty method yields no sets`() {
        assertTrue(WarmupCalculator.generate(100.0, emptyList(), barbell = true, equipment = defaultEquipment).isEmpty())
    }

    @Test
    fun `a non-positive or NaN working weight yields no sets`() {
        assertTrue(WarmupCalculator.generate(0.0, defaultWarmupMethod, barbell = false, equipment = null).isEmpty())
        assertTrue(WarmupCalculator.generate(-10.0, defaultWarmupMethod, barbell = false, equipment = null).isEmpty())
        assertTrue(WarmupCalculator.generate(Double.NaN, defaultWarmupMethod, barbell = false, equipment = null).isEmpty())
    }

    @Test
    fun `reps come from the method's own rows, per step`() {
        val method = listOf(WarmupStep(percent = 0.50, reps = 8), WarmupStep(percent = 0.75, reps = 2))
        val plan = WarmupCalculator.generate(100.0, method, barbell = false, equipment = null)
        // 50 and 75 are both on the 2.5 grid, so the weights are exact.
        assertEquals(listOf(WarmupSetPlan(50.0, 8), WarmupSetPlan(75.0, 2)), plan)
    }
}
