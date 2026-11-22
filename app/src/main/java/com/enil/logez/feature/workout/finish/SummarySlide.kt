package com.enil.logez.feature.workout.finish

/**
 * W3-02a: one section of the post-workout summary, shown as its own swipeable slide. Declaration
 * order is display order, and [OVERVIEW] is always first, so the pager opens on it.
 */
enum class SummarySlide { OVERVIEW, MUSCLES, HEART_RATE, RECORDS }

/**
 * The slides this workout actually has data for. A section with nothing to show produces no slide
 * rather than a blank one; the overview is always present.
 */
internal fun summarySlides(
    hasMuscles: Boolean,
    hasHeartRate: Boolean,
    hasRecords: Boolean,
): List<SummarySlide> =
    buildList {
        add(SummarySlide.OVERVIEW)
        if (hasMuscles) add(SummarySlide.MUSCLES)
        if (hasHeartRate) add(SummarySlide.HEART_RATE)
        if (hasRecords) add(SummarySlide.RECORDS)
    }
