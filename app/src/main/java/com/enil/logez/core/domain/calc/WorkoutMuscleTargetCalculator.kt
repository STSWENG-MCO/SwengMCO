package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.MuscleGroup

/**
 * Converts the exercises actually completed in one workout into BodyDiagram intensities.
 *
 * W2-01a: only [MAPPABLE] groups are scored. CARDIO, FULL_BODY and OTHER have no region on the
 * body diagram, so they are dropped *before* normalizing — otherwise a FULL_BODY exercise could
 * take the "strongest" slot and make every muscle that *can* be drawn look faint.
 */
object WorkoutMuscleTargetCalculator {
    /** Groups the body diagram can draw: every [MuscleGroup] except the three non-anatomical ones. */
    val MAPPABLE: Set<MuscleGroup> =
        MuscleGroup.entries.toSet() - setOf(MuscleGroup.CARDIO, MuscleGroup.FULL_BODY, MuscleGroup.OTHER)

    data class TargetSet(
        val primary: MuscleGroup,
        val secondary: List<MuscleGroup>,
    )

    fun intensities(sets: List<TargetSet>): Map<MuscleGroup, Float> {
        if (sets.isEmpty()) return emptyMap()
        val scores = mutableMapOf<MuscleGroup, Double>()
        sets.forEach { set ->
            if (set.primary in MAPPABLE) {
                scores[set.primary] = scores.getOrDefault(set.primary, 0.0) + 1.0
            }
            set.secondary
                .distinct()
                .filter { it != set.primary && it in MAPPABLE }
                .forEach { group -> scores[group] = scores.getOrDefault(group, 0.0) + 0.5 }
        }
        val strongest = scores.values.maxOrNull() ?: return emptyMap()
        return scores.mapValues { (_, score) -> (score / strongest).toFloat() }
    }
}
