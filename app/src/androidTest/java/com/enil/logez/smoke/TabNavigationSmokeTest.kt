package com.enil.logez.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.enil.logez.MainActivity
import com.enil.logez.R
import com.enil.logez.testutil.bottomNavTab
import com.enil.logez.testutil.string
import com.enil.logez.testutil.waitUntilTextExists
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabNavigationSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun workoutTab_tap_showsStartEmptyWorkout() {
        composeRule.bottomNavTab(R.string.nav_workout).performClick()

        val startEmptyLabel = composeRule.string(R.string.workout_start_empty)
        composeRule.waitUntilTextExists(startEmptyLabel)

        composeRule.onNodeWithText(startEmptyLabel).assertIsDisplayed()
        composeRule.bottomNavTab(R.string.nav_workout).assertIsSelected()
    }

    @Test
    fun profileTab_tap_showsEmptyStats() {
        composeRule.bottomNavTab(R.string.nav_profile).performClick()

        val workoutsLabel = composeRule.string(R.string.profile_stat_workouts)
        val last7EmptyHint = composeRule.string(R.string.profile_last7_empty)
        composeRule.waitUntilTextExists(last7EmptyHint)

        composeRule.onNodeWithText(workoutsLabel).assertIsDisplayed()
        composeRule.onNodeWithText(last7EmptyHint).assertIsDisplayed()
        composeRule.bottomNavTab(R.string.nav_profile).assertIsSelected()
    }

    @Test
    fun historyTab_tapAfterOtherTab_returnsToHistory() {
        composeRule.bottomNavTab(R.string.nav_workout).performClick()
        composeRule.bottomNavTab(R.string.nav_workout).assertIsSelected()

        composeRule.bottomNavTab(R.string.nav_history).performClick()

        val emptyTitle = composeRule.string(R.string.history_empty_title)
        composeRule.waitUntilTextExists(emptyTitle)
        composeRule.onNodeWithText(emptyTitle).assertIsDisplayed()
        composeRule.bottomNavTab(R.string.nav_history).assertIsSelected()
    }
}
