package com.enil.logez.feature.history

/** Route strings for the History tab's sub-screens (PHASE2_PLAN.md §4). */
object HistoryRoutes {
    const val DETAIL = "workout_detail/{workoutId}"
    /** §5.2 Calendar screen — reached from the Profile tab's month preview. */
    const val CALENDAR = "calendar"

    fun detail(workoutId: String) = "workout_detail/$workoutId"
}
