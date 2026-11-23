package com.enil.logez.feature.workout.finish

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Spacing
import kotlinx.coroutines.launch

internal const val SUMMARY_PAGER_TAG = "summary_pager"
internal const val SUMMARY_PAGE_INDICATOR_TAG = "summary_page_indicator"
internal const val SUMMARY_PAGE_PREVIOUS_TAG = "summary_page_previous"
internal const val SUMMARY_PAGE_NEXT_TAG = "summary_page_next"

/**
 * W3-02a: the summary as a horizontal pager, one [SummarySlide] per page, with a page indicator
 * underneath. Compose Foundation's own pager, no third-party library.
 *
 * The current page lives in [rememberPagerState], which is saveable, so it survives rotation and
 * process recreation. Each slide fills the page and draws its own content; [slideContent] is
 * responsible for scrolling vertically when that content is taller than the page.
 */
@Composable
internal fun SummaryPager(
    slides: List<SummarySlide>,
    modifier: Modifier = Modifier,
    slideContent: @Composable (SummarySlide) -> Unit,
) {
    // pageCount is read lazily, so a slide that appears later (heart rate arriving from Health
    // Connect after Save) is picked up without resetting the page the user is on.
    val pagerState = rememberPagerState(initialPage = 0) { slides.size }
    val scope = rememberCoroutineScope()
    val current = pagerState.currentPage.coerceIn(0, slides.lastIndex)
    Column(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth().testTag(SUMMARY_PAGER_TAG),
            key = { page -> slides[page].name },
        ) { page ->
            Box(Modifier.fillMaxSize()) { slideContent(slides[page]) }
        }
        if (slides.size > 1) {
            PageIndicator(
                current = current,
                total = slides.size,
                onPrevious = { scope.launch { pagerState.animateScrollToPage(current - 1) } },
                onNext = { scope.launch { pagerState.animateScrollToPage(current + 1) } },
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.xs),
            )
        }
    }
}

/**
 * W3-02b: previous/next buttons around the dots and "2 / 4". The dots and counter are one polite
 * live region with a single description, so TalkBack announces "Slide 2 of 4" when the page
 * changes and does not also read the visible counter. The buttons give a way to change slides
 * without a swipe. Hidden by the caller when there is only one slide, where it says nothing.
 */
@Composable
private fun PageIndicator(
    current: Int,
    total: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.summary_page_indicator_description, current + 1, total)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onPrevious,
            enabled = current > 0,
            modifier = Modifier.testTag(SUMMARY_PAGE_PREVIOUS_TAG),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.summary_page_previous),
            )
        }
        Row(
            modifier =
                Modifier.clearAndSetSemantics {
                    testTag = SUMMARY_PAGE_INDICATOR_TAG
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(total) { index ->
                val selected = index == current
                Box(
                    Modifier
                        .size(if (selected) 8.dp else 6.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            CircleShape,
                        ),
                )
            }
            Text(
                stringResource(R.string.summary_page_indicator, current + 1, total),
                style = LogEzMono.dataSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = Spacing.xs),
            )
        }
        IconButton(
            onClick = onNext,
            enabled = current < total - 1,
            modifier = Modifier.testTag(SUMMARY_PAGE_NEXT_TAG),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = stringResource(R.string.summary_page_next),
            )
        }
    }
}
