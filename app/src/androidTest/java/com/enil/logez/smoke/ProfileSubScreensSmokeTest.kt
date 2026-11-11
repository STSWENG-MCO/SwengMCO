package com.enil.logez.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enil.logez.MainActivity
import com.enil.logez.R
import com.enil.logez.testutil.assertBottomBarHidden
import com.enil.logez.testutil.assertBottomBarVisible
import com.enil.logez.testutil.bottomNavTab
import com.enil.logez.testutil.pressSystemBack
import com.enil.logez.testutil.string
import com.enil.logez.testutil.waitUntilTextExists
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileSubScreensSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun profile_openExercises_hidesBottomBar_backReturns() {
        composeRule.bottomNavTab(R.string.nav_profile).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.profile_nav_exercises)).performClick()

        val searchHint = composeRule.string(R.string.exercise_library_search_hint)
        composeRule.waitUntilTextExists(searchHint)
        composeRule.onNodeWithText(searchHint).assertIsDisplayed()
        composeRule.assertBottomBarHidden()

        composeRule.pressSystemBack()
        composeRule.assertBottomBarVisible()
        composeRule.bottomNavTab(R.string.nav_profile).assertIsSelected()
    }

    @Test
    fun profile_openStatistics_hidesBottomBar_backReturns() {
        composeRule.bottomNavTab(R.string.nav_profile).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.profile_nav_statistics)).performClick()

        val emptyStatsTitle = composeRule.string(R.string.profile_empty_title)
        composeRule.waitUntilTextExists(emptyStatsTitle)
        composeRule.onNodeWithText(emptyStatsTitle).assertIsDisplayed()
        composeRule.assertBottomBarHidden()

        composeRule.pressSystemBack()
        composeRule.assertBottomBarVisible()
        composeRule.bottomNavTab(R.string.nav_profile).assertIsSelected()
    }

    @Test
    fun profile_openCalendar_hidesBottomBar_backReturns() {
        composeRule.bottomNavTab(R.string.nav_profile).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.profile_calendar_row)).performClick()

        val noStreakBanner = composeRule.string(R.string.profile_no_streak)
        composeRule.waitUntilTextExists(noStreakBanner)
        composeRule.onNodeWithContentDescription(composeRule.string(R.string.calendar_first_day_of_week)).assertIsDisplayed()
        composeRule.assertBottomBarHidden()

        composeRule.pressSystemBack()
        composeRule.assertBottomBarVisible()
        composeRule.bottomNavTab(R.string.nav_profile).assertIsSelected()
    }

    @Test
    fun profile_openMeasurements_hidesBottomBar_backReturns() {
        composeRule.bottomNavTab(R.string.nav_profile).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.profile_measurements_row)).performClick()

        val trackingModeTitle = composeRule.string(R.string.measurements_tracking_mode)
        composeRule.waitUntilTextExists(trackingModeTitle)
        composeRule.onNodeWithText(trackingModeTitle).assertIsDisplayed()
        composeRule.assertBottomBarHidden()

        composeRule.pressSystemBack()
        composeRule.assertBottomBarVisible()
        composeRule.bottomNavTab(R.string.nav_profile).assertIsSelected()
    }

    @Test
    fun profile_openSettings_hidesBottomBar_backReturns() {
        composeRule.bottomNavTab(R.string.nav_profile).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.settings_title)).performClick()

        val weightUnitTitle = composeRule.string(R.string.settings_weight_unit)
        composeRule.waitUntilTextExists(weightUnitTitle)
        composeRule.onNodeWithText(weightUnitTitle).assertIsDisplayed()
        composeRule.assertBottomBarHidden()

        composeRule.pressSystemBack()
        composeRule.assertBottomBarVisible()
        composeRule.bottomNavTab(R.string.nav_profile).assertIsSelected()
    }
}
