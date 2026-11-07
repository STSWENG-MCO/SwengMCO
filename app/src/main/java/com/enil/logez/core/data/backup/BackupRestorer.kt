package com.enil.logez.core.data.backup

import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.dao.BackupDao
import com.enil.logez.core.data.seed.SeedManager
import com.enil.logez.core.domain.WidgetRefresher
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.TransactionRunner
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Replaces everything in the app with the contents of a staged backup.
 *
 * The ordering here is the whole design, and each step is where it is for a reason:
 *
 * The database work is one transaction, so a failure anywhere in it leaves the user's existing
 * data exactly as it was. The personal-record rebuild is inside that transaction rather than after
 * it, because a crash in between would otherwise leave a full training history with no records and
 * no obvious way to repair it.
 *
 * Photo files cannot be in the transaction — a database commit is atomic and hundreds of file
 * copies are not. They are swapped afterwards, behind a marker file, so an interrupted swap can be
 * finished on the next launch rather than leaving restored rows pointing at half-replaced files.
 *
 * Settings are written after the commit as well: a failure there leaves restored data with the old
 * preferences, which the user can simply set again. The reverse order would lose preferences and
 * then roll the data back.
 *
 * Finally the seed marker is cleared and seeding re-run. Without that, restoring a backup taken at
 * an older seed version leaves the device believing it is already up to date, and the modern
 * exercise library never returns.
 */
@Singleton
class BackupRestorer @Inject constructor(
    private val backupDao: BackupDao,
    private val reader: BackupReader,
    private val transactionRunner: TransactionRunner,
    private val personalRecordsUpdater: PersonalRecordsUpdater,
    private val settingsRepository: SettingsRepository,
    private val seedManager: SeedManager,
    private val widgetRefresher: WidgetRefresher,
    private val logger: AppLogger,
) {
    /**
     * @param stagingDir a directory [BackupReader.stage] has already populated and verified.
     * @param filesDir the app's files directory, where the live photo directories live.
     */
    suspend fun restore(stagingDir: File, filesDir: File, manifest: BackupManifest) {
        val stagedSettings = reader.stagedSettings(stagingDir)

        // Read before the transaction opens: the preferences store is not part of it, and holding
        // a Room transaction open across unrelated I/O is what the transaction runner warns about.
        val includeWarmups = stagedSettings?.includeWarmupsInStats
            ?: personalRecordsUpdater.includeWarmupsInStats()

        val marker = RestoreMarker(filesDir)
        marker.write(RestoreMarker.Phase.DATABASE, stagingDir)

        transactionRunner.runInTransaction {
            BackupTables.WIPE_ORDER.forEach { backupDao.wipe(it) }
            BackupTables.EXPORT_ORDER.forEach { insertTable(stagingDir, it) }

            val touched = backupDao.exerciseIdsWithCompletedHistory()
            if (touched.isNotEmpty()) {
                personalRecordsUpdater.rebuildForExercises(touched.toSet(), workoutId = "", includeWarmups)
            }
        }

        marker.write(RestoreMarker.Phase.MEDIA, stagingDir)
        swapMedia(stagingDir, filesDir)

        stagedSettings?.let { settingsRepository.replaceAll(it.toUserSettings()) }

        // The library the backup carried may predate the one installed here.
        seedManager.resetSeedVersion()
        runCatching { seedManager.seedIfNeeded() }
            .onFailure { logger.e(TAG, "Re-seeding after a restore failed", it) }

        marker.clear()
        stagingDir.deleteRecursively()
        widgetRefresher.refresh()
    }

    /** Finishes a restore whose file swap was interrupted. Safe to call when there is nothing to do. */
    suspend fun resumeIfInterrupted(filesDir: File) {
        val marker = RestoreMarker(filesDir)
        val phase = marker.read()
        if (phase == null) {
            // No restore was running, so anything still staged belongs to a confirm dialog that died
            // with its process. It is a full copy of a backup, photos included; do not keep it.
            RestoreMarker.stagingDirIn(filesDir).deleteRecursively()
            return
        }
        logger.e(TAG, "Recovering an interrupted restore (phase $phase)", null)
        when (phase) {
            RestoreMarker.Phase.DATABASE -> {
                // The transaction either committed or rolled back cleanly; nothing is half-written,
                // so there is nothing to repair beyond clearing up what was unpacked.
                marker.stagingDir()?.deleteRecursively()
                marker.clear()
            }
            RestoreMarker.Phase.MEDIA -> {
                // The database already holds the backup; finish everything that follows it, in the
                // same order restore() uses: media, then settings, then the seed reset.
                val staging = marker.stagingDir()
                if (staging != null && staging.isDirectory) {
                    swapMedia(staging, filesDir)
                    reader.stagedSettings(staging)?.let { settingsRepository.replaceAll(it.toUserSettings()) }
                }
                // The caller runs seedIfNeeded() straight after this, which re-seeds in full now.
                seedManager.resetSeedVersion()
                staging?.deleteRecursively()
                marker.clear()
                widgetRefresher.refresh()
            }
        }
    }

    /**
     * Moves the restored photo directories into place. Every step checks before acting, so
     * re-running after an interruption resumes rather than breaking.
     */
    private fun swapMedia(stagingDir: File, filesDir: File) {
        listOf(BackupWriter.PROGRESS_PHOTOS_DIR, BackupWriter.EXERCISE_MEDIA_DIR).forEach { name ->
            val live = File(filesDir, name)
            val staged = File(stagingDir, "${BackupFormat.MEDIA_PREFIX}$name")
            val old = File(filesDir, "$name.old")

            // BackupReader.stage always creates both staged folders, so a missing one means an
            // earlier, interrupted run already moved it into place: the live folder IS the restored
            // media. Swapping again would move it aside and delete it.
            if (!staged.isDirectory) {
                live.mkdirs()
                return@forEach
            }

            old.deleteRecursively()
            if (live.exists() && !live.renameTo(old)) live.deleteRecursively()
            // A copy (when rename fails) leaves the staged folder behind, so a resume re-runs this
            // swap; that is harmless because the result is the same files.
            if (!staged.renameTo(live)) staged.copyRecursively(live, overwrite = true)
            old.deleteRecursively()
        }
    }

    private suspend fun insertTable(stagingDir: File, table: String) {
        val json = BackupFormat.jsonRead
        // Each page is collected first so the suspending insert happens outside the reader's
        // non-suspending lambda.
        val pages = mutableListOf<List<String>>()
        reader.forEachPage(stagingDir, table, decode = { it }, consume = { pages.add(it) })

        pages.forEach { lines ->
            when (table) {
                BackupTables.EXERCISES ->
                    backupDao.insertExercises(lines.map { json.decodeFromString<ExerciseDto>(it).toEntity() })
                BackupTables.ROUTINE_FOLDERS ->
                    backupDao.insertRoutineFolders(lines.map { json.decodeFromString<RoutineFolderDto>(it).toEntity() })
                BackupTables.ROUTINES ->
                    backupDao.insertRoutines(lines.map { json.decodeFromString<RoutineDto>(it).toEntity() })
                BackupTables.ROUTINE_EXERCISES ->
                    backupDao.insertRoutineExercisesBulk(lines.map { json.decodeFromString<RoutineExerciseDto>(it).toEntity() })
                BackupTables.ROUTINE_SETS ->
                    backupDao.insertRoutineSetsBulk(lines.map { json.decodeFromString<RoutineSetDto>(it).toEntity() })
                BackupTables.WORKOUTS ->
                    backupDao.insertWorkoutsBulk(lines.map { json.decodeFromString<WorkoutDto>(it).toEntity() })
                BackupTables.WORKOUT_EXERCISES ->
                    backupDao.insertWorkoutExercisesBulk(lines.map { json.decodeFromString<WorkoutExerciseDto>(it).toEntity() })
                BackupTables.WORKOUT_SETS ->
                    backupDao.insertWorkoutSetsBulk(lines.map { json.decodeFromString<WorkoutSetDto>(it).toEntity() })
                BackupTables.ACTIVITY_TRACKS ->
                    backupDao.insertActivityTracks(lines.map { json.decodeFromString<ActivityTrackDto>(it).toEntity() })
                BackupTables.WORKOUT_HEART_RATE_SAMPLES ->
                    backupDao.insertHeartRateSamples(lines.map { json.decodeFromString<WorkoutHeartRateSampleDto>(it).toEntity() })
                BackupTables.BODY_MEASUREMENTS ->
                    backupDao.insertBodyMeasurements(lines.map { json.decodeFromString<BodyMeasurementDto>(it).toEntity() })
                BackupTables.PROGRESS_PHOTOS ->
                    backupDao.insertProgressPhotos(lines.map { json.decodeFromString<ProgressPhotoDto>(it).toEntity() })
                BackupTables.GOAL_DEFINITIONS ->
                    backupDao.insertGoals(lines.map { json.decodeFromString<GoalDefinitionDto>(it).toEntity() })
                BackupTables.DAILY_WELLNESS_TOTALS ->
                    backupDao.insertWellnessTotals(lines.map { json.decodeFromString<DailyWellnessTotalDto>(it).toEntity() })
                else -> error("no inserter for table $table")
            }
        }
    }

    private companion object {
        const val TAG = "BackupRestorer"
    }
}
