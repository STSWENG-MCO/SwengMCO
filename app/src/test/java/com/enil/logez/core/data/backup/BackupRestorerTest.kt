package com.enil.logez.core.data.backup

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.data.repository.ExerciseRepositoryImpl
import com.enil.logez.core.data.repository.MeasurementRepositoryImpl
import com.enil.logez.core.data.repository.PersonalRecordsRepositoryImpl
import com.enil.logez.core.data.repository.RoomTransactionRunner
import com.enil.logez.core.data.repository.WorkoutRepositoryImpl
import com.enil.logez.core.data.seed.SeedManager
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWidgetRefresher
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Drives the real restorer, with the real transaction runner, the real record rebuild and a real
 * preferences store — everything the DAO-level round trip deliberately bypassed. Run under a
 * timeout so a deadlock inside the transaction reads as a failure rather than a hung build.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestorerTest : RoomDatabaseTestBase() {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val seedVersionKey = intPreferencesKey("lastAppliedSeedVersion")

    private fun filesDir(): File = File(context.filesDir, "restorer_${System.nanoTime()}").apply { mkdirs() }

    private fun dataStore(dir: File) = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) { File(dir, "settings.preferences_pb") }

    private fun exercise(id: String) = ExerciseEntity(
        id = id, name = "Exercise $id", exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = MuscleGroup.CHEST, secondaryMuscleGroups = emptyList(),
        equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false,
        createdAt = 1, updatedAt = 1, muscleHeads = emptyList(), deprecatedPrimaryMuscleHead = null,
    )

    private suspend fun seedOneWorkout() {
        val dao = database.backupDao()
        dao.insertExercises(listOf(exercise("e1")))
        dao.insertWorkoutsBulk(
            listOf(
                WorkoutEntity(
                    "w1", null, "Push Day", null, WorkoutStatus.COMPLETED, 100, 200, 3600, 100, 200,
                    WorkoutStructure.REGULAR, WorkoutKind.STRENGTH,
                ),
            ),
        )
        dao.insertWorkoutExercisesBulk(listOf(WorkoutExerciseEntity("we1", "w1", "e1", 0, null, null, null)))
        dao.insertWorkoutSetsBulk(
            listOf(WorkoutSetEntity("ws1", "we1", 0, SetType.NORMAL, 100.0, 5, null, null, null, null, true, 150)),
        )
    }

    private fun restorer(
        settings: FakeSettingsRepository,
        seedManager: SeedManager,
        widget: FakeWidgetRefresher,
    ) = BackupRestorer(
        backupDao = database.backupDao(),
        reader = BackupReader(),
        transactionRunner = RoomTransactionRunner(database),
        personalRecordsUpdater = PersonalRecordsUpdater(
            WorkoutRepositoryImpl(database.workoutDao(), database.analyticsDao()),
            ExerciseRepositoryImpl(database.exerciseDao(), FakeClock()),
            PersonalRecordsRepositoryImpl(database.recordsDao()),
            MeasurementRepositoryImpl(database.measurementDao()),
            settings,
        ),
        settingsRepository = settings,
        seedManager = seedManager,
        widgetRefresher = widget,
        logger = AppLogger.NoOp,
    )

    @Test
    fun `restore brings back a deleted workout, rebuilds its records, and cleans up after itself`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val store = dataStore(dir)
            val settings = FakeSettingsRepository(UserSettings(weeklyActiveDayTarget = 6))
            val seedManager = SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp)
            val widget = FakeWidgetRefresher()

            seedOneWorkout()
            val writer = BackupWriter(database.backupDao(), settings, seedManager, FakeClock())
            val archive = ByteArrayOutputStream().also { writer.write(it, dir, "0.1.0", 1) }.toByteArray()

            // Lose the workout, then change a setting, so the restore has something to undo.
            database.workoutDao().deleteWorkoutById("w1")
            settings.setWeeklyActiveDayTarget(2)
            assertNull(database.workoutDao().getById("w1"))

            val staging = RestoreMarker.stagingDirIn(dir)
            val manifest = BackupReader().stage(ByteArrayInputStream(archive), staging)
            restorer(settings, seedManager, widget).restore(staging, dir, manifest)

            assertEquals("Push Day", database.workoutDao().getById("w1")?.title)
            assertTrue(database.recordsDao().getForExercise("e1").isNotEmpty())
            assertEquals(6, settings.settings.first().weeklyActiveDayTarget)
            assertFalse(File(dir, "restore.marker").exists())
            assertFalse(staging.exists())
            assertTrue(widget.refreshCount >= 1)
        }
    }

    @Test
    fun `restore resets the seed marker so an older library cannot strand the device`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val store = dataStore(dir)
            val settings = FakeSettingsRepository()
            val seedManager = SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp)

            seedOneWorkout()
            val archive = ByteArrayOutputStream()
                .also { BackupWriter(database.backupDao(), settings, seedManager, FakeClock()).write(it, dir, "0.1.0", 1) }
                .toByteArray()

            // The device believes it is on a newer seed than the backup carries.
            store.edit { it[seedVersionKey] = 99 }

            val staging = RestoreMarker.stagingDirIn(dir)
            val manifest = BackupReader().stage(ByteArrayInputStream(archive), staging)
            restorer(settings, seedManager, FakeWidgetRefresher()).restore(staging, dir, manifest)

            // Re-seeding ran: the marker is no longer the stale 99, it is whatever the asset says.
            val after = store.data.first()[seedVersionKey]
            assertTrue("seed marker should have been reset and re-applied, was $after", after != 99)
        }
    }

    /**
     * 2026-09-25 review: a restore interrupted after its photo swap but before the marker was
     * cleared used to move the (already restored) live photos aside and delete them on resume,
     * because nothing was left staged. Staging now always creates both media folders, so a
     * missing one means "already swapped" and the live folder is left alone.
     */
    @Test
    fun `resuming after the photo swap already happened keeps the restored photos`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val store = dataStore(dir)
            val settings = FakeSettingsRepository()
            val seedManager = SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp)

            // State after swapMedia moved both staged folders into place: live photos present,
            // the staging dir left with no media folders, the marker still at MEDIA.
            val livePhoto = File(dir, "progress_photos/p1.jpg").apply { parentFile!!.mkdirs(); writeText("restored") }
            File(dir, "exercise_media").mkdirs()
            val staging = RestoreMarker.stagingDirIn(dir).apply { mkdirs() }
            RestoreMarker(dir).write(RestoreMarker.Phase.MEDIA, staging)

            restorer(settings, seedManager, FakeWidgetRefresher()).resumeIfInterrupted(dir)

            assertTrue("restored photo must survive the resume", livePhoto.isFile)
            assertEquals("restored", livePhoto.readText())
            assertFalse(File(dir, "restore.marker").exists())
        }
    }

    @Test
    fun `staging always creates both media folders, even for a backup without photos`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val store = dataStore(dir)
            val settings = FakeSettingsRepository()
            val seedManager = SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp)
            seedOneWorkout()
            val archive = ByteArrayOutputStream()
                .also { BackupWriter(database.backupDao(), settings, seedManager, FakeClock()).write(it, dir, "0.1.0", 1) }
                .toByteArray()

            val staging = RestoreMarker.stagingDirIn(dir)
            BackupReader().stage(ByteArrayInputStream(archive), staging)

            assertTrue(File(staging, "media/progress_photos").isDirectory)
            assertTrue(File(staging, "media/exercise_media").isDirectory)
        }
    }

    @Test
    fun `a leftover staged restore with no marker is deleted at launch`() = runBlocking {
        withTimeout(60_000) {
            val dir = filesDir()
            val store = dataStore(dir)
            val settings = FakeSettingsRepository()
            val seedManager = SeedManager(context, database.exerciseDao(), store, RoomTransactionRunner(database), AppLogger.NoOp)
            val staging = RestoreMarker.stagingDirIn(dir).apply { mkdirs() }
            File(staging, "media/progress_photos/p1.jpg").apply { parentFile!!.mkdirs(); writeText("x") }

            restorer(settings, seedManager, FakeWidgetRefresher()).resumeIfInterrupted(dir)

            assertFalse(staging.exists())
        }
    }
}
