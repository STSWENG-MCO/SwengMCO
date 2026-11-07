package com.enil.logez.core.domain.calc

/**
 * PHASE2_PLAN.md §8.2 — estimated one-rep max from a 30-entry percentage-of-1RM lookup table
 * (reps -> fraction of 1RM), the tabular alternative to closed-form formulas such as Epley or
 * Brzycki. WEIGHT_REPS exercises only — no other ExerciseType ever computes 1RM.
 */
object OneRepMax {
    /** Index = reps - 1. */
    private val PCT = doubleArrayOf(
        1.00, 0.97, 0.94, 0.92, 0.89, 0.86, 0.83, 0.81, 0.78, 0.75,
        0.73, 0.71, 0.70, 0.68, 0.67, 0.65, 0.64, 0.63, 0.61, 0.60,
        0.59, 0.58, 0.57, 0.56, 0.55, 0.54, 0.53, 0.52, 0.51, 0.50,
    )

    /**
     * Raw estimate — non-positive reps -> 0.0; reps > 30 clamp to the 0.50 factor. Unrounded.
     * `reps <= 0` (not just `== 0`) because `StatSet.reps` is a nullable Int with no non-negative
     * constraint anywhere in the schema, and `PCT[reps - 1]` would throw on a negative.
     */
    fun estimate(weightKg: Double, reps: Int): Double =
        if (reps <= 0) 0.0 else weightKg / (if (reps > 30) 0.50 else PCT[reps - 1])

    /** Display rule (§8.2): convert to the user's unit first, then round to 1 decimal. */
    fun roundForDisplay(valueInDisplayUnit: Double): Double =
        Math.round(valueInDisplayUnit * 10) / 10.0

    /**
     * Per-workout chart aggregation (§8.2): the included set with the highest raw 1RM becomes
     * that workout's point. One point per workout. Returns only the value — since the result is
     * a `Double`, an orderIndex tie-break would be unobservable here (equal 1RMs are equal
     * numbers); callers that need the *achieving set's identity* (e.g. the PR rebuild filling
     * `PersonalRecordEntity.workoutSetId`) must use [bestSetPerWorkout] instead.
     */
    fun bestPerWorkout(sets: List<StatSet>, includeWarmupsInStats: Boolean): Map<String, Double> =
        bestSetPerWorkout(sets, includeWarmupsInStats).mapValues { (_, entry) -> entry.second }

    /**
     * Same aggregation as [bestPerWorkout] but keeps the winning [StatSet], so a caller can
     * attribute the value to a concrete set. Ties break to the lowest `orderIndex` (the earlier
     * set in the session wins) — here that tie-break is genuinely observable, because two sets
     * can produce an identical 1RM and only one may be named as the record holder.
     */
    fun bestSetPerWorkout(sets: List<StatSet>, includeWarmupsInStats: Boolean): Map<String, Pair<StatSet, Double>> =
        sets
            .filter { isIncluded(it, includeWarmupsInStats) && it.weightKg != null && it.reps != null }
            .groupBy { it.workoutId }
            .mapValues { (_, workoutSets) ->
                workoutSets
                    .sortedBy { it.orderIndex }
                    .map { it to estimate(it.weightKg!!, it.reps!!) }
                    .reduce { best, candidate -> if (candidate.second > best.second) candidate else best }
            }
}
