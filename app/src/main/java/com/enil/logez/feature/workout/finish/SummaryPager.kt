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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Spacing

internal const val SUMMARY_PAGER_TAG = "summary_pager"
internal const val SUMMARY_PAGE_INDICATOR_TAG = "summary_page_indicator"

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
                current = pagerState.currentPage.coerceIn(0, slides.lastIndex),
                total = slides.size,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = Spacing.xs),
            )
        }
    }
}

/** Dots plus "2 / 4". Hidden by the caller when there is only one slide, where it says nothing. */
@Composable
private fun PageIndicator(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.summary_page_indicator_description, current + 1, total)
    Row(
        modifier =
            modifier
                .testTag(SUMMARY_PAGE_INDICATOR_TAG)
                .semantics(mergeDescendants = true) { contentDescription = description },
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
}
