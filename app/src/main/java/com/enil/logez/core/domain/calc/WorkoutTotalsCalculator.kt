package com.enil.logez.core.domain.calc

/**
 * W1-02: one workout's core totals for the post-workout summary — sets, reps and distance, plus
 * which of those (and weight) the workout actually logged, so the summary can hide a metric that
 * doesn't apply instead of showing a fake zero.
 *
 * Counts only what [isIncluded] counts: completed sets, with warm-ups following the user's
 * `includeWarmupsInStats` setting. Drop sets and failure sets count like normal sets. Volume is
 * not totalled here because it needs each exercise's type ([VolumeCalculator.setVolume]).
 */
object WorkoutTotalsCalculator {
    data class Totals(
        val setCount: Int,
        /** Sum of logged reps; a set with no reps (a plank, a run) adds 0, never a guessed count. */
        val totalReps: Int,
        val totalDistanceMeters: Double,
        /** Whether any counted set logged a weight — gates the Volume cell. */
        val hasWeight: Boolean,
        /** Whether any counted set logged reps — gates the Reps cell (hidden on a walk/run). */
        val hasReps: Boolean,
        /** Whether any counted set logged a distance — gates the Distance cell. */
        val hasDistance: Boolean,
    )

    fun totals(
        sets: List<StatSet>,
        includeWarmupsInStats: Boolean,
    ): Totals {
        val counted = sets.filter { isIncluded(it, includeWarmupsInStats) }
        return Totals(
            setCount = counted.size,
            totalReps = counted.sumOf { it.reps ?: 0 },
            totalDistanceMeters = counted.sumOf { it.distanceMeters ?: 0.0 },
            hasWeight = counted.any { it.weightKg != null },
            hasReps = counted.any { it.reps != null },
            hasDistance = counted.any { it.distanceMeters != null },
        )
    }
}
