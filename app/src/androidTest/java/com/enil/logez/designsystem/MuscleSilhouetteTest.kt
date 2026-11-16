package com.enil.logez.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.designsystem.MuscleSilhouette
import com.enil.logez.core.domain.calc.WorkoutMuscleTargetCalculator
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MuscleSilhouetteTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun description(names: String) =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext
            .getString(R.string.muscle_silhouette_description, names)

    @Test
    fun muscleSilhouette_withTrainedMuscles_describesThemForTalkBack() {
        composeRule.setContent {
            LogEzTheme {
                MuscleSilhouette(
                    intensity = mapOf(MuscleGroup.CHEST to 1f, MuscleGroup.TRICEPS to 0.5f),
                    variant = MuscleDiagramVariant.MALE,
                )
            }
        }
        composeRule.onNodeWithContentDescription(description("Chest, Triceps")).assertIsDisplayed()
    }

    @Test
    fun muscleSilhouette_rendersForMaleVariant() {
        composeRule.setContent {
            LogEzTheme { MuscleSilhouette(mapOf(MuscleGroup.CHEST to 1f), MuscleDiagramVariant.MALE) }
        }
        composeRule.onNodeWithContentDescription(description("Chest")).assertIsDisplayed()
    }

    @Test
    fun muscleSilhouette_rendersForFemaleVariant() {
        composeRule.setContent {
            LogEzTheme { MuscleSilhouette(mapOf(MuscleGroup.CHEST to 1f), MuscleDiagramVariant.FEMALE) }
        }
        composeRule.onNodeWithContentDescription(description("Chest")).assertIsDisplayed()
    }

    @Test
    fun muscleSilhouette_withNoIntensity_isNotShown() {
        composeRule.setContent {
            LogEzTheme { MuscleSilhouette(emptyMap(), MuscleDiagramVariant.MALE) }
        }
        composeRule.onAllNodesWithContentDescription("Body map", substring = true).assertCountEquals(0)
    }

    @Test
    fun muscleSilhouette_withOnlyNonMappableGroups_isNotShown() {
        composeRule.setContent {
            LogEzTheme {
                MuscleSilhouette(
                    mapOf(MuscleGroup.CARDIO to 1f, MuscleGroup.FULL_BODY to 1f, MuscleGroup.OTHER to 1f),
                    MuscleDiagramVariant.FEMALE,
                )
            }
        }
        composeRule.onAllNodesWithContentDescription("Body map", substring = true).assertCountEquals(0)
    }

    @Test
    fun muscleSilhouette_withEveryMuscleInNarrowSlot_stillRenders() {
        val everything = WorkoutMuscleTargetCalculator.MAPPABLE.associateWith { 1f }
        composeRule.setContent {
            LogEzTheme {
                Box(Modifier.width(160.dp)) { MuscleSilhouette(everything, MuscleDiagramVariant.MALE) }
            }
        }
        composeRule.onAllNodesWithContentDescription("Body map", substring = true).assertCountEquals(1)
    }
}
