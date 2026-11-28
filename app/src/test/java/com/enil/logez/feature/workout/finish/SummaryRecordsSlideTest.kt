package com.enil.logez.feature.workout.finish

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.enil.logez.R
import com.enil.logez.core.designsystem.formatElapsedClock
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.GpsActivity
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * W3-01: the personal-records highlight on the summary. Covers the with-PR and no-PR states for a
 * strength workout and a walk/run, with the weight and distance unit settings applied.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SummaryRecordsSlideTest {
    @get:Rule
    val compose = createComposeRule()

    private val strengthPrs =
        listOf(
            PrMedal("Bench Press (Barbell)", PrType.HEAVIEST_WEIGHT, 100.0),
            PrMedal("Pull Up", PrType.MOST_REPS_SET, 12.0),
        )

    private val gpsPrs =
        listOf(
            PrMedal("Running (Outdoor)", PrType.LONGEST_DISTANCE, 5_000.0),
            PrMedal("Running (Outdoor)", PrType.BEST_TIME, 1_678.0),
        )

    // Same wiring as WorkoutSummaryScreen: the slide list comes from the state, then the pager.
    @Composable
    private fun StrengthSummary(state: WorkoutSummaryUiState) {
        SummaryPager(slides = state.summarySlides(), modifier = Modifier.fillMaxSize()) { slide ->
            StrengthSummarySlide(slide, state)
        }
    }

    @Composable
    private fun GpsSummary(state: WorkoutSummaryUiState) {
        SummaryPager(slides = state.summarySlides(), modifier = Modifier.fillMaxSize()) { slide ->
            GpsSummarySlide(slide, state, onOpenMap = {})
        }
    }

    private fun swipeToRecords() {
        compose.onNodeWithTag(SUMMARY_PAGER_TAG).performTouchInput { swipeLeft() }
        compose.waitForIdle()
    }

    // --- with PRs ---

    @Test
    fun `a strength PR shows exercise name, type label and value`() {
        val state = WorkoutSummaryUiState(isLoading = false, weightUnit = WeightUnit.KG, prMedals = strengthPrs)
        assertTrue(SummarySlide.RECORDS in state.summarySlides())

        compose.setContent { StrengthSummary(state) }
        swipeToRecords()

        compose.onNodeWithText("Personal records").assertIsDisplayed()
        compose.onNodeWithText("Bench Press (Barbell)").assertIsDisplayed()
        compose.onNodeWithText("Heaviest weight").assertIsDisplayed()
        compose.onNodeWithText("100kg").assertIsDisplayed()
        compose.onNodeWithText("Pull Up").assertIsDisplayed()
        compose.onNodeWithText("Most reps in a set").assertIsDisplayed()
        compose.onNodeWithText("12").assertIsDisplayed()
    }

    @Test
    fun `a strength PR value follows the weight unit setting`() {
        val state = WorkoutSummaryUiState(isLoading = false, weightUnit = WeightUnit.LB, prMedals = strengthPrs)

        compose.setContent { StrengthSummary(state) }
        swipeToRecords()

        compose.onNodeWithText("220.46lb").assertIsDisplayed()
    }

    @Test
    fun `a walk or run PR shows type label, exercise name and distance in the chosen unit`() {
        val state =
            WorkoutSummaryUiState(
                isLoading = false,
                isGpsTracked = true,
                distanceUnit = DistanceUnit.MILES,
                prMedals = gpsPrs,
            )
        assertEquals(listOf(SummarySlide.OVERVIEW, SummarySlide.RECORDS), state.summarySlides())

        // The walk/run overview is the map screen; the records slide is what is under test here.
        compose.setContent { GpsSummarySlide(SummarySlide.RECORDS, state, onOpenMap = {}) }

        compose.onNodeWithText("Personal records".uppercase()).assertIsDisplayed()
        compose.onNodeWithText("Longest distance").assertIsDisplayed()
        compose.onNodeWithText("Best time").assertIsDisplayed()
        compose.onNodeWithText("3.11 mi").assertIsDisplayed()
        compose.onNodeWithText("27:58").assertIsDisplayed()
    }

    // --- without PRs ---

    @Test
    fun `a walk or run with no PRs has only the overview slide`() {
        val state = WorkoutSummaryUiState(isLoading = false, isGpsTracked = true, prMedals = emptyList())
        assertFalse(SummarySlide.RECORDS in state.summarySlides())
        assertEquals(listOf(SummarySlide.OVERVIEW), state.summarySlides())
    }

    @Test
    fun `a walk or run summary with no PRs shows no records section`() {
        val state = WorkoutSummaryUiState(isLoading = false, isGpsTracked = true, prMedals = emptyList())

        compose.setContent { GpsSummary(state) }

        compose.onNodeWithText("Personal records").assertDoesNotExist()
        compose.onNodeWithText("Personal records".uppercase()).assertDoesNotExist()
        compose.onNodeWithTag(SUMMARY_PAGE_INDICATOR_TAG).assertDoesNotExist()
    }

    @Test
    fun `no PRs means no records slide`() {
        val state = WorkoutSummaryUiState(isLoading = false, prMedals = emptyList())
        assertFalse(SummarySlide.RECORDS in state.summarySlides())
        assertEquals(listOf(SummarySlide.OVERVIEW), state.summarySlides())
    }

    @Test
    fun `a summary with no PRs shows no records section`() {
        val state = WorkoutSummaryUiState(isLoading = false, prMedals = emptyList())

        compose.setContent { StrengthSummary(state) }

        compose.onNodeWithText("Personal records").assertDoesNotExist()
        compose.onNodeWithTag(SUMMARY_PAGE_INDICATOR_TAG).assertDoesNotExist()
    }

    // --- regression: the existing summary content still shows inside the pager ---

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun swipeBackToOverview() {
        compose.onNodeWithTag(SUMMARY_PAGER_TAG).performTouchInput { swipeRight() }
        compose.waitForIdle()
    }

    private val loggedWorkout =
        WorkoutSummaryUiState(
            isLoading = false,
            title = "Push Day",
            weightUnit = WeightUnit.KG,
            completedSetCount = 5,
            totalReps = 40,
            totalVolumeKg = 2_400.0,
            durationSeconds = 3_720,
            hasVolume = true,
            hasReps = true,
            prMedals = strengthPrs,
        )

    private fun assertOverviewContentShown(state: WorkoutSummaryUiState) {
        compose.onNodeWithText(string(R.string.summary_title)).assertIsDisplayed()
        compose.onNodeWithText(state.title).assertIsDisplayed()
        compose.onNodeWithText(formatSummaryVolume(state.totalVolumeKg, state.weightUnit)).assertExists()
        compose.onNodeWithText(state.completedSetCount.toString()).assertExists()
        compose.onNodeWithText(state.totalReps.toString()).assertExists()
        compose.onNodeWithText("1h 2m").assertExists()
        compose.onNodeWithText(string(R.string.summary_volume), ignoreCase = true).assertExists()
        compose.onNodeWithText(string(R.string.summary_sets), ignoreCase = true).assertExists()
        compose.onNodeWithText(string(R.string.summary_reps), ignoreCase = true).assertExists()
        compose.onNodeWithText(string(R.string.summary_duration), ignoreCase = true).assertExists()
    }

    @Test
    fun `the pager opens on the overview with title, volume, sets, reps and duration`() {
        compose.setContent { StrengthSummary(loggedWorkout) }

        assertOverviewContentShown(loggedWorkout)
    }

    @Test
    fun `the overview content is unchanged with no PRs, when the pager has a single slide`() {
        val state = loggedWorkout.copy(prMedals = emptyList())
        assertEquals(listOf(SummarySlide.OVERVIEW), state.summarySlides())

        compose.setContent { StrengthSummary(state) }

        assertOverviewContentShown(state)
    }

    @Test
    fun `the overview content is still there after swiping to records and back`() {
        compose.setContent { StrengthSummary(loggedWorkout) }

        swipeToRecords()
        compose.onNodeWithText("Personal records").assertIsDisplayed()
        swipeBackToOverview()

        assertOverviewContentShown(loggedWorkout)
    }

    @Test
    fun `a walk or run overview inside the pager still shows title, distance and time`() {
        val state =
            WorkoutSummaryUiState(
                isLoading = false,
                isGpsTracked = true,
                gpsActivity = GpsActivity.RUN,
                title = "Morning Run",
                distanceUnit = DistanceUnit.KM,
                totalDistanceMeters = 5_000.0,
                durationSeconds = 1_678,
                hasDistance = true,
                prMedals = gpsPrs,
            )
        assertEquals(listOf(SummarySlide.OVERVIEW, SummarySlide.RECORDS), state.summarySlides())

        compose.setContent { GpsSummary(state) }

        compose.onNodeWithText("Morning Run").assertIsDisplayed()
        compose.onNodeWithText(formatDistanceNumber(5_000.0, DistanceUnit.KM)).assertExists()
        compose.onNodeWithText("km").assertExists()
        compose.onNodeWithText(formatElapsedClock(1_678)).assertExists()
        compose.onNodeWithText(string(R.string.summary_gps_distance), ignoreCase = true).assertExists()
        compose.onNodeWithText(string(R.string.summary_gps_time), ignoreCase = true).assertExists()
    }
}
