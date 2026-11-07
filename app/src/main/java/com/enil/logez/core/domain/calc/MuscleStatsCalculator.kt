package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import java.time.DayOfWeek
import java.time.LocalDate

enum class StatBucket { WEEK, MONTH }

/** PHASE2_PLAN.md §8.8 — Muscle Distribution + Set Count Per Muscle Group; set-count based, primary-muscle-only attribution. */
object MuscleStatsCalculator {
    data class MuscleSetInput(
        val primaryMuscleGroup: MuscleGroup,
        val workoutDate: LocalDate,
        val setType: SetType,
        val isCompleted: Boolean,
    )

    data class GroupShare(val group: MuscleGroup, val setCount: Int, val sharePercent: Int)
    data class DistributionResult(val current: List<GroupShare>, val previous: List<GroupShare>?)
    data class MuscleBucketCount(val bucketStart: LocalDate, val group: MuscleGroup, val setCount: Int)

    private fun included(s: MuscleSetInput, includeWarmups: Boolean) =
        s.isCompleted && (s.setType != SetType.WARMUP || includeWarmups)

    private fun shares(sets: List<MuscleSetInput>): List<GroupShare> {
        val total = sets.size
        if (total == 0) return emptyList()
        return sets.groupingBy { it.primaryMuscleGroup }.eachCount()
            .map { (g, count) -> GroupShare(g, count, Math.round(count * 100.0 / total).toInt()) }
    }

    fun distribution(
        sets: List<MuscleSetInput>,
        range: ChartRange,
        today: LocalDate,
        includeWarmups: Boolean,
    ): DistributionResult {
        val window = ChartAggregator.window(range, today)
        val previousWindow = window?.let {
            val lengthDays = java.time.temporal.ChronoUnit.DAYS.between(it.start, it.endInclusive) + 1
            val prevEnd = it.start.minusDays(1)
            prevEnd.minusDays(lengthDays - 1)..prevEnd
        }
        return distributionInWindows(sets, window, previousWindow, includeWarmups)
    }

    /**
     * Arbitrary-window variant — the Monthly Report compares a calendar month against the
     * previous calendar month, which is not expressible as a [ChartRange]. A null [window]
     * means all time (and forces no comparison, matching ALL_TIME above).
     */
    fun distributionInWindows(
        sets: List<MuscleSetInput>,
        window: ClosedRange<LocalDate>?,
        previousWindow: ClosedRange<LocalDate>?,
        includeWarmups: Boolean,
    ): DistributionResult {
        val included = sets.filter { included(it, includeWarmups) }
        val current = included.filter { window == null || it.workoutDate in window }
        val previous = if (window == null) null else previousWindow?.let { pw -> included.filter { it.workoutDate in pw } }
        return DistributionResult(
            current = shares(current),
            previous = previous?.takeIf { it.isNotEmpty() }?.let { shares(it) },
        )
    }

    fun setCountsPerMuscleGroup(
        sets: List<MuscleSetInput>,
        range: ChartRange,
        bucket: StatBucket,
        firstDayOfWeek: DayOfWeek,
        today: LocalDate,
        includeWarmups: Boolean,
    ): List<MuscleBucketCount> {
        val window = ChartAggregator.window(range, today)
        val included = sets
            .filter { included(it, includeWarmups) }
            .filter { window == null || it.workoutDate in window }
        return included
            .map { s ->
                val bucketStart = when (bucket) {
                    StatBucket.WEEK -> StreakCalculator.weekStart(s.workoutDate, firstDayOfWeek)
                    StatBucket.MONTH -> s.workoutDate.withDayOfMonth(1)
                }
                bucketStart to s.primaryMuscleGroup
            }
            .groupingBy { it }
            .eachCount()
            .map { (key, count) -> MuscleBucketCount(key.first, key.second, count) }
            // §8.8's vector orders CHEST before LATS within a bucket — name order, not the
            // MuscleGroup enum's declaration order (which puts LATS four entries earlier).
            .sortedWith(compareBy({ it.bucketStart }, { it.group.name }))
    }
}
