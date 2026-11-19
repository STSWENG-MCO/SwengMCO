package com.enil.logez.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enil.logez.MainActivity
import com.enil.logez.R
import com.enil.logez.testutil.assertBottomBarVisible
import com.enil.logez.testutil.bottomNavTab
import com.enil.logez.testutil.string
import com.enil.logez.testutil.waitUntilTextExists
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LaunchSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun coldStart_opensHistoryTab_withEmptyState() {
        val emptyTitle = composeRule.string(R.string.history_empty_title)
        composeRule.waitUntilTextExists(emptyTitle)

        composeRule.onNodeWithText(emptyTitle).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.history_empty_subtitle)).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.history_empty_cta)).assertIsDisplayed()
        composeRule.bottomNavTab(R.string.nav_history).assertIsSelected()
    }

    @Test
    fun coldStart_showsBottomNav_withThreeTabs() {
        composeRule.assertBottomBarVisible()
        composeRule.bottomNavTab(R.string.nav_history).assertIsSelected()
        composeRule.bottomNavTab(R.string.nav_workout).assertIsNotSelected()
        composeRule.bottomNavTab(R.string.nav_profile).assertIsNotSelected()
    }
}
