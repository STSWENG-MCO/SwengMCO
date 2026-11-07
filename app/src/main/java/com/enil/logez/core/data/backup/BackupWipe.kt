package com.enil.logez.core.data.backup

import com.enil.logez.core.data.dao.BackupDao

/**
 * Empties one backed-up table. Shared by [BackupRestorer], which wipes before inserting a backup,
 * and [LocalDataEraser], which wipes and stops. Callers go through [BackupTables.WIPE_ORDER]
 * (children first) inside one transaction.
 */
internal suspend fun BackupDao.wipe(table: String) = when (table) {
    BackupTables.PERSONAL_RECORDS -> deleteAllPersonalRecords()
    BackupTables.ACTIVITY_TRACKS -> deleteAllActivityTracks()
    BackupTables.WORKOUT_HEART_RATE_SAMPLES -> deleteAllHeartRateSamples()
    BackupTables.WORKOUT_SETS -> deleteAllWorkoutSets()
    BackupTables.WORKOUT_EXERCISES -> deleteAllWorkoutExercises()
    BackupTables.WORKOUTS -> deleteAllWorkouts()
    BackupTables.ROUTINE_SETS -> deleteAllRoutineSets()
    BackupTables.ROUTINE_EXERCISES -> deleteAllRoutineExercises()
    BackupTables.ROUTINES -> deleteAllRoutines()
    BackupTables.ROUTINE_FOLDERS -> deleteAllRoutineFolders()
    BackupTables.PROGRESS_PHOTOS -> deleteAllProgressPhotos()
    BackupTables.BODY_MEASUREMENTS -> deleteAllBodyMeasurements()
    BackupTables.GOAL_DEFINITIONS -> deleteAllGoals()
    BackupTables.DAILY_WELLNESS_TOTALS -> deleteAllWellnessTotals()
    BackupTables.EXERCISES -> deleteAllExercises()
    else -> error("no wipe for table $table")
}
