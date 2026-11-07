package com.enil.logez.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.data.entity.BodyMeasurementEntity
import com.enil.logez.core.data.entity.DailyWellnessTotalEntity
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.GoalDefinitionEntity
import com.enil.logez.core.data.entity.ProgressPhotoEntity
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity

/**
 * Whole-table reads, whole-table writes and whole-table deletes, for backup and restore only.
 *
 * **This is the only file in the app containing an unscoped DELETE**, and `BackupRestorer` is its
 * only legal caller. Every other delete in the codebase is scoped by id or by parent, and keeping
 * it that way is worth more than the convenience of putting `deleteAll` on the DAOs where feature
 * code would autocomplete into it. `grep "DELETE FROM" | grep -v WHERE` should find hits here and
 * nowhere else.
 *
 * Reads are keyset-paged — `WHERE <pk> > :after ORDER BY <pk> LIMIT :limit` — rather than using
 * OFFSET, which rescans everything it skips. Every primary key here is TEXT, so the empty string
 * seeds the first page.
 *
 * Inserts deliberately have no conflict strategy. A restore runs against emptied tables, so a
 * duplicate key means the archive itself is corrupt; aborting rolls the transaction back and the
 * user keeps their data, where REPLACE would silently swallow it.
 */
@Dao
interface BackupDao {

    // ---- exercises ----
    // Unlike getAllActive(), this includes soft-deleted rows. They have to be exported: a workout
    // that used a since-retired exercise still references it, and leaving them out would fail the
    // restore on a foreign key.
    @Query("SELECT * FROM exercises WHERE id > :after ORDER BY id ASC LIMIT :limit")
    suspend fun pageExercises(after: String, limit: Int): List<ExerciseEntity>

    @Query("SELECT COUNT(*) FROM exercises")
    suspend fun countExercises(): Int

    @Insert
    suspend fun insertExercises(rows: List<ExerciseEntity>)

    @Query("DELETE FROM exercises")
    suspend fun deleteAllExercises()

    /**
     * Every media path a row still points at, relative to filesDir. Drives both the backup's
     * media list and MediaFileJanitor, so an orphaned file is neither kept nor exported.
     */
    @Query("SELECT file_path FROM progress_photos UNION SELECT media_path FROM exercises WHERE media_path IS NOT NULL")
    suspend fun referencedMediaPaths(): List<String>

    // ---- routine_folders ----
    @Query("SELECT * FROM routine_folders WHERE id > :after ORDER BY id ASC LIMIT :limit")
    suspend fun pageRoutineFolders(after: String, limit: Int): List<RoutineFolderEntity>

    @Query("SELECT COUNT(*) FROM routine_folders")
    suspend fun countRoutineFolders(): Int

    @Insert
    suspend fun insertRoutineFolders(rows: List<RoutineFolderEntity>)

    @Query("DELETE FROM routine_folders")
    suspend fun deleteAllRoutineFolders()

    // ---- routines ----
    @Query("SELECT * FROM routines WHERE id > :after ORDER BY id ASC LIMIT :limit")
    suspend fun pageRoutines(after: String, limit: Int): List<RoutineEntity>

    @Query("SELECT COUNT(*) FROM routines")
    suspend fun countRoutines(): Int

    @Insert
    suspend fun insertRoutines(rows: List<RoutineEntity>)

    @Query("DELETE FROM routines")
    suspend fun deleteAllRoutines()

    // ---- routine_exercises ----
    @Query("SELECT * FROM routine_exercises WHERE id > :after ORDER BY id ASC LIMIT :limit")
    suspend fun pageRoutineExercises(after: String, limit: Int): List<RoutineExerciseEntity>

    @Query("SELECT COUNT(*) FROM routine_exercises")
    suspend fun countRoutineExercises(): Int

    @Insert
    suspend fun insertRoutineExercisesBulk(rows: List<RoutineExerciseEntity>)

    @Query("DELETE FROM routine_exercises")
    suspend fun deleteAllRoutineExercises()

    // ---- routine_sets ----
    @Query("SELECT * FROM routine_sets WHERE id > :after ORDER BY id ASC LIMIT :limit")
    suspend fun pageRoutineSets(after: String, limit: Int): List<RoutineSetEntity>

    @Query("SELECT COUNT(*) FROM routine_sets")
    suspend fun countRoutineSets(): Int

    @Insert
    suspend fun insertRoutineSetsBulk(rows: List<RoutineSetEntity>)

    @Query("DELETE FROM routine_sets")
    suspend fun deleteAllRoutineSets()

    // ---- workouts ----
    // Includes IN_PROGRESS rows, unlike getCompletedWorkouts(): a backup is a snapshot of
    // everything, and a restore is blocked while a session is live anyway.
    @Query("SELECT * FROM workouts WHERE id > :after ORDER BY id ASC LIMIT :limit")
    suspend fun pageWorkouts(after: String, limit: Int): List<WorkoutEntity>

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun countWorkouts(): Int

    @Insert
    suspend fun insertWorkoutsBulk(rows: List<WorkoutEntity>)

    @Query("DELETE FROM workouts")
    suspend fun deleteAllWorkouts()

    // ---- workout_exercises ----
    @Query("SELECT * FROM workout_exercises WHERE id > :after ORDER BY id ASC LIMIT :limit")
    suspend fun pageWorkoutExercises(after: String, limit: Int): List<WorkoutExerciseEntity>

    @Query("SELECT COUNT(*) FROM workout_exercises")
    suspend fun countWorkoutExercises(): Int

    @Insert
    suspend fun insertWorkoutExercisesBulk(rows: List<WorkoutExerciseEntity>)

    @Query("DELETE FROM workout_exercises")
    suspend fun deleteAllWorkoutExercises()

    // ---- workout_sets ----
    @Query("SELECT * FROM workout_sets WHERE id > :after ORDER BY id ASC LIMIT :limit")
    suspend fun pageWorkoutSets(after: String, limit: Int): List<WorkoutSetEntity>

    @Query("SELECT COUNT(*) FROM workout_sets")
    suspend fun countWorkoutSets(): Int

    @Insert
    suspend fun insertWorkoutSetsBulk(rows: List<WorkoutSetEntity>)

    @Query("DELETE FROM workout_sets")
    suspend fun deleteAllWorkoutSets()

    // ---- activity_tracks ----
    @Query("SELECT * FROM activity_tracks WHERE id > :after ORDER BY id ASC LIMIT :limit")
    suspend fun pageActivityTracks(after: String, limit: Int): List<ActivityTrackEntity>

    @Query("SELECT COUNT(*) FROM activity_tracks")
    suspend fun countActivityTracks(): Int

    @Insert
    suspend fun insertActivityTracks(rows: List<ActivityTrackEntity>)

    @Query("DELETE FROM activity_tracks")
    suspend fun deleteAllActivityTracks()

    // ---- workout_heart_rate_samples ----
    @Query("SELECT * FROM workout_heart_rate_samples WHERE id > :after ORDER BY id ASC LIMIT :limit")
    suspend fun pageHeartRateSamples(after: String, limit: Int): List<WorkoutHeartRateSampleEntity>

    @Query("SELECT COUNT(*) FROM workout_heart_rate_samples")
    suspend fun countHeartRateSamples(): Int

    @Insert
    suspend fun insertHeartRateSamples(rows: List<WorkoutHeartRateSampleEntity>)

    @Query("DELETE FROM workout_heart_rate_samples")
    suspend fun deleteAllHeartRateSamples()

    // ---- body_measurements (keyed by date, not id) ----
    @Query("SELECT * FROM body_measurements WHERE date > :after ORDER BY date ASC LIMIT :limit")
    suspend fun pageBodyMeasurements(after: String, limit: Int): List<BodyMeasurementEntity>

    @Query("SELECT COUNT(*) FROM body_measurements")
    suspend fun countBodyMeasurements(): Int

    @Insert
    suspend fun insertBodyMeasurements(rows: List<BodyMeasurementEntity>)

    @Query("DELETE FROM body_measurements")
    suspend fun deleteAllBodyMeasurements()

    // ---- progress_photos ----
    @Query("SELECT * FROM progress_photos WHERE id > :after ORDER BY id ASC LIMIT :limit")
    suspend fun pageProgressPhotos(after: String, limit: Int): List<ProgressPhotoEntity>

    @Query("SELECT COUNT(*) FROM progress_photos")
    suspend fun countProgressPhotos(): Int

    @Insert
    suspend fun insertProgressPhotos(rows: List<ProgressPhotoEntity>)

    @Query("DELETE FROM progress_photos")
    suspend fun deleteAllProgressPhotos()

    // ---- goal_definitions ----
    @Query("SELECT * FROM goal_definitions WHERE id > :after ORDER BY id ASC LIMIT :limit")
    suspend fun pageGoals(after: String, limit: Int): List<GoalDefinitionEntity>

    @Query("SELECT COUNT(*) FROM goal_definitions")
    suspend fun countGoals(): Int

    @Insert
    suspend fun insertGoals(rows: List<GoalDefinitionEntity>)

    @Query("DELETE FROM goal_definitions")
    suspend fun deleteAllGoals()

    // ---- daily_wellness_totals (keyed by date) ----
    @Query("SELECT * FROM daily_wellness_totals WHERE date > :after ORDER BY date ASC LIMIT :limit")
    suspend fun pageWellnessTotals(after: String, limit: Int): List<DailyWellnessTotalEntity>

    @Query("SELECT COUNT(*) FROM daily_wellness_totals")
    suspend fun countWellnessTotals(): Int

    @Insert
    suspend fun insertWellnessTotals(rows: List<DailyWellnessTotalEntity>)

    @Query("DELETE FROM daily_wellness_totals")
    suspend fun deleteAllWellnessTotals()

    // ---- personal_records: wiped but never exported, since it is rebuilt from the sets ----
    @Query("DELETE FROM personal_records")
    suspend fun deleteAllPersonalRecords()

    /**
     * The exercises a personal-record rebuild actually has to run for. Rebuilding all 400 would be
     * mostly no-ops against exercises the user has never logged.
     */
    @Query(
        """
        SELECT DISTINCT we.exercise_id FROM workout_exercises we
        JOIN workouts w ON w.id = we.workout_id
        WHERE w.status = 'COMPLETED'
        """,
    )
    suspend fun exerciseIdsWithCompletedHistory(): List<String>
}
