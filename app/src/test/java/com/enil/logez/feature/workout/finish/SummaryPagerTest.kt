package com.enil.logez.feature.workout.finish

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** W3-02a: Compose UI tests for the summary slide pager (swiping, page changes, state restoration). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SummaryPagerTest {
    @get:Rule
    val compose = createComposeRule()

    private val threeSlides = listOf(SummarySlide.OVERVIEW, SummarySlide.MUSCLES, SummarySlide.RECORDS)

    @Composable
    private fun TestPager(slides: List<SummarySlide>) {
        SummaryPager(slides = slides, modifier = Modifier.fillMaxSize()) { slide ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text("slide:${slide.name}")
                // Taller than the page, so the slide has to scroll to reach its last line.
                Text("filler", Modifier.height(4000.dp))
                Text("end:${slide.name}", Modifier.testTag("end:${slide.name}"))
            }
        }
    }

    private fun assertOnSlide(
        number: Int,
        total: Int,
    ) {
        compose
            .onNodeWithTag(SUMMARY_PAGE_INDICATOR_TAG)
            .assert(hasContentDescription("Slide $number of $total"))
    }

    @Test
    fun opensOnTheOverviewSlide() {
        compose.setContent { TestPager(threeSlides) }
        compose.onNodeWithText("slide:OVERVIEW").assertIsDisplayed()
        assertOnSlide(1, 3)
    }

    @Test
    fun swipingMovesBetweenSlides() {
        compose.setContent { TestPager(threeSlides) }

        compose.onNodeWithTag(SUMMARY_PAGER_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("slide:MUSCLES").assertIsDisplayed()
        assertOnSlide(2, 3)

        compose.onNodeWithTag(SUMMARY_PAGER_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        compose.onNodeWithText("slide:RECORDS").assertIsDisplayed()
        assertOnSlide(3, 3)

        compose.onNodeWithTag(SUMMARY_PAGER_TAG).performTouchInput { swipeRight() }
        compose.waitForIdle()
        compose.onNodeWithText("slide:MUSCLES").assertIsDisplayed()
        assertOnSlide(2, 3)
    }

    @Test
    fun currentSlideSurvivesStateRestoration() {
        val restorationTester = StateRestorationTester(compose)
        restorationTester.setContent { TestPager(threeSlides) }

        compose.onNodeWithTag(SUMMARY_PAGER_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertOnSlide(2, 3)

        restorationTester.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("slide:MUSCLES").assertIsDisplayed()
        assertOnSlide(2, 3)
    }

    @Test
    fun aSlideScrollsVerticallyWhenItsContentOverflows() {
        compose.setContent { TestPager(threeSlides) }
        compose.onNodeWithTag("end:OVERVIEW").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aSingleSlideShowsNoPageIndicator() {
        compose.setContent { TestPager(listOf(SummarySlide.OVERVIEW)) }
        compose.onNodeWithTag(SUMMARY_PAGE_INDICATOR_TAG).assertDoesNotExist()
    }

    @Test
    fun theIndicatorIsAPoliteLiveRegionSoTalkBackAnnouncesPageChanges() {
        compose.setContent { TestPager(threeSlides) }
        compose
            .onNodeWithTag(SUMMARY_PAGE_INDICATOR_TAG)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun theAnnouncedDescriptionChangesWithThePage() {
        compose.setContent { TestPager(threeSlides) }
        assertOnSlide(1, 3)
        compose.onNodeWithContentDescription("Next slide").performClick()
        compose.waitForIdle()
        assertOnSlide(2, 3)
    }

    @Test
    fun theIndicatorDoesNotAlsoExposeItsVisibleCounterAsSeparateText() {
        compose.setContent { TestPager(threeSlides) }
        compose
            .onNodeWithTag(SUMMARY_PAGE_INDICATOR_TAG)
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
    }

    @Test
    fun nextAndPreviousButtonsMoveBetweenSlidesWithoutASwipe() {
        compose.setContent { TestPager(threeSlides) }
        compose.onNodeWithContentDescription("Next slide").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("slide:MUSCLES").assertIsDisplayed()
        compose.onNodeWithContentDescription("Previous slide").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("slide:OVERVIEW").assertIsDisplayed()
    }

    @Test
    fun theButtonsAreDisabledAtTheFirstAndLastSlide() {
        compose.setContent { TestPager(threeSlides) }
        compose.onNodeWithContentDescription("Previous slide").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Next slide").assertIsEnabled()
        repeat(2) {
            compose.onNodeWithContentDescription("Next slide").performClick()
            compose.waitForIdle()
        }
        assertOnSlide(3, 3)
        compose.onNodeWithContentDescription("Next slide").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Previous slide").assertIsEnabled()
    }

    @Test
    fun aSingleSlideShowsNoSlideButtons() {
        compose.setContent { TestPager(listOf(SummarySlide.OVERVIEW)) }
        compose.onNodeWithContentDescription("Previous slide").assertDoesNotExist()
        compose.onNodeWithContentDescription("Next slide").assertDoesNotExist()
    }
}
