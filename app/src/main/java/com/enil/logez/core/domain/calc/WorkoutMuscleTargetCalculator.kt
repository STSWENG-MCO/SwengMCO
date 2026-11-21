package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.MuscleGroup

/** Converts the exercises actually completed in one workout into BodyDiagram intensities. */
object WorkoutMuscleTargetCalculator {
    data class TargetSet(
        val primary: MuscleGroup,
        val secondary: List<MuscleGroup>,
    )

    fun intensities(sets: List<TargetSet>): Map<MuscleGroup, Float> {
        if (sets.isEmpty()) return emptyMap()
        val scores = mutableMapOf<MuscleGroup, Double>()
        sets.forEach { set ->
            scores[set.primary] = scores.getOrDefault(set.primary, 0.0) + 1.0
            set.secondary.distinct().filterNot { it == set.primary }.forEach { group ->
                scores[group] = scores.getOrDefault(group, 0.0) + 0.5
            }
        }
        val strongest = scores.values.maxOrNull() ?: return emptyMap()
        return scores.mapValues { (_, score) -> (score / strongest).toFloat() }
    }
}
