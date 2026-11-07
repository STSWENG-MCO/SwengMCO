package com.enil.logez.core.data.backup

/**
 * The order tables must be emptied and refilled in, declared once so the two can be checked
 * against each other and against the schema.
 *
 * Foreign keys make both orders load-bearing: children go before parents when deleting and after
 * them when inserting. Two edges are nullable-on-delete rather than cascading, so they would
 * survive a mistake — every other edge would fail the restore outright, which is the better of the
 * two outcomes but still not one to rely on.
 */
object BackupTables {
    const val EXERCISES = "exercises"
    const val ROUTINE_FOLDERS = "routine_folders"
    const val ROUTINES = "routines"
    const val ROUTINE_EXERCISES = "routine_exercises"
    const val ROUTINE_SETS = "routine_sets"
    const val WORKOUTS = "workouts"
    const val WORKOUT_EXERCISES = "workout_exercises"
    const val WORKOUT_SETS = "workout_sets"
    const val ACTIVITY_TRACKS = "activity_tracks"
    const val WORKOUT_HEART_RATE_SAMPLES = "workout_heart_rate_samples"
    const val BODY_MEASUREMENTS = "body_measurements"
    const val PROGRESS_PHOTOS = "progress_photos"
    const val GOAL_DEFINITIONS = "goal_definitions"
    const val DAILY_WELLNESS_TOTALS = "daily_wellness_totals"

    /** Derived from the sets, so it is never exported — but it still has to be emptied. */
    const val PERSONAL_RECORDS = "personal_records"

    /** Every table carried in an archive, in the order rows must be inserted. */
    val EXPORT_ORDER = listOf(
        EXERCISES,
        ROUTINE_FOLDERS,
        ROUTINES,
        ROUTINE_EXERCISES,
        ROUTINE_SETS,
        WORKOUTS,
        WORKOUT_EXERCISES,
        WORKOUT_SETS,
        ACTIVITY_TRACKS,
        WORKOUT_HEART_RATE_SAMPLES,
        BODY_MEASUREMENTS,
        PROGRESS_PHOTOS,
        GOAL_DEFINITIONS,
        DAILY_WELLNESS_TOTALS,
    )

    /** Children first. `exercises` is last: routine and workout rows reference it. */
    val WIPE_ORDER = listOf(
        PERSONAL_RECORDS,
        ACTIVITY_TRACKS,
        WORKOUT_HEART_RATE_SAMPLES,
        WORKOUT_SETS,
        WORKOUT_EXERCISES,
        WORKOUTS,
        ROUTINE_SETS,
        ROUTINE_EXERCISES,
        ROUTINES,
        ROUTINE_FOLDERS,
        PROGRESS_PHOTOS,
        BODY_MEASUREMENTS,
        GOAL_DEFINITIONS,
        DAILY_WELLNESS_TOTALS,
        EXERCISES,
    )

    fun tableEntry(table: String) = "${BackupFormat.TABLES_PREFIX}$table.jsonl"
}
