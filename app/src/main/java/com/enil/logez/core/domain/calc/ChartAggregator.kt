package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.ExerciseType
import java.time.LocalDate

/** PHASE2_PLAN.md §8.9 — all four ranges always available; no gating anywhere in this engine. */
enum class ChartRange { LAST_30_DAYS, LAST_3_MONTHS, LAST_YEAR, ALL_TIME }

enum class ChartMetric {
    HEAVIEST_WEIGHT, ONE_REP_MAX, BEST_SET_VOLUME, SESSION_VOLUME, TOTAL_REPS,
    MOST_REPS_SET, SESSION_REPS, BEST_TIME, LONGEST_TIME, LONGEST_DISTANCE, BEST_PACE,
}

data class ChartPoint(val workoutId: String, val startedAt: Long, val value: Double)

object ChartAggregator {
    /** Inclusive local-date window; null lower bound means ALL_TIME (§8.9). */
    fun window(range: ChartRange, today: LocalDate): ClosedRange<LocalDate>? = when (range) {
        ChartRange.ALL_TIME -> null
        ChartRange.LAST_30_DAYS -> (today.minusDays(29))..today
        ChartRange.LAST_3_MONTHS -> (today.minusMonths(3).plusDays(1))..today
        ChartRange.LAST_YEAR -> (today.minusYears(1).plusDays(1))..today
    }

    /** Per-ExerciseType chart metric sets (§8.9). */
    fun metricsFor(type: ExerciseType): List<ChartMetric> = when (type) {
        ExerciseType.WEIGHT_REPS -> listOf(
            ChartMetric.HEAVIEST_WEIGHT, ChartMetric.ONE_REP_MAX, ChartMetric.BEST_SET_VOLUME,
            ChartMetric.SESSION_VOLUME, ChartMetric.TOTAL_REPS,
        )
        ExerciseType.REPS_ONLY, ExerciseType.BODYWEIGHT_ASSISTED ->
            listOf(ChartMetric.MOST_REPS_SET, ChartMetric.SESSION_REPS)
        ExerciseType.BODYWEIGHT_WEIGHTED ->
            listOf(ChartMetric.HEAVIEST_WEIGHT, ChartMetric.BEST_SET_VOLUME, ChartMetric.TOTAL_REPS)
        ExerciseType.DURATION -> listOf(ChartMetric.BEST_TIME)
        ExerciseType.WEIGHT_DURATION -> listOf(ChartMetric.HEAVIEST_WEIGHT, ChartMetric.BEST_TIME)
        ExerciseType.DISTANCE_DURATION ->
            listOf(ChartMetric.BEST_PACE, ChartMetric.LONGEST_DISTANCE, ChartMetric.LONGEST_TIME)
        ExerciseType.WEIGHT_DISTANCE -> listOf(ChartMetric.HEAVIEST_WEIGHT, ChartMetric.LONGEST_DISTANCE)
        ExerciseType.FLOORS_DURATION, ExerciseType.STEPS_DURATION -> listOf(ChartMetric.BEST_TIME)
    }

    /** One point per COMPLETED workout with >=1 included set for the metric (§8.9). */
    fun points(
        metric: ChartMetric,
        exerciseType: ExerciseType,
        isBodyweightVolumeEligible: Boolean,
        sets: List<StatSet>,
        bodyweightByWorkout: Map<String, Double?>,
        includeWarmupsInStats: Boolean,
    ): List<ChartPoint> =
        sets
            .filter { isIncluded(it, includeWarmupsInStats) }
            .groupBy { it.workoutId }
            .mapNotNull { (workoutId, workoutSets) ->
                val value = metricValue(metric, exerciseType, isBodyweightVolumeEligible, workoutSets, bodyweightByWorkout[workoutId])
                value?.let { ChartPoint(workoutId, workoutSets.first().workoutStartedAt, it) }
            }
            .sortedBy { it.startedAt }

    private fun metricValue(
        metric: ChartMetric,
        exerciseType: ExerciseType,
        isBodyweightVolumeEligible: Boolean,
        sets: List<StatSet>,
        bodyweightKg: Double?,
    ): Double? = when (metric) {
        ChartMetric.HEAVIEST_WEIGHT -> sets.mapNotNull { it.weightKg }.maxOrNull()
        ChartMetric.ONE_REP_MAX -> sets.mapNotNull { s ->
            val w = s.weightKg ?: return@mapNotNull null
            val r = s.reps ?: return@mapNotNull null
            OneRepMax.estimate(w, r)
        }.maxOrNull()
        ChartMetric.BEST_SET_VOLUME -> sets.map {
            VolumeCalculator.setVolume(exerciseType, isBodyweightVolumeEligible, it.weightKg, it.reps, bodyweightKg)
        }.maxOrNull()
        ChartMetric.SESSION_VOLUME -> VolumeCalculator.sessionVolume(
            sets.map { VolumeCalculator.setVolume(exerciseType, isBodyweightVolumeEligible, it.weightKg, it.reps, bodyweightKg) },
        )
        ChartMetric.TOTAL_REPS, ChartMetric.SESSION_REPS -> sets.sumOf { it.reps ?: 0 }.toDouble()
        ChartMetric.MOST_REPS_SET -> sets.mapNotNull { it.reps }.maxOrNull()?.toDouble()
        ChartMetric.BEST_TIME, ChartMetric.LONGEST_TIME -> sets.mapNotNull { it.durationSeconds }.maxOrNull()?.toDouble()
        ChartMetric.LONGEST_DISTANCE -> sets.mapNotNull { it.distanceMeters }.maxOrNull()
        ChartMetric.BEST_PACE -> sets
            .filter { (it.distanceMeters ?: 0.0) > 0 && (it.durationSeconds ?: 0) > 0 }
            .minOfOrNull { it.durationSeconds!! / (it.distanceMeters!! / 1000.0) }
    }
}
