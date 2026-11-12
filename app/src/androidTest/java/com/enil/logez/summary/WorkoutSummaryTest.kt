package com.enil.logez.summary

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.times
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enil.logez.MainActivity
import com.enil.logez.R
import com.enil.logez.testutil.assertBottomBarVisible
import com.enil.logez.testutil.bottomNavTab
import com.enil.logez.testutil.dismissNotificationPromptIfShown
import com.enil.logez.testutil.string
import com.enil.logez.testutil.waitUntilTextExists
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class WorkoutSummaryTest {

    private val grantPermissionsRule = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val pkg = instrumentation.targetContext.packageName
                val uiAutomation = instrumentation.uiAutomation
                val permissions = listOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    permissions.forEach { uiAutomation.grantRuntimePermission(pkg, it) }
                } else {
                    permissions.forEach { permission ->
                        uiAutomation.executeShellCommand("pm grant $pkg $permission").close()
                    }
                }
                base.evaluate()
            }
        }
    }

    val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val ruleChain: RuleChain = RuleChain.outerRule(grantPermissionsRule).around(composeRule)

    @Test
    fun finishSaveSummaryDone_navigatesToHistoryTab() {
        val interruptedDiscardLabel = composeRule.string(R.string.activity_tracking_interrupted_discard)
        waitForComposeAndDismissInterrupted(interruptedDiscardLabel)

        // 1. Open Workout tab and start an empty workout
        composeRule.bottomNavTab(R.string.nav_workout).performClick()
        val startEmptyLabel = composeRule.string(R.string.workout_start_empty)
        composeRule.waitUntilTextExists(startEmptyLabel)
        composeRule.onNodeWithText(startEmptyLabel).performClick()

        // 2. Wait for active workout screen, discarding any async Room conflict dialog if shown
        val addExerciseLabel = composeRule.string(R.string.routine_builder_add_exercise)
        val discardLabel = composeRule.string(R.string.workout_resume_discard_action)
        composeRule.waitUntil(timeoutMillis = 8_000) {
            if (composeRule.onAllNodesWithText(discardLabel).fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithText(discardLabel).performClick()
            }
            if (composeRule.onAllNodesWithText(interruptedDiscardLabel).fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithText(interruptedDiscardLabel).performClick()
            }
            composeRule.dismissNotificationPromptIfShown()
            composeRule.onAllNodesWithText(addExerciseLabel).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(addExerciseLabel).performClick()

        // 3. Select the first exercise in the list and tap the "Add 1..." confirm button
        val createRowLabel = composeRule.string(R.string.exercise_picker_create_row)
        composeRule.waitUntilTextExists(createRowLabel)
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodes(isToggleable()).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodes(isToggleable()).onFirst().performClick()

        val confirmAddLabel =
            composeRule.activity.resources.getQuantityString(R.plurals.exercise_picker_add_n, 1, 1)
        composeRule.onNodeWithText(confirmAddLabel).performClick()

        // 4. Check off the 1st set so FinishWorkoutScreen allows saving
        val checkSetDesc = composeRule.string(R.string.workout_check_set)
        composeRule.waitUntil(timeoutMillis = 8_000) {
            composeRule.onAllNodesWithContentDescription(checkSetDesc).fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onAllNodesWithContentDescription(checkSetDesc).onFirst().performClick()

        // 5. Tap Finish -> Save
        val finishLabel = composeRule.string(R.string.workout_finish)
        composeRule.onNodeWithText(finishLabel).performClick()

        val saveLabel = composeRule.string(R.string.finish_save)
        composeRule.waitUntilTextExists(saveLabel)
        composeRule.onNodeWithText(saveLabel).performScrollTo().performClick()

        // 6. Verify Summary screen renders -> Tap Done
        val summaryTitle = composeRule.string(R.string.summary_title)
        composeRule.waitUntilTextExists(summaryTitle)
        composeRule.onNodeWithText(summaryTitle).assertIsDisplayed()

        val doneLabel = composeRule.string(R.string.summary_done)
        composeRule.onNodeWithText(doneLabel).performScrollTo().performClick()

        // 7. Verify Done navigates to the History tab
        composeRule.assertBottomBarVisible()
        composeRule.bottomNavTab(R.string.nav_history).assertIsSelected()
        composeRule.waitForIdle()
    }

    @Test
    fun gpsActivityFinishSaveSummaryDone_navigatesToHistoryTab() {
        val interruptedDiscardLabel = composeRule.string(R.string.activity_tracking_interrupted_discard)
        waitForComposeAndDismissInterrupted(interruptedDiscardLabel)

        // 1. Open Workout tab, scroll QuickTrackCard into view, and lift it above the pinned bottom bar
        composeRule.bottomNavTab(R.string.nav_workout).performClick()
        val runButtonLabel = composeRule.string(R.string.workout_track_walk_run_running)
        composeRule.waitUntil(timeoutMillis = 8_000) {
            runCatching {
                composeRule.onNode(hasScrollAction())
                    .performScrollToNode(hasText(runButtonLabel))
                true
            }.getOrDefault(false)
        }
        val listNode = composeRule.onNode(hasScrollAction())
        val listBounds = listNode.getUnclippedBoundsInRoot()
        val buttonBounds = composeRule.onNodeWithText(runButtonLabel).getUnclippedBoundsInRoot()
        if (buttonBounds.bottom > listBounds.bottom * 0.65f) {
            listNode.performTouchInput {
                swipeUp(startY = centerY, endY = top + 120f, durationMillis = 400)
            }
        }
        composeRule.onNodeWithText(runButtonLabel).performClick()

        // 2. Wait for live tracking screen (ScreenTitle renders uppercase "TRACKING") -> Tap Finish
        val trackingTitle = composeRule.string(R.string.activity_tracking_screen_title)
        val rationaleAllowLabel = composeRule.string(R.string.activity_tracking_location_rationale_allow)
        val resumeDiscardLabel = composeRule.string(R.string.workout_resume_discard_action)
        composeRule.waitUntil(timeoutMillis = 8_000) {
            if (composeRule.onAllNodesWithText(rationaleAllowLabel).fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithText(rationaleAllowLabel).performClick()
            }
            if (composeRule.onAllNodesWithText(resumeDiscardLabel).fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithText(resumeDiscardLabel).performClick()
            }
            if (composeRule.onAllNodesWithText(interruptedDiscardLabel).fetchSemanticsNodes().isNotEmpty()) {
                composeRule.onNodeWithText(interruptedDiscardLabel).performClick()
            }
            composeRule.onAllNodesWithText(trackingTitle, ignoreCase = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(trackingTitle, ignoreCase = true).assertIsDisplayed()

        val finishTrackingLabel = composeRule.string(R.string.activity_tracking_finish)
        composeRule.onNodeWithText(finishTrackingLabel).performClick()

        // 3. Save on FinishWorkoutScreen
        val saveLabel = composeRule.string(R.string.finish_save)
        composeRule.waitUntilTextExists(saveLabel)
        composeRule.onNodeWithText(saveLabel).performScrollTo().performClick()

        // 4. Verify GpsWorkoutSummary renders (time card, title, optional empty-route notice) -> Tap Done
        val gpsTimeLabel = composeRule.string(R.string.summary_gps_time)
        composeRule.waitUntilTextExists(gpsTimeLabel)
        composeRule.onNodeWithText(gpsTimeLabel).assertIsDisplayed()
        composeRule.onAllNodesWithText("Running (Outdoor)").onFirst().assertIsDisplayed()

        val doneLabel = composeRule.string(R.string.summary_done)
        composeRule.onNodeWithText(doneLabel).performScrollTo()

        val routeEmptyLabel = composeRule.string(R.string.summary_gps_route_empty)
        if (composeRule.onAllNodesWithText(routeEmptyLabel).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(routeEmptyLabel).assertIsDisplayed()
        }

        composeRule.onNodeWithText(doneLabel).performClick()

        // 5. Verify Done navigates to the History tab and the saved run appears in the list
        composeRule.assertBottomBarVisible()
        composeRule.bottomNavTab(R.string.nav_history).assertIsSelected()
        composeRule.waitUntilTextExists("Running (Outdoor)")
        composeRule.onAllNodesWithText("Running (Outdoor)").onFirst().assertIsDisplayed()
        composeRule.waitForIdle()
    }

    private fun waitForComposeAndDismissInterrupted(interruptedDiscardLabel: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                composeRule.onAllNodesWithText(interruptedDiscardLabel).fetchSemanticsNodes()
            }.isSuccess
        }
        if (composeRule.onAllNodesWithText(interruptedDiscardLabel).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText(interruptedDiscardLabel).performClick()
            composeRule.waitForIdle()
        }
    }
}