package com.enil.logez.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enil.logez.MainActivity
import com.enil.logez.R
import com.enil.logez.testutil.DEFAULT_WAIT_TIMEOUT_MS
import com.enil.logez.testutil.bottomNavTab
import com.enil.logez.testutil.pressSystemBack
import com.enil.logez.testutil.string
import com.enil.logez.testutil.waitUntilTextExists
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun weightUnit_changeToLb_persistsAfterNavigatingAway() {
        composeRule.bottomNavTab(R.string.nav_profile).performClick()

        val settingsTitle = composeRule.string(R.string.settings_title)
        composeRule.onNodeWithText(settingsTitle).performClick()

        val weightUnitTitle = composeRule.string(R.string.settings_weight_unit)
        composeRule.waitUntilTextExists(weightUnitTitle)
        composeRule.onNode(hasText(weightUnitTitle) and hasText("kg")).assertIsDisplayed()

        composeRule.onNodeWithText(weightUnitTitle).performClick()

        val poundsOption = composeRule.string(R.string.settings_weight_unit_lb)
        composeRule.waitUntilTextExists(poundsOption)
        composeRule.onNodeWithText(poundsOption).performClick()

        val weightUnitLbRow = hasText(weightUnitTitle) and hasText("lb")
        composeRule.waitUntil(DEFAULT_WAIT_TIMEOUT_MS) {
            composeRule.onAllNodes(weightUnitLbRow).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNode(weightUnitLbRow).assertIsDisplayed()

        composeRule.pressSystemBack()
        composeRule.bottomNavTab(R.string.nav_profile).assertIsSelected()

        composeRule.onNodeWithText(settingsTitle).performClick()
        composeRule.waitUntilTextExists(weightUnitTitle)
        composeRule.onNode(weightUnitLbRow).assertIsDisplayed()
    }
}
