package com.enil.logez.designsystem

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.designsystem.MuscleBalanceRadar
import com.enil.logez.core.domain.calc.MuscleStatsCalculator
import com.enil.logez.core.domain.calc.RegionShare
import com.enil.logez.core.domain.calc.balanceAxes
import com.enil.logez.core.domain.model.MuscleGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MuscleBalanceRadarTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val axisLabelRes =
        listOf(
            R.string.muscle_region_chest_axis,
            R.string.muscle_region_back_axis,
            R.string.muscle_region_shoulders_axis,
            R.string.muscle_region_arms_axis,
            R.string.muscle_region_core_axis,
            R.string.muscle_region_quads_axis,
            R.string.muscle_region_hamstrings_glutes_axis,
            R.string.muscle_region_lower_leg_axis,
        )

    private fun text(resId: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun sampleShares(): List<RegionShare> =
        balanceAxes(
            listOf(
                MuscleStatsCalculator.GroupShare(MuscleGroup.CHEST, 3, 0),
                MuscleStatsCalculator.GroupShare(MuscleGroup.LATS, 1, 0),
                MuscleStatsCalculator.GroupShare(MuscleGroup.QUADRICEPS, 2, 0),
            ),
        )

    private fun assertAllEightLabelsShown() {
        check(axisLabelRes.size == 8)
        axisLabelRes.forEach { res ->
            // The radar upper-cases its spoke labels, so match case-insensitively.
            composeRule.onNodeWithText(text(res), ignoreCase = true).assertIsDisplayed()
        }
    }

    @Test
    fun muscleBalanceRadar_withData_showsAllEightAxisLabels() {
        composeRule.setContent { LogEzTheme { MuscleBalanceRadar(shares = sampleShares()) } }

        assertAllEightLabelsShown()
    }

    @Test
    fun muscleBalanceRadar_compact_showsAllEightAxisLabels() {
        composeRule.setContent { LogEzTheme { MuscleBalanceRadar(shares = sampleShares(), compact = true) } }

        assertAllEightLabelsShown()
    }

    @Test
    fun muscleBalanceRadar_withNoRegionSets_showsMessageNotAnEmptyWheel() {
        composeRule.setContent { LogEzTheme { MuscleBalanceRadar(shares = balanceAxes(emptyList())) } }

        composeRule.onNodeWithText(text(R.string.muscle_balance_no_regions)).assertIsDisplayed()
    }
}
