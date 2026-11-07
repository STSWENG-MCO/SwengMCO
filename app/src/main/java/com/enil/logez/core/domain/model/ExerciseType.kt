package com.enil.logez.core.domain.model

/**
 * PHASE2_PLAN.md §3.1. Determines which [com.enil.logez.core.data.entity.WorkoutSetEntity] /
 * [com.enil.logez.core.data.entity.RoutineSetEntity] columns are populated for an exercise —
 * immutable once an exercise is created (§5.2 Exercise Library).
 *
 * The 8 values above the line are user-selectable when creating a custom exercise; the 2 below
 * are seed-only (stair-machine exercises), logged via `customMetric` + duration.
 */
enum class ExerciseType {
    WEIGHT_REPS,
    REPS_ONLY,
    BODYWEIGHT_WEIGHTED,
    BODYWEIGHT_ASSISTED,
    DURATION,
    WEIGHT_DURATION,
    DISTANCE_DURATION,
    WEIGHT_DISTANCE,

    // Seed-only — never offered in the custom-exercise type picker.
    FLOORS_DURATION,
    STEPS_DURATION,

    ;

    companion object
}

/** The 8 types selectable when creating a custom exercise (excludes the 2 seed-only types). */
val ExerciseType.Companion.userSelectable: List<ExerciseType>
    get() = listOf(
        ExerciseType.WEIGHT_REPS,
        ExerciseType.REPS_ONLY,
        ExerciseType.BODYWEIGHT_WEIGHTED,
        ExerciseType.BODYWEIGHT_ASSISTED,
        ExerciseType.DURATION,
        ExerciseType.WEIGHT_DURATION,
        ExerciseType.DISTANCE_DURATION,
        ExerciseType.WEIGHT_DISTANCE,
    )

/** One target-column kind from the set-table matrix (§5.1.2). */
enum class TargetField { WEIGHT, REPS, DURATION, DISTANCE }

/** Which target columns a given [ExerciseType] shows (§5.1.2's per-type column table). */
fun ExerciseType.targetFields(): Set<TargetField> = when (this) {
    ExerciseType.WEIGHT_REPS -> setOf(TargetField.WEIGHT, TargetField.REPS)
    ExerciseType.REPS_ONLY -> setOf(TargetField.REPS)
    ExerciseType.BODYWEIGHT_WEIGHTED -> setOf(TargetField.WEIGHT, TargetField.REPS)
    ExerciseType.BODYWEIGHT_ASSISTED -> setOf(TargetField.WEIGHT, TargetField.REPS)
    ExerciseType.DURATION -> setOf(TargetField.DURATION)
    ExerciseType.WEIGHT_DURATION -> setOf(TargetField.WEIGHT, TargetField.DURATION)
    ExerciseType.DISTANCE_DURATION -> setOf(TargetField.DISTANCE, TargetField.DURATION)
    ExerciseType.WEIGHT_DISTANCE -> setOf(TargetField.WEIGHT, TargetField.DISTANCE)
    ExerciseType.FLOORS_DURATION, ExerciseType.STEPS_DURATION -> setOf(TargetField.DURATION)
}
