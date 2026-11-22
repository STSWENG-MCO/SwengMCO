package com.enil.logez.feature.workout.finish

import org.junit.Assert.assertEquals
import org.junit.Test

class SummarySlideTest {
    @Test
    fun `every section with data gets a slide, overview first`() {
        assertEquals(
            listOf(SummarySlide.OVERVIEW, SummarySlide.MUSCLES, SummarySlide.HEART_RATE, SummarySlide.RECORDS),
            summarySlides(hasMuscles = true, hasHeartRate = true, hasRecords = true),
        )
    }

    @Test
    fun `empty sections produce no slides`() {
        assertEquals(
            listOf(SummarySlide.OVERVIEW, SummarySlide.RECORDS),
            summarySlides(hasMuscles = false, hasHeartRate = false, hasRecords = true),
        )
    }

    @Test
    fun `overview is always present even with nothing else`() {
        assertEquals(
            listOf(SummarySlide.OVERVIEW),
            summarySlides(hasMuscles = false, hasHeartRate = false, hasRecords = false),
        )
    }

    @Test
    fun `a walk or run gets heart rate and records but never a muscle slide`() {
        assertEquals(
            listOf(SummarySlide.OVERVIEW, SummarySlide.HEART_RATE, SummarySlide.RECORDS),
            summarySlides(hasMuscles = false, hasHeartRate = true, hasRecords = true),
        )
    }
}
