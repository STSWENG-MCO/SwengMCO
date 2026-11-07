package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.SetType

/**
 * PHASE2_PLAN.md §8.4 — the PrType-per-ExerciseType matrix, per-set and per-session candidate
 * values, and live-banner detection. `personal_records` itself is a derived cache: this object
 * is pure computation; [com.enil.logez.core.domain.repository.PersonalRecordsRepository] owns
 * the actual rebuild-from-history orchestration, wired up at M4c.
 *
 * A candidate is omitted when its required field is null, or when the computed value is <= 0.0.
 */
object PrCalculator {
    private val SESSION_SCOPED = setOf(PrType.BEST_SESSION_VOLUME, PrType.MOST_SESSION_REPS)

    fun applicablePrTypes(type: ExerciseType): Set<PrType> = when (type) {
        ExerciseType.WEIGHT_REPS ->
            setOf(PrType.HEAVIEST_WEIGHT, PrType.BEST_1RM, PrType.BEST_SET_VOLUME, PrType.BEST_SESSION_VOLUME)
        ExerciseType.REPS_ONLY, ExerciseType.BODYWEIGHT_ASSISTED ->
            setOf(PrType.MOST_REPS_SET, PrType.MOST_SESSION_REPS)
        ExerciseType.BODYWEIGHT_WEIGHTED -> setOf(PrType.HEAVIEST_WEIGHT, PrType.BEST_SET_VOLUME)
        ExerciseType.DURATION -> setOf(PrType.BEST_TIME)
        ExerciseType.WEIGHT_DURATION -> setOf(PrType.HEAVIEST_WEIGHT, PrType.BEST_TIME)
        ExerciseType.DISTANCE_DURATION -> setOf(PrType.LONGEST_DISTANCE, PrType.LONGEST_TIME)
        ExerciseType.WEIGHT_DISTANCE -> setOf(PrType.HEAVIEST_WEIGHT, PrType.LONGEST_DISTANCE)
        ExerciseType.FLOORS_DURATION, ExerciseType.STEPS_DURATION -> setOf(PrType.BEST_TIME)
    }

    /** Per-set candidates only — HEAVIEST_WEIGHT, BEST_1RM, BEST_SET_VOLUME, MOST_REPS_SET, LONGEST_DISTANCE, BEST_TIME/LONGEST_TIME. */
    fun setCandidates(type: ExerciseType, set: StatSet, eligible: Boolean, bodyweightKg: Double?): Map<PrType, Double> =
        (applicablePrTypes(type) - SESSION_SCOPED)
            .mapNotNull { pr -> setCandidateValue(pr, type, set, eligible, bodyweightKg)?.takeIf { it > 0.0 }?.let { pr to it } }
            .toMap()

    /** Session-scoped candidates only — BEST_SESSION_VOLUME / MOST_SESSION_REPS, summed over [sessionSets]. */
    fun sessionCandidates(
        type: ExerciseType,
        sessionSets: List<StatSet>,
        eligible: Boolean,
        bodyweightKg: Double?,
    ): Map<PrType, Double> {
        val applicable = applicablePrTypes(type).intersect(SESSION_SCOPED)
        val result = mutableMapOf<PrType, Double>()
        if (PrType.BEST_SESSION_VOLUME in applicable) {
            val total = sessionSets.sumOf { VolumeCalculator.setVolume(type, eligible, it.weightKg, it.reps, bodyweightKg) }
            if (total > 0.0) result[PrType.BEST_SESSION_VOLUME] = total
        }
        if (PrType.MOST_SESSION_REPS in applicable) {
            val total = sessionSets.sumOf { it.reps ?: 0 }.toDouble()
            if (total > 0.0) result[PrType.MOST_SESSION_REPS] = total
        }
        return result
    }

    /**
     * Live in-workout banner check (§8.4 point 1): compares the new set's set-scoped candidates
     * against `max(cachedBests, earlierSessionBests)` with strict `>`. Session-scoped PRs are
     * never evaluated live — only at save time. No banner on a warm-up unless the stats setting
     * includes them, and never on an exercise's first-ever log.
     */
    fun liveBanners(
        type: ExerciseType,
        newSet: StatSet,
        eligible: Boolean,
        bodyweightKg: Double?,
        cachedBests: Map<PrType, Double>,
        earlierSessionBests: Map<PrType, Double>,
        isFirstEverLog: Boolean,
        includeWarmupsInStats: Boolean,
    ): List<PrType> {
        if (isFirstEverLog) return emptyList()
        if (newSet.setType == SetType.WARMUP && !includeWarmupsInStats) return emptyList()
        return setCandidates(type, newSet, eligible, bodyweightKg)
            .filter { (pr, value) ->
                val priorBest = maxOf(cachedBests[pr] ?: Double.NEGATIVE_INFINITY, earlierSessionBests[pr] ?: Double.NEGATIVE_INFINITY)
                value > priorBest
            }
            .keys
            .toList()
    }

    private fun setCandidateValue(
        pr: PrType,
        type: ExerciseType,
        set: StatSet,
        eligible: Boolean,
        bodyweightKg: Double?,
    ): Double? = when (pr) {
        PrType.HEAVIEST_WEIGHT -> set.weightKg
        PrType.BEST_1RM -> {
            val w = set.weightKg ?: return null
            val r = set.reps ?: return null
            OneRepMax.estimate(w, r)
        }
        PrType.BEST_SET_VOLUME -> {
            if (set.weightKg == null || set.reps == null) null
            else VolumeCalculator.setVolume(type, eligible, set.weightKg, set.reps, bodyweightKg)
        }
        PrType.MOST_REPS_SET -> set.reps?.toDouble()
        PrType.LONGEST_DISTANCE -> set.distanceMeters
        PrType.BEST_TIME, PrType.LONGEST_TIME -> set.durationSeconds?.toDouble()
        PrType.BEST_SESSION_VOLUME, PrType.MOST_SESSION_REPS -> null
    }
}
