package com.enil.logez.feature.workout.finish

import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.WeightUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutSummaryFormattingTest {
    @Test fun `summary numbers omit trailing zeroes and cap fractions at hundredths`() {
        assertEquals("100", formatSummaryNumber(100.0))
        assertEquals("12.5", formatSummaryNumber(12.5))
        assertEquals("12.35", formatSummaryNumber(12.345))
    }

    @Test fun `summary units use the same two-decimal rule`() {
        assertEquals("1234.57kg", formatSummaryVolume(1234.567, WeightUnit.KG))
        assertEquals("2.35km", formatSummaryDistance(2345.0, DistanceUnit.KM))
        // 1609.344m is exactly one mile; a miles user reads "1mi" here, not "1.61km".
        assertEquals("1mi", formatSummaryDistance(1609.344, DistanceUnit.MILES))
    }

    @Test fun `summary volume converts to pounds for a pounds user`() {
        // 100 kg is 220.46 lb; the stored figure is always kg, only the text changes.
        assertEquals("220.46lb", formatSummaryVolume(100.0, WeightUnit.LB))
        assertEquals("0lb", formatSummaryVolume(0.0, WeightUnit.LB))
    }

    @Test
    fun `walk-run distance always shows two decimals, in the chosen unit`() {
        assertEquals("4.62", formatDistanceNumber(4_620.0, DistanceUnit.KM))
        assertEquals("0.54", formatDistanceNumber(537.41, DistanceUnit.KM))
        assertEquals("10.00", formatDistanceNumber(10_000.0, DistanceUnit.KM))
        assertEquals("2.87", formatDistanceNumber(4_620.0, DistanceUnit.MILES))
    }

    @Test
    fun `a distance record reads in km or miles, not raw meters`() {
        val medal = PrMedal("Running (Outdoor)", PrType.LONGEST_DISTANCE, 537.41)
        assertEquals("0.54 km", formatGpsPrValue(medal, DistanceUnit.KM))
        assertEquals("0.33 mi", formatGpsPrValue(medal, DistanceUnit.MILES))
    }

    @Test
    fun `a time record keeps its hours`() {
        assertEquals("1:15:03", formatGpsPrValue(PrMedal("Running (Outdoor)", PrType.LONGEST_TIME, 4_503.0), DistanceUnit.KM))
        assertEquals("27:58", formatGpsPrValue(PrMedal("Running (Outdoor)", PrType.LONGEST_TIME, 1_678.0), DistanceUnit.KM))
    }

    @Test
    fun `elapsed clock time switches to hours at an hour`() {
        assertEquals("0:45", com.enil.logez.core.designsystem.formatElapsedClock(45))
        assertEquals("59:59", com.enil.logez.core.designsystem.formatElapsedClock(3_599))
        assertEquals("1:00:00", com.enil.logez.core.designsystem.formatElapsedClock(3_600))
    }
}
