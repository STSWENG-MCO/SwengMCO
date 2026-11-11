package com.enil.logez.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
class ExerciseLibrarySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun search_benchPress_filtersList() {
        composeRule.bottomNavTab(R.string.nav_profile).performClick()
        composeRule.onNodeWithText(composeRule.string(R.string.profile_nav_exercises)).performClick()

        // Wait for SeedManager's initial 400-exercise insert to land in the LazyColumn.
        val nonMatchingExercise = "Ab Wheel Rollout"
        composeRule.waitUntilTextExists(nonMatchingExercise)
        composeRule.onNodeWithText(nonMatchingExercise).assertIsDisplayed()

        val searchHint = composeRule.string(R.string.exercise_library_search_hint)
        composeRule.onNodeWithText(searchHint).performTextInput("Bench Press")

        val matchingExercise = "Bench Press (Barbell)"
        composeRule.waitUntilTextExists(matchingExercise)
        composeRule.onNodeWithText(matchingExercise).assertIsDisplayed()
        composeRule.onNodeWithText(nonMatchingExercise).assertDoesNotExist()
    }
}
