package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.SetType

/**
 * PHASE2_PLAN.md §8.1 — the shared input DTO every calculation engine below consumes. Built by
 * repositories from a join of workout_sets -> workout_exercises -> workouts filtered on
 * `workouts.status = COMPLETED`; an IN_PROGRESS workout never contributes a StatSet.
 */
data class StatSet(
    val setId: String,
    val workoutId: String,
    val workoutStartedAt: Long,
    val orderIndex: Int,
    val setType: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val customMetric: Double?,
    val isCompleted: Boolean,
    /**
     * Not in the plan's original §8.1 DTO, but §8.10's own PREVIOUS-column test vector requires
     * displaying it ("52.5 kg x 10 @ 8.5") — added here rather than left as a gap between two
     * sections of the same document.
     */
    val rpe: Double? = null,
    /** Same rationale as [rpe]: §8.10's `SAME_ROUTINE` PREVIOUS mode needs it to filter by source workout. */
    val routineId: String? = null,
)

/**
 * §8.1's single included-set predicate — every stat, chart, record, and PR engine below filters
 * through this and only this, so warm-up-inclusion semantics can never drift between screens
 * (§8.6). FAILURE and DROPSET sets always count as normal; only completed sets ever count.
 */
fun isIncluded(set: StatSet, includeWarmupsInStats: Boolean): Boolean =
    set.isCompleted && (set.setType != SetType.WARMUP || includeWarmupsInStats)
