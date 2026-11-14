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
import com.enil.logez.testutil.dismissNotificationPromptIfShown
import com.enil.logez.testutil.string
import com.enil.logez.testutil.waitUntilTextExists
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkoutStartSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startEmptyWorkout_opensLogger_discardReturnsToWorkoutTab() {
        composeRule.bottomNavTab(R.string.nav_workout).performClick()

        composeRule.waitUntilTextExists(composeRule.string(R.string.workout_empty_title))
        val startEmptyLabel = composeRule.string(R.string.workout_start_empty)
        composeRule.waitUntilTextExists(startEmptyLabel)
        composeRule.onNodeWithText(startEmptyLabel).performClick()

        composeRule.dismissNotificationPromptIfShown()

        val finishLabel = composeRule.string(R.string.workout_finish)
        composeRule.waitUntilTextExists(finishLabel)
        composeRule.onNodeWithText(finishLabel).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.string(R.string.routine_builder_add_exercise)).assertIsDisplayed()
        composeRule.assertBottomBarHidden()

        composeRule.onNodeWithContentDescription(composeRule.string(R.string.more_options)).performClick()

        val discardLabel = composeRule.string(R.string.workout_discard)
        composeRule.waitUntilTextExists(discardLabel)
        composeRule.onNodeWithText(discardLabel).performClick()

        composeRule.waitUntilTextExists(composeRule.string(R.string.workout_discard_confirm_title))
        composeRule.onNodeWithText(discardLabel).performClick()

        composeRule.waitUntilTextExists(startEmptyLabel)
        composeRule.onNodeWithText(startEmptyLabel).assertIsDisplayed()
        composeRule.assertBottomBarVisible()
        composeRule.bottomNavTab(R.string.nav_workout).assertIsSelected()
        composeRule.waitForIdle()
    }
}
