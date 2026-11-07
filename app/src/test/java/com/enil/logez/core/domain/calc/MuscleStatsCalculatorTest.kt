package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.calc.MuscleStatsCalculator.MuscleSetInput
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** PHASE2_PLAN.md §8.8's literal test vectors — today = 2026-08-22, includeWarmups = false. */
class MuscleStatsCalculatorTest {
    private val today = LocalDate.of(2026, 8, 22)

    private fun set(group: MuscleGroup, date: LocalDate, type: SetType = SetType.NORMAL, completed: Boolean = true) =
        MuscleSetInput(group, date, type, completed)

    private fun sets(group: MuscleGroup, date: String, count: Int) =
        List(count) { set(group, LocalDate.parse(date)) }

    // --- distribution (§8.8 vector rows 1-4) ---

    @Test
    fun `LAST_30_DAYS current shares - 3 CHEST and 1 LATS makes 75-25`() {
        val result = MuscleStatsCalculator.distribution(
            sets(MuscleGroup.CHEST, "2026-08-10", 3) + sets(MuscleGroup.LATS, "2026-08-12", 1),
            ChartRange.LAST_30_DAYS, today, includeWarmups = false,
        )
        assertEquals(
            listOf(
                MuscleStatsCalculator.GroupShare(MuscleGroup.CHEST, 3, 75),
                MuscleStatsCalculator.GroupShare(MuscleGroup.LATS, 1, 25),
            ),
            result.current.sortedByDescending { it.setCount },
        )
        assertNull(result.previous)
    }

    @Test
    fun `previous window holding 2 CHEST + 2 LATS reports 50-50`() {
        val result = MuscleStatsCalculator.distribution(
            sets(MuscleGroup.CHEST, "2026-08-10", 3) + sets(MuscleGroup.LATS, "2026-08-12", 1) +
                // Previous window is 2026-06-24..2026-07-23 (the 30 days before the current window).
                sets(MuscleGroup.CHEST, "2026-07-01", 2) + sets(MuscleGroup.LATS, "2026-07-10", 2),
            ChartRange.LAST_30_DAYS, today, includeWarmups = false,
        )
        assertEquals(
            listOf(
                MuscleStatsCalculator.GroupShare(MuscleGroup.CHEST, 2, 50),
                MuscleStatsCalculator.GroupShare(MuscleGroup.LATS, 2, 50),
            ),
            result.previous!!.sortedBy { it.group.name },
        )
    }

    @Test
    fun `an empty previous window yields null so the UI hides the comparison`() {
        val result = MuscleStatsCalculator.distribution(
            sets(MuscleGroup.CHEST, "2026-08-10", 3),
            ChartRange.LAST_30_DAYS, today, includeWarmups = false,
        )
        assertNull(result.previous)
    }

    @Test
    fun `ALL_TIME never has a previous comparison`() {
        val result = MuscleStatsCalculator.distribution(
            sets(MuscleGroup.CHEST, "2026-08-10", 3) + sets(MuscleGroup.LATS, "2020-01-01", 5),
            ChartRange.ALL_TIME, today, includeWarmups = false,
        )
        assertNull(result.previous)
        assertEquals(8, result.current.sumOf { it.setCount })
    }

    @Test
    fun `warm-up and uncompleted sets are excluded by default and warm-ups return with the toggle`() {
        val input = sets(MuscleGroup.CHEST, "2026-08-10", 2) +
            listOf(
                set(MuscleGroup.CHEST, LocalDate.parse("2026-08-10"), SetType.WARMUP),
                set(MuscleGroup.CHEST, LocalDate.parse("2026-08-10"), completed = false),
            )
        val off = MuscleStatsCalculator.distribution(input, ChartRange.LAST_30_DAYS, today, includeWarmups = false)
        assertEquals(2, off.current.single().setCount)
        val on = MuscleStatsCalculator.distribution(input, ChartRange.LAST_30_DAYS, today, includeWarmups = true)
        assertEquals(3, on.current.single().setCount)
    }

    // --- setCountsPerMuscleGroup (§8.8 vector row 5) ---

    @Test
    fun `WEEK buckets starting MONDAY group the vector's sets into the plan's three rows`() {
        val result = MuscleStatsCalculator.setCountsPerMuscleGroup(
            sets(MuscleGroup.CHEST, "2026-08-17", 3) + sets(MuscleGroup.LATS, "2026-08-18", 2) +
                sets(MuscleGroup.CHEST, "2026-08-11", 1),
            ChartRange.LAST_30_DAYS, StatBucket.WEEK, DayOfWeek.MONDAY, today, includeWarmups = false,
        )
        assertEquals(
            listOf(
                MuscleStatsCalculator.MuscleBucketCount(LocalDate.parse("2026-08-10"), MuscleGroup.CHEST, 1),
                MuscleStatsCalculator.MuscleBucketCount(LocalDate.parse("2026-08-17"), MuscleGroup.CHEST, 3),
                MuscleStatsCalculator.MuscleBucketCount(LocalDate.parse("2026-08-17"), MuscleGroup.LATS, 2),
            ),
            result,
        )
    }

    @Test
    fun `MONTH buckets are calendar months`() {
        val result = MuscleStatsCalculator.setCountsPerMuscleGroup(
            sets(MuscleGroup.CHEST, "2026-08-17", 2) + sets(MuscleGroup.CHEST, "2026-07-30", 1),
            ChartRange.LAST_3_MONTHS, StatBucket.MONTH, DayOfWeek.MONDAY, today, includeWarmups = false,
        )
        assertEquals(
            listOf(
                MuscleStatsCalculator.MuscleBucketCount(LocalDate.parse("2026-07-01"), MuscleGroup.CHEST, 1),
                MuscleStatsCalculator.MuscleBucketCount(LocalDate.parse("2026-08-01"), MuscleGroup.CHEST, 2),
            ),
            result,
        )
    }

    // --- distributionInWindows (Monthly Report's month-vs-month path) ---

    @Test
    fun `a calendar month compares against the previous calendar month`() {
        val result = MuscleStatsCalculator.distributionInWindows(
            sets(MuscleGroup.CHEST, "2026-08-10", 2) + sets(MuscleGroup.LATS, "2026-07-15", 4),
            LocalDate.parse("2026-08-01")..LocalDate.parse("2026-08-31"),
            LocalDate.parse("2026-07-01")..LocalDate.parse("2026-07-31"),
            includeWarmups = false,
        )
        assertEquals(MuscleGroup.CHEST, result.current.single().group)
        assertEquals(MuscleGroup.LATS, result.previous!!.single().group)
    }
}
