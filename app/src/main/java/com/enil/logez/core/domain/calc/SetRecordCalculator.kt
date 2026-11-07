package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.ExerciseType

/**
 * PHASE2_PLAN.md §8.5 — "the heaviest weight you've lifted for that specific number of
 * repetitions." Never persisted, never triggers a PR banner/medal. Applies only to the two
 * weight+reps type families; BODYWEIGHT_ASSISTED is excluded (assistance isn't lifted load).
 */
object SetRecordCalculator {
    data class SetRecord(val reps: Int, val weightKg: Double)

    private val APPLICABLE_TYPES = setOf(ExerciseType.WEIGHT_REPS, ExerciseType.BODYWEIGHT_WEIGHTED)

    fun setRecords(exerciseType: ExerciseType, sets: List<StatSet>, includeWarmupsInStats: Boolean): List<SetRecord> {
        if (exerciseType !in APPLICABLE_TYPES) return emptyList()
        return sets
            .filter { isIncluded(it, includeWarmupsInStats) }
            .mapNotNull { s ->
                val reps = s.reps ?: return@mapNotNull null
                val weight = s.weightKg ?: return@mapNotNull null
                if (reps <= 0) null else reps to weight
            }
            .groupBy({ it.first }, { it.second })
            .map { (reps, weights) -> SetRecord(reps, weights.max()) }
            .sortedBy { it.reps }
    }
}
