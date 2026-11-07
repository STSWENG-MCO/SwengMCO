package com.enil.logez.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.enil.logez.core.data.dao.ActivityTrackDao
import com.enil.logez.core.data.dao.AnalyticsDao
import com.enil.logez.core.data.dao.BackupDao
import com.enil.logez.core.data.dao.ExerciseDao
import com.enil.logez.core.data.dao.GoalDao
import com.enil.logez.core.data.dao.MeasurementDao
import com.enil.logez.core.data.dao.RecordsDao
import com.enil.logez.core.data.dao.RoutineDao
import com.enil.logez.core.data.dao.WellnessDao
import com.enil.logez.core.data.dao.WorkoutDao
import com.enil.logez.core.data.dao.WorkoutHeartRateSampleDao
import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.data.entity.BodyMeasurementEntity
import com.enil.logez.core.data.entity.DailyWellnessTotalEntity
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.GoalDefinitionEntity
import com.enil.logez.core.data.entity.PersonalRecordEntity
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
 * Schema v1 (PHASE2_PLAN.md §3.2, §10.4), v2 adds `goal_definitions` (M8d), v3 adds
 * `exercises.primary_muscle_head` (M8e), v4 replaces that with `exercises.muscle_heads` (M8e
 * revision — a checklist, not a single pick), v5 adds `routines.structure` and
 * `workouts.structure` (M11 circuits — a discriminator only, no new tables), v6 adds
 * `activity_tracks` (M21a — GPS-tracked run/walk route data, one row per tracked `workout_sets`
 * row), v7 adds `daily_wellness_totals` (M21e — a local cache of Health Connect's own all-day
 * steps/calories aggregate, one row per calendar day), v8 adds `workout_heart_rate_samples` (M21f —
 * a local cache of Health Connect's per-sample heart rate over a workout's own time window, read
 * once at Finish), v9 adds `workouts.kind` (M23a — which live surface owns an IN_PROGRESS row once
 * the process that was tracking it is gone; a discriminator only, no new tables).
 * `exportSchema = true`
 * from day one — `schemas/` is
 * committed alongside this file. `fallbackToDestructiveMigration` is never used anywhere in this
 * app (project-rules.md testing expectations): this app's entire value is the historical log, so
 * every schema change ships as a real, tested [androidx.room.migration.Migration].
 */
@Database(
    entities = [
        ExerciseEntity::class,
        RoutineFolderEntity::class,
        RoutineEntity::class,
        RoutineExerciseEntity::class,
        RoutineSetEntity::class,
        WorkoutEntity::class,
        WorkoutExerciseEntity::class,
        WorkoutSetEntity::class,
        PersonalRecordEntity::class,
        BodyMeasurementEntity::class,
        ProgressPhotoEntity::class,
        GoalDefinitionEntity::class,
        ActivityTrackEntity::class,
        DailyWellnessTotalEntity::class,
        WorkoutHeartRateSampleEntity::class,
    ],
    version = LogEzDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LogEzDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun routineDao(): RoutineDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun recordsDao(): RecordsDao
    abstract fun measurementDao(): MeasurementDao
    abstract fun analyticsDao(): AnalyticsDao
    abstract fun goalDao(): GoalDao
    abstract fun activityTrackDao(): ActivityTrackDao
    abstract fun wellnessDao(): WellnessDao
    abstract fun workoutHeartRateSampleDao(): WorkoutHeartRateSampleDao

    /** Backup/restore only -- whole-table reads and the app's only unscoped deletes. */
    abstract fun backupDao(): BackupDao

    companion object {
        /**
         * The schema version, in one place. [com.enil.logez.core.data.backup.BackupWriter] stamps it
         * into every backup's manifest so an older app refuses a newer backup; it drifted once
         * (left at 9 when v10 shipped, 2026-09-26) while it was a separate literal.
         */
        const val VERSION = 10

        const val DATABASE_NAME = "logez.db"

        /** v1 -> v2 (M8d): a brand-new table only, nothing existing changes shape. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `goal_definitions` (
                        `id` TEXT NOT NULL,
                        `metric` TEXT NOT NULL,
                        `period` TEXT NOT NULL,
                        `target_value` REAL NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** v2 -> v3 (M8e): one new nullable column, no existing data touched. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `primary_muscle_head` TEXT")
            }
        }

        /**
         * v3 -> v4 (M8e revision — Owner feedback: heads should be a checklist, not a single
         * pick). Adds `muscle_heads` (a JSON list, matching `secondary_muscle_groups`'s existing
         * convention) and carries forward any already-set `primary_muscle_head` as that head's
         * single-element list. `primary_muscle_head` itself is left in place, unused, rather than
         * dropped — `DROP COLUMN` support varies across the SQLite versions bundled with API
         * 26-36 (this app's minSdk-to-target range). Unlike a hand-written schema, this does
         * *not* mean the column can simply stop being declared in [ExerciseEntity]: Room's
         * post-migration validation ([androidx.room.util.TableInfo]) compares the live table's
         * *entire* column set against the entity's, so an undeclared-but-still-present column
         * fails validation exactly like a genuinely missing one would — see
         * [ExerciseEntity.deprecatedPrimaryMuscleHead] (this exact gap crashed every upgrade in
         * `debug13.9`, caught and fixed the same day via [LogEzDatabaseMigrationTest]'s real-open
         * reproduction test).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `exercises` ADD COLUMN `muscle_heads` TEXT NOT NULL DEFAULT '[]'")
                db.execSQL(
                    """
                    UPDATE `exercises`
                    SET `muscle_heads` = '["' || `primary_muscle_head` || '"]'
                    WHERE `primary_muscle_head` IS NOT NULL
                    """.trimIndent(),
                )
            }
        }

        /**
         * v4 -> v5 (M11 circuits): one defaulted TEXT column on each of `routines` and `workouts`.
         * Purely additive — every pre-existing row reads back as REGULAR, which is exactly what it
         * was. The `DEFAULT 'REGULAR'` literal must stay byte-identical to the entities'
         * `@ColumnInfo(defaultValue = "'REGULAR'")` declarations or Room's post-migration schema
         * validation rejects the live table on the next open (the `debug13.9` failure mode).
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `routines` ADD COLUMN `structure` TEXT NOT NULL DEFAULT 'REGULAR'")
                db.execSQL("ALTER TABLE `workouts` ADD COLUMN `structure` TEXT NOT NULL DEFAULT 'REGULAR'")
            }
        }

        /** v5 -> v6 (M21a): a brand-new table only, nothing existing changes shape. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // FOREIGN KEY clause required verbatim -- ActivityTrackEntity declares one, and
                // Room's post-migration TableInfo comparison rejects a table missing it exactly
                // like a genuinely wrong column would (the debug13.9 failure mode).
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_tracks` (
                        `id` TEXT NOT NULL,
                        `workout_set_id` TEXT NOT NULL,
                        `route_polyline` TEXT,
                        `point_count` INTEGER NOT NULL,
                        `avg_accuracy_m` REAL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`workout_set_id`) REFERENCES `workout_sets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_activity_tracks_workout_set_id` ON `activity_tracks` (`workout_set_id`)",
                )
            }
        }

        /** v6 -> v7 (M21e): a brand-new table only, nothing existing changes shape. */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `daily_wellness_totals` (
                        `date` TEXT NOT NULL,
                        `steps` INTEGER NOT NULL,
                        `calories_burned` REAL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`date`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /** v7 -> v8 (M21f): a brand-new table only, nothing existing changes shape. */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // FOREIGN KEY clause required verbatim -- WorkoutHeartRateSampleEntity declares
                // one, and Room's post-migration TableInfo comparison rejects a table missing it
                // exactly like a genuinely wrong column would (the debug13.9 failure mode).
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workout_heart_rate_samples` (
                        `id` TEXT NOT NULL,
                        `workout_id` TEXT NOT NULL,
                        `recorded_at` INTEGER NOT NULL,
                        `bpm` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`workout_id`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_workout_heart_rate_samples_workout_id` ON `workout_heart_rate_samples` (`workout_id`)",
                )
            }
        }

        /**
         * v8 -> v9 (M23a): one defaulted column on `workouts`, plus a backfill. Additive — every
         * pre-existing row reads back as STRENGTH, which is what it was, except the GPS runs the
         * backfill finds. The `DEFAULT 'STRENGTH'` literal must stay byte-identical to
         * [WorkoutEntity.kind]'s `@ColumnInfo(defaultValue = "'STRENGTH'")` or Room's
         * post-migration validation rejects the live table on the next open — this is the first
         * defaulted NOT NULL column since [MIGRATION_4_5], i.e. the first one back inside the
         * exact blast radius of the debug13.9 failure.
         *
         * The backfill is exact rather than a guess: an `activity_tracks` row is written in
         * exactly one place (`ActivityTrackingController.finishTracking`), for exactly one
         * `workout_sets` row, for exactly one GPS session — so "has a track" and "was GPS-tracked"
         * are the same set for every *finished* run. A run still in flight at upgrade time has no
         * track row yet and stays STRENGTH; that is the one row this cannot classify, and the
         * interrupted-run recovery path degrades it to the Logger with a blank set.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `workouts` ADD COLUMN `kind` TEXT NOT NULL DEFAULT 'STRENGTH'")
                db.execSQL(
                    """
                    UPDATE `workouts` SET `kind` = 'GPS_TRACKED'
                    WHERE `id` IN (
                        SELECT we.`workout_id`
                        FROM `activity_tracks` t
                        JOIN `workout_sets` ws ON ws.`id` = t.`workout_set_id`
                        JOIN `workout_exercises` we ON we.`id` = ws.`workout_exercise_id`
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * v9 -> v10 (walk/run summary, 2026-09-26): one nullable column on `activity_tracks`, the
         * time each route point was recorded, so a finished run's splits and pace chart can be
         * rebuilt. Additive with no default: every run tracked before this reads back null and
         * simply shows no splits, which is honest, since its timing was never kept.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `activity_tracks` ADD COLUMN `route_times` TEXT")
            }
        }
    }
}
