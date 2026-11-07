package com.enil.logez.core.domain.calc

/** PHASE2_PLAN.md §8.10 — positional target/logged pairing for the "Update Routine Values" toggle. */
data class RoutineSetTargets(
    val orderIndex: Int,
    val targetWeightKg: Double?,
    val targetReps: Int?,
    val targetRepRangeMin: Int?,
    val targetRepRangeMax: Int?,
    val targetDurationSeconds: Int?,
    val targetDistanceMeters: Double?,
)

data class LoggedSetValues(
    val orderIndex: Int,
    val weightKg: Double?,
    val reps: Int?,
    val durationSeconds: Int?,
    val distanceMeters: Double?,
    val isCompleted: Boolean,
)

/**
 * §8.10 — runs only when the finish flow's "Update Routine Values" toggle is on. Rep-range
 * targets never auto-update (weight/duration/distance still do); unmatched/incomplete sets are
 * left untouched — structural changes are the separate Update-Routine-vs-Keep-Original prompt.
 */
object RoutineValueUpdater {
    /**
     * [loggedSets] must be the sets of a **single** workout-exercise block. `orderIndex` is only
     * unique within one block, so passing every set of an exercise across two blocks of the same
     * session would collide on index and let the later block overwrite the earlier one's targets.
     * The first entry per index wins here as a backstop, but the caller owns the block split.
     */
    fun updatedTargets(routineSets: List<RoutineSetTargets>, loggedSets: List<LoggedSetValues>): List<RoutineSetTargets> {
        val loggedByIndex = HashMap<Int, LoggedSetValues>()
        loggedSets.forEach { loggedByIndex.putIfAbsent(it.orderIndex, it) }
        return routineSets.map { target ->
            val logged = loggedByIndex[target.orderIndex] ?: return@map target
            if (!logged.isCompleted) return@map target
            val isRepRange = target.targetRepRangeMin != null || target.targetRepRangeMax != null
            target.copy(
                targetWeightKg = logged.weightKg ?: target.targetWeightKg,
                targetReps = if (isRepRange) target.targetReps else (logged.reps ?: target.targetReps),
                targetDurationSeconds = logged.durationSeconds ?: target.targetDurationSeconds,
                targetDistanceMeters = logged.distanceMeters ?: target.targetDistanceMeters,
            )
        }
    }
}
