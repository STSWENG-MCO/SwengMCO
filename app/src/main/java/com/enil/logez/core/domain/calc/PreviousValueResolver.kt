package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.PreviousValuesMode

/**
 * PHASE2_PLAN.md §8.10 — resolves the live logger's PREVIOUS column source: the most recent
 * COMPLETED workout containing this exercise (ANY_WORKOUT), or the most recent one sharing the
 * current workout's `routineId` (SAME_ROUTINE, no fallback to ANY_WORKOUT if none matches — a
 * silent fallback would contradict what the setting promises). [rows] must already be sorted by
 * workout recency descending, sets ascending within each workout — [WorkoutDao.getStatRowsForExercise]'s
 * existing order — since this only groups by first-match, it doesn't re-sort.
 *
 * [beforeStartedAt] restricts the search to workouts that started strictly earlier. Live logging
 * leaves it null — an IN_PROGRESS workout contributes no StatSets, so "most recent" is already
 * "most recent other". Edit mode must pass the edited workout's own `startedAt` (§5.1.10: "PREVIOUS
 * shows what it showed relative to the workout's own startedAt"): that workout *is* COMPLETED, so
 * without the bound it would surface its own sets as its own PREVIOUS values.
 */
fun resolvePreviousWorkoutSets(
    rows: List<StatSet>,
    mode: PreviousValuesMode,
    currentRoutineId: String?,
    beforeStartedAt: Long? = null,
): List<StatSet> {
    val inWindow = if (beforeStartedAt == null) rows else rows.filter { it.workoutStartedAt < beforeStartedAt }
    val candidates = if (mode == PreviousValuesMode.SAME_ROUTINE) {
        inWindow.filter { it.routineId == currentRoutineId }
    } else {
        inWindow
    }
    val mostRecentWorkoutId = candidates.firstOrNull()?.workoutId ?: return emptyList()
    return candidates.filter { it.workoutId == mostRecentWorkoutId }
}
