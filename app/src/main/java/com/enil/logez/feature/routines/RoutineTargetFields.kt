package com.enil.logez.feature.routines

import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.TargetField
import com.enil.logez.core.domain.model.targetFields

/**
 * §5.1.2 Replace Exercise: "targets preserved where the new ExerciseType shares fields, clearing
 * the rest." Reps-range mode is cleared whenever the new type drops REPS, since a range with no
 * REPS column to display it in is meaningless.
 */
internal fun RoutineSetDraft.carryOverTo(oldType: ExerciseType, newType: ExerciseType): RoutineSetDraft {
    val kept = oldType.targetFields() intersect newType.targetFields()
    return copy(
        targetWeightKg = if (TargetField.WEIGHT in kept) targetWeightKg else null,
        targetReps = if (TargetField.REPS in kept) targetReps else null,
        targetRepRangeMin = if (TargetField.REPS in kept) targetRepRangeMin else null,
        targetRepRangeMax = if (TargetField.REPS in kept) targetRepRangeMax else null,
        targetDurationSeconds = if (TargetField.DURATION in kept) targetDurationSeconds else null,
        targetDistanceMeters = if (TargetField.DISTANCE in kept) targetDistanceMeters else null,
    )
}
