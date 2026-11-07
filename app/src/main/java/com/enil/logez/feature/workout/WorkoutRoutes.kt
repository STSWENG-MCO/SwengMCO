package com.enil.logez.feature.workout

/** Route strings for the Live Workout Logger and its finish flow (PHASE2_PLAN.md §4). */
object WorkoutRoutes {
    const val LOGGER = "workout_logger/{workoutId}"
    const val FINISH = "workout_finish/{workoutId}"
    const val SUMMARY = "workout_summary/{workoutId}"
    /** §5.1.10 edit mode — the same Logger screen over a COMPLETED workout. */
    const val EDIT = "workout_edit/{workoutId}"

    fun logger(workoutId: String) = "workout_logger/$workoutId"
    fun finish(workoutId: String) = "workout_finish/$workoutId"
    fun summary(workoutId: String) = "workout_summary/$workoutId"
    fun edit(workoutId: String) = "workout_edit/$workoutId"
}
