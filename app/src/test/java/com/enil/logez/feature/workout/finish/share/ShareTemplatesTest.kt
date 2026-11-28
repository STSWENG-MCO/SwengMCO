package com.enil.logez.feature.workout.finish.share

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.ShareBackground
import com.enil.logez.core.domain.model.ShareTemplate
import com.enil.logez.core.domain.model.ShareTemplateChoice
import com.enil.logez.core.domain.model.ShareTemplateRegistry
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStructure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ShareTemplatesTest {
    @get:Rule
    val compose = createComposeRule()

    private val splits =
        listOf(
            ShareSplit(1, 380.0, "6:20"),
            ShareSplit(2, 366.0, "6:06"),
            ShareSplit(3, 375.0, "6:15"),
            ShareSplit(4, 346.0, "5:46"),
        )

    private val run =
        GpsShareCardData(
            eyebrow = "Run complete",
            routePoints = listOf(14.0 to 121.0, 14.001 to 121.002, 14.002 to 121.001, 14.003 to 121.003),
            distanceNumber = "4.79",
            distanceUnitLabel = "km",
            timeText = "29:21",
            paceText = "6:07",
            paceLabel = "Pace /km",
            avgBpmText = "150",
            prLines = listOf("Fastest km" to "5:46"),
            splits = splits,
        )

    private fun data(
        kind: WorkoutKind,
        template: ShareTemplate,
        title: String = if (kind == WorkoutKind.GPS_TRACKED) "Running (Outdoor)" else "Push Day",
        background: ShareBackground = ShareBackground.DARK,
    ): ShareCardData {
        val base = ShareTemplateRegistry.defaultChoice(kind)
        val choice = ShareTemplateChoice(template, base.customization.copy(background = background))
        return ShareCardData(
            title = title,
            dateLine = "Fri 9 Oct 2026",
            durationText = "52m",
            volumeText = "4,382 kg",
            setsText = "13",
            repsText = "117",
            muscleIntensity = mapOf(MuscleGroup.CHEST to 1f, MuscleGroup.SHOULDERS to 0.6f),
            muscleBalance = emptyList(),
            prs = emptyList(),
            exerciseLines = emptyList(),
            structure = WorkoutStructure.REGULAR,
            rounds = 0,
            gps = if (kind == WorkoutKind.GPS_TRACKED) run else null,
            choice = choice,
            records =
                listOf(
                    ShareRecord("Bench Press (Barbell)", "Heaviest weight", "72.5 kg"),
                    ShareRecord("Bench Press (Barbell)", "Best 1RM", "91.8 kg"),
                    ShareRecord("Triceps Pushdown (Cable)", "Heaviest weight", "27.5 kg"),
                    ShareRecord("Overhead Press (Barbell)", "Best 1RM", "50 kg"),
                ),
            exercises =
                listOf(
                    ShareExercise("Bench Press (Barbell)", "72.5 × 8", "1,720 kg", listOf("70 × 8", "72.5 × 8"), true),
                    ShareExercise("Overhead Press (Barbell)", "40 × 8", "940 kg", listOf("40 × 8"), false),
                ),
            muscleSets = mapOf(MuscleGroup.CHEST to 7, MuscleGroup.SHOULDERS to 4, MuscleGroup.TRICEPS to 2),
            streak = ShareStreak(9, 3, 4, listOf(2, 3, 4, 4, 4, 4, 4, 4, 4, 4, 4, 2), 8, "October"),
        )
    }

    /** Text only this layout draws; the layouts that share a heading are identified by the title. */
    private fun marker(
        template: ShareTemplate,
        title: String,
    ): String =
        when (template) {
            ShareTemplate.RECORDS -> "NEW PERSONAL RECORD"
            ShareTemplate.MUSCLES -> "MUSCLES TRAINED"
            ShareTemplate.LOG -> "WORKOUT LOG"
            ShareTemplate.STREAK -> "WEEK STREAK"
            else -> title
        }

    private fun assertShows(
        text: String,
        message: String,
    ) {
        assertTrue(message, compose.onAllNodesWithText(text, ignoreCase = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun `every template renders for each kind that supports it`() {
        val shown = mutableStateOf(data(WorkoutKind.STRENGTH, ShareTemplate.STACK))
        compose.setContent { ShareCard(shown.value, ShareCardFormat.STORY, Modifier.testTag(SHARE_CARD_TAG)) }

        ShareTemplate.entries.forEach { template ->
            WorkoutKind.entries.filter { it in template.kinds }.forEach { kind ->
                val card = data(kind, template)
                assertEquals("$template should be selectable for $kind", template, card.resolvedChoice().template)
                compose.runOnIdle { shown.value = card }
                compose.waitForIdle()
                compose.onNodeWithTag(SHARE_CARD_TAG).assertExists("$template on $kind drew no card")
                val text = marker(template, card.title)
                assertShows(text, "$template on $kind is missing '$text'")
            }
        }
    }

    @Test
    fun `stack draws on every ground`() {
        val shown = mutableStateOf(data(WorkoutKind.STRENGTH, ShareTemplate.STACK))
        compose.setContent { ShareCard(shown.value, ShareCardFormat.STORY, Modifier.testTag(SHARE_CARD_TAG)) }
        ShareBackground.entries.forEach { ground ->
            compose.runOnIdle { shown.value = data(WorkoutKind.STRENGTH, ShareTemplate.STACK, background = ground) }
            compose.waitForIdle()
            compose.onNodeWithTag(SHARE_CARD_TAG).assertExists("stack on $ground drew no card")
            compose.onNodeWithText("Push Day").assertExists()
        }
    }

    @Test
    fun `a very long title still renders in every layout that shows it`() {
        val longTitle = "Upper Body Hypertrophy Block Week 7 Heavy Push Day With Extra Accessory Volume And Finishers"
        val shown = mutableStateOf(data(WorkoutKind.STRENGTH, ShareTemplate.STACK, title = longTitle))
        compose.setContent { ShareCard(shown.value, ShareCardFormat.STORY, Modifier.testTag(SHARE_CARD_TAG)) }
        listOf(
            ShareTemplate.STACK,
            ShareTemplate.GLASS,
            ShareTemplate.STRIP,
            ShareTemplate.RAIL,
            ShareTemplate.NUMBER,
            ShareTemplate.BODY_MAP,
            ShareTemplate.SUMMARY,
            ShareTemplate.LOG,
            ShareTemplate.MUSCLES,
        ).forEach { template ->
            compose.runOnIdle { shown.value = data(WorkoutKind.STRENGTH, template, title = longTitle) }
            compose.waitForIdle()
            compose.onNodeWithText(longTitle).assertExists("$template dropped a long title")
        }
    }

    @Test
    fun `a saved template the workout cannot use falls back to stack`() {
        val card = data(WorkoutKind.STRENGTH, ShareTemplate.ROUTE)
        assertEquals(ShareTemplate.STACK, card.resolvedChoice().template)
        compose.setContent { ShareCard(card, ShareCardFormat.STORY, Modifier.testTag(SHARE_CARD_TAG)) }
        assertShows("WORKOUT COMPLETE", "stack fallback did not draw the Stack eyebrow")
    }
}
