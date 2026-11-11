package com.enil.logez.testutil

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.rules.ActivityScenarioRule
import com.enil.logez.MainActivity
import com.enil.logez.R

const val DEFAULT_WAIT_TIMEOUT_MS = 5_000L

typealias MainActivityComposeRule = AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>

fun MainActivityComposeRule.string(
    @StringRes resId: Int,
): String = activity.getString(resId)

/**
 * Matches a bottom-navigation item by its label resource and selectable semantics, avoiding
 * collisions with top-bar headers or screen body text that share the same word.
 */
fun MainActivityComposeRule.bottomNavTab(
    @StringRes labelRes: Int,
): SemanticsNodeInteraction = onNode(hasText(string(labelRes)) and isSelectable())

fun MainActivityComposeRule.assertBottomBarVisible() {
    bottomNavTab(R.string.nav_history).assertIsDisplayed()
    bottomNavTab(R.string.nav_workout).assertIsDisplayed()
    bottomNavTab(R.string.nav_profile).assertIsDisplayed()
}

fun MainActivityComposeRule.assertBottomBarHidden() {
    bottomNavTab(R.string.nav_history).assertDoesNotExist()
    bottomNavTab(R.string.nav_workout).assertDoesNotExist()
    bottomNavTab(R.string.nav_profile).assertDoesNotExist()
}

fun MainActivityComposeRule.pressSystemBack() {
    runOnUiThread {
        activity.onBackPressedDispatcher.onBackPressed()
    }
    waitForIdle()
}

fun MainActivityComposeRule.waitUntilTextExists(
    text: String,
    timeoutMillis: Long = DEFAULT_WAIT_TIMEOUT_MS,
) {
    waitUntil(timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

/**
 * POST_NOTIFICATIONS is a runtime permission only on API 33+, so the pre-permission rationale
 * dialog appears on the API 36 emulator and is skipped entirely on API 26. Waits for either
 * the dialog or the logger screen to land, then dismisses the prompt if present.
 */
fun MainActivityComposeRule.dismissNotificationPromptIfShown(timeoutMillis: Long = DEFAULT_WAIT_TIMEOUT_MS) {
    val notNowLabel = string(R.string.workout_notification_permission_not_now)
    val finishLabel = string(R.string.workout_finish)
    waitUntil(timeoutMillis) {
        onAllNodesWithText(notNowLabel).fetchSemanticsNodes().isNotEmpty() ||
            onAllNodesWithText(finishLabel).fetchSemanticsNodes().isNotEmpty()
    }
    if (onAllNodesWithText(notNowLabel).fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithText(notNowLabel).performClick()
    }
}
