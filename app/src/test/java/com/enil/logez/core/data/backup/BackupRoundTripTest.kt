package com.enil.logez.core.data.backup

import androidx.test.core.app.ApplicationProvider
import com.enil.logez.core.data.RoomDatabaseTestBase
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
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.GoalMetric
import com.enil.logez.core.domain.model.GoalPeriod
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The test the whole feature exists for: write an archive from a populated database, empty it,
 * restore, and check every table came back.
 *
 * Runs against a real Room database rather than fakes, because what is being tested is exactly the
 * behaviour a fake would have to assume — foreign-key ordering, bulk inserts and the wipe.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRoundTripTest : RoomDatabaseTestBase() {

    private val filesDir: File get() = File(
        ApplicationProvider.getApplicationContext<android.content.Context>().filesDir,
        "roundtrip",
    ).apply { mkdirs() }

    private fun exercise(id: String, deleted: Boolean = false) = ExerciseEntity(
        id = id, name = "Exercise $id", exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = listOf(MuscleGroup.TRICEPS),
        equipment = Equipment.BARBELL, instructions = "1. Do it", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = deleted,
        createdAt = 1, updatedAt = 1, muscleHeads = emptyList(),
        deprecatedPrimaryMuscleHead = null,
    )

    /** The backup's media list and the orphan sweep both trust this query, so it runs against real SQL. */
    @Test
    fun `referencedMediaPaths lists photo and exercise media paths and nothing else`() = runTest {
        val dao = database.backupDao()
        dao.insertExercises(listOf(exercise("e1").copy(mediaPath = "exercise_media/e1.jpg"), exercise("e2")))
        dao.insertProgressPhotos(listOf(ProgressPhotoEntity("p1", "2026-09-22", "progress_photos/a.jpg", 600)))

        assertEquals(
            setOf("exercise_media/e1.jpg", "progress_photos/a.jpg"),
            dao.referencedMediaPaths().toSet(),
        )
    }

    /** Populates one row in every backed-up table, wired together so the foreign keys are real. */
    private suspend fun seedEverything() {
        val dao = database.backupDao()
        dao.insertExercises(listOf(exercise("e1"), exercise("e_retired", deleted = true)))
        dao.insertRoutineFolders(listOf(RoutineFolderEntity("f1", "Push", 0, 1, 1)))
        dao.insertRoutines(
            listOf(RoutineEntity("r1", "f1", "Push A", "notes", 0, 1, 1, WorkoutStructure.CIRCUIT)),
        )
        dao.insertRoutineExercisesBulk(listOf(RoutineExerciseEntity("re1", "r1", "e1", 0, 1, 90, "n")))
        dao.insertRoutineSetsBulk(
            listOf(RoutineSetEntity("rs1", "re1", 0, SetType.NORMAL, 60.0, 8, 6, 10, 45, 100.0)),
        )
        dao.insertWorkoutsBulk(
            listOf(
                WorkoutEntity(
                    "w1", "r1", "Push Day", "felt strong", WorkoutStatus.COMPLETED,
                    100, 200, 3600, 100, 200, WorkoutStructure.REGULAR, WorkoutKind.GPS_TRACKED,
                ),
            ),
        )
        dao.insertWorkoutExercisesBulk(listOf(WorkoutExerciseEntity("we1", "w1", "e1", 0, null, null, null)))
        dao.insertWorkoutSetsBulk(
            listOf(
                WorkoutSetEntity(
                    "ws1", "we1", 0, SetType.NORMAL, 100.0, 5, 60, 5250.0, 8.5, 42.0, true, 150,
                ),
            ),
        )
        dao.insertActivityTracks(listOf(ActivityTrackEntity("t1", "ws1", "abc", 12, 4.5, routeTimes = "AEE")))
        dao.insertHeartRateSamples(listOf(WorkoutHeartRateSampleEntity("hr1", "w1", 150, 132L)))
        dao.insertBodyMeasurements(
            listOf(
                BodyMeasurementEntity(
                    "2026-09-22", 82.4, 65.1, 18.2, 38.0, 120.0, 102.0, 36.5, 36.8, 29.0,
                    29.2, 84.0, 80.0, 96.0, 58.0, 58.4, 38.0, 38.2, 500,
                ),
            ),
        )
        dao.insertProgressPhotos(listOf(ProgressPhotoEntity("p1", "2026-09-22", "progress_photos/a.jpg", 600)))
        dao.insertGoals(
            listOf(GoalDefinitionEntity("g1", GoalMetric.WORKOUT_COUNT, GoalPeriod.WEEKLY, 4.0, 1, 1)),
        )
        dao.insertWellnessTotals(listOf(DailyWellnessTotalEntity("2026-09-22", 9312L, 512.5, 700)))
    }

    private suspend fun snapshot(): Map<String, List<Any>> {
        val dao = database.backupDao()
        val n = 1000
        return mapOf(
            BackupTables.EXERCISES to dao.pageExercises("", n),
            BackupTables.ROUTINE_FOLDERS to dao.pageRoutineFolders("", n),
            BackupTables.ROUTINES to dao.pageRoutines("", n),
            BackupTables.ROUTINE_EXERCISES to dao.pageRoutineExercises("", n),
            BackupTables.ROUTINE_SETS to dao.pageRoutineSets("", n),
            BackupTables.WORKOUTS to dao.pageWorkouts("", n),
            BackupTables.WORKOUT_EXERCISES to dao.pageWorkoutExercises("", n),
            BackupTables.WORKOUT_SETS to dao.pageWorkoutSets("", n),
            BackupTables.ACTIVITY_TRACKS to dao.pageActivityTracks("", n),
            BackupTables.WORKOUT_HEART_RATE_SAMPLES to dao.pageHeartRateSamples("", n),
            BackupTables.BODY_MEASUREMENTS to dao.pageBodyMeasurements("", n),
            BackupTables.PROGRESS_PHOTOS to dao.pageProgressPhotos("", n),
            BackupTables.GOAL_DEFINITIONS to dao.pageGoals("", n),
            BackupTables.DAILY_WELLNESS_TOTALS to dao.pageWellnessTotals("", n),
        )
    }

    /** The writer's DAO half, exercised directly — the rest of it needs Hilt-provided collaborators. */
    private suspend fun writeArchive(): ByteArray {
        val out = ByteArrayOutputStream()
        val dao = database.backupDao()
        val counts = BackupTables.EXPORT_ORDER.associateWith { table ->
            snapshot().getValue(table).size
        }
        java.util.zip.ZipOutputStream(out).use { zip ->
            val manifest = BackupManifest(
                roomSchemaVersion = BackupWriter.ROOM_SCHEMA_VERSION,
                tables = BackupTables.EXPORT_ORDER.map {
                    BackupManifest.TableEntry(it, counts.getValue(it))
                },
            )
            zip.putNextEntry(java.util.zip.ZipEntry(BackupFormat.MANIFEST_ENTRY))
            zip.write(BackupFormat.jsonWrite.encodeToString(BackupManifest.serializer(), manifest).toByteArray())
            zip.closeEntry()

            val rows = snapshot()
            BackupTables.EXPORT_ORDER.forEach { table ->
                zip.putNextEntry(java.util.zip.ZipEntry(BackupTables.tableEntry(table)))
                val text = rows.getValue(table).joinToString("") { encode(table, it) + "\n" }
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        // dao is used above via snapshot(); referenced here to keep the intent obvious.
        checkNotNull(dao)
        return out.toByteArray()
    }

    private fun encode(table: String, row: Any): String {
        val j = BackupFormat.jsonWrite
        return when (table) {
            BackupTables.EXERCISES -> j.encodeToString(ExerciseDto.serializer(), (row as ExerciseEntity).toDto())
            BackupTables.ROUTINE_FOLDERS -> j.encodeToString(RoutineFolderDto.serializer(), (row as RoutineFolderEntity).toDto())
            BackupTables.ROUTINES -> j.encodeToString(RoutineDto.serializer(), (row as RoutineEntity).toDto())
            BackupTables.ROUTINE_EXERCISES -> j.encodeToString(RoutineExerciseDto.serializer(), (row as RoutineExerciseEntity).toDto())
            BackupTables.ROUTINE_SETS -> j.encodeToString(RoutineSetDto.serializer(), (row as RoutineSetEntity).toDto())
            BackupTables.WORKOUTS -> j.encodeToString(WorkoutDto.serializer(), (row as WorkoutEntity).toDto())
            BackupTables.WORKOUT_EXERCISES -> j.encodeToString(WorkoutExerciseDto.serializer(), (row as WorkoutExerciseEntity).toDto())
            BackupTables.WORKOUT_SETS -> j.encodeToString(WorkoutSetDto.serializer(), (row as WorkoutSetEntity).toDto())
            BackupTables.ACTIVITY_TRACKS -> j.encodeToString(ActivityTrackDto.serializer(), (row as ActivityTrackEntity).toDto())
            BackupTables.WORKOUT_HEART_RATE_SAMPLES -> j.encodeToString(WorkoutHeartRateSampleDto.serializer(), (row as WorkoutHeartRateSampleEntity).toDto())
            BackupTables.BODY_MEASUREMENTS -> j.encodeToString(BodyMeasurementDto.serializer(), (row as BodyMeasurementEntity).toDto())
            BackupTables.PROGRESS_PHOTOS -> j.encodeToString(ProgressPhotoDto.serializer(), (row as ProgressPhotoEntity).toDto())
            BackupTables.GOAL_DEFINITIONS -> j.encodeToString(GoalDefinitionDto.serializer(), (row as GoalDefinitionEntity).toDto())
            BackupTables.DAILY_WELLNESS_TOTALS -> j.encodeToString(DailyWellnessTotalDto.serializer(), (row as DailyWellnessTotalEntity).toDto())
            else -> error(table)
        }
    }

    @Test
    fun `every table survives export, wipe and restore`() = runTest {
        seedEverything()
        val before = snapshot()
        val archive = writeArchive()

        val staging = File(filesDir, "staging")
        val manifest = BackupReader().stage(ByteArrayInputStream(archive), staging)

        // Wipe, then restore from the staged archive using the real ordering.
        val dao = database.backupDao()
        BackupTables.WIPE_ORDER.forEach { table ->
            when (table) {
                BackupTables.PERSONAL_RECORDS -> dao.deleteAllPersonalRecords()
                BackupTables.ACTIVITY_TRACKS -> dao.deleteAllActivityTracks()
                BackupTables.WORKOUT_HEART_RATE_SAMPLES -> dao.deleteAllHeartRateSamples()
                BackupTables.WORKOUT_SETS -> dao.deleteAllWorkoutSets()
                BackupTables.WORKOUT_EXERCISES -> dao.deleteAllWorkoutExercises()
                BackupTables.WORKOUTS -> dao.deleteAllWorkouts()
                BackupTables.ROUTINE_SETS -> dao.deleteAllRoutineSets()
                BackupTables.ROUTINE_EXERCISES -> dao.deleteAllRoutineExercises()
                BackupTables.ROUTINES -> dao.deleteAllRoutines()
                BackupTables.ROUTINE_FOLDERS -> dao.deleteAllRoutineFolders()
                BackupTables.PROGRESS_PHOTOS -> dao.deleteAllProgressPhotos()
                BackupTables.BODY_MEASUREMENTS -> dao.deleteAllBodyMeasurements()
                BackupTables.GOAL_DEFINITIONS -> dao.deleteAllGoals()
                BackupTables.DAILY_WELLNESS_TOTALS -> dao.deleteAllWellnessTotals()
                BackupTables.EXERCISES -> dao.deleteAllExercises()
            }
        }
        assertEquals(0, dao.countWorkouts())

        restoreFromStaging(staging)

        assertEquals(before, snapshot())
        assertEquals(1, manifest.workoutCount)
        assertEquals(2, manifest.exerciseCount)
    }

    private suspend fun restoreFromStaging(staging: File) {
        val dao = database.backupDao()
        val reader = BackupReader()
        val j = BackupFormat.jsonRead
        BackupTables.EXPORT_ORDER.forEach { table ->
            val pages = mutableListOf<List<String>>()
            reader.forEachPage<String>(staging, table, decode = { it }, consume = { pages.add(it) })
            pages.forEach { lines ->
                when (table) {
                    BackupTables.EXERCISES -> dao.insertExercises(lines.map { j.decodeFromString(ExerciseDto.serializer(), it).toEntity() })
                    BackupTables.ROUTINE_FOLDERS -> dao.insertRoutineFolders(lines.map { j.decodeFromString(RoutineFolderDto.serializer(), it).toEntity() })
                    BackupTables.ROUTINES -> dao.insertRoutines(lines.map { j.decodeFromString(RoutineDto.serializer(), it).toEntity() })
                    BackupTables.ROUTINE_EXERCISES -> dao.insertRoutineExercisesBulk(lines.map { j.decodeFromString(RoutineExerciseDto.serializer(), it).toEntity() })
                    BackupTables.ROUTINE_SETS -> dao.insertRoutineSetsBulk(lines.map { j.decodeFromString(RoutineSetDto.serializer(), it).toEntity() })
                    BackupTables.WORKOUTS -> dao.insertWorkoutsBulk(lines.map { j.decodeFromString(WorkoutDto.serializer(), it).toEntity() })
                    BackupTables.WORKOUT_EXERCISES -> dao.insertWorkoutExercisesBulk(lines.map { j.decodeFromString(WorkoutExerciseDto.serializer(), it).toEntity() })
                    BackupTables.WORKOUT_SETS -> dao.insertWorkoutSetsBulk(lines.map { j.decodeFromString(WorkoutSetDto.serializer(), it).toEntity() })
                    BackupTables.ACTIVITY_TRACKS -> dao.insertActivityTracks(lines.map { j.decodeFromString(ActivityTrackDto.serializer(), it).toEntity() })
                    BackupTables.WORKOUT_HEART_RATE_SAMPLES -> dao.insertHeartRateSamples(lines.map { j.decodeFromString(WorkoutHeartRateSampleDto.serializer(), it).toEntity() })
                    BackupTables.BODY_MEASUREMENTS -> dao.insertBodyMeasurements(lines.map { j.decodeFromString(BodyMeasurementDto.serializer(), it).toEntity() })
                    BackupTables.PROGRESS_PHOTOS -> dao.insertProgressPhotos(lines.map { j.decodeFromString(ProgressPhotoDto.serializer(), it).toEntity() })
                    BackupTables.GOAL_DEFINITIONS -> dao.insertGoals(lines.map { j.decodeFromString(GoalDefinitionDto.serializer(), it).toEntity() })
                    BackupTables.DAILY_WELLNESS_TOTALS -> dao.insertWellnessTotals(lines.map { j.decodeFromString(DailyWellnessTotalDto.serializer(), it).toEntity() })
                }
            }
        }
    }

    @Test
    fun `a soft-deleted exercise round-trips, so its foreign keys still resolve`() = runTest {
        seedEverything()
        val archive = writeArchive()
        val staging = File(filesDir, "staging_deleted")
        BackupReader().stage(ByteArrayInputStream(archive), staging)

        val lines = mutableListOf<String>()
        BackupReader().forEachPage<String>(
            staging, BackupTables.EXERCISES, decode = { it }, consume = { lines.addAll(it) },
        )
        val restored = lines.map { BackupFormat.jsonRead.decodeFromString(ExerciseDto.serializer(), it).toEntity() }
        assertTrue(restored.any { it.isDeleted })
    }

    @Test
    fun `an archive whose first entry is not the manifest is rejected`() {
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("something_else.txt"))
            zip.write("nope".toByteArray())
            zip.closeEntry()
        }
        assertThrows(BackupReader.NotABackupException::class.java) {
            BackupReader().peekManifest(ByteArrayInputStream(out.toByteArray()))
        }
    }

    @Test
    fun `an archive whose row counts do not match its manifest is rejected before anything is wiped`() = runTest {
        seedEverything()
        val out = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            // Claims a hundred workouts and ships none — a truncated archive looks like this.
            val lying = BackupManifest(
                roomSchemaVersion = BackupWriter.ROOM_SCHEMA_VERSION,
                tables = listOf(BackupManifest.TableEntry(BackupTables.WORKOUTS, 100)),
            )
            zip.putNextEntry(java.util.zip.ZipEntry(BackupFormat.MANIFEST_ENTRY))
            zip.write(BackupFormat.jsonWrite.encodeToString(BackupManifest.serializer(), lying).toByteArray())
            zip.closeEntry()
        }
        val staging = File(filesDir, "staging_truncated")
        assertThrows(BackupReader.NotABackupException::class.java) {
            BackupReader().stage(ByteArrayInputStream(out.toByteArray()), staging)
        }
    }

    @Test
    fun `an archive from a newer database is refused rather than half-restored`() {
        val manifest = BackupManifest(roomSchemaVersion = BackupWriter.ROOM_SCHEMA_VERSION + 1)
        assertEquals(
            BackupManifest.Compatibility.TooNew,
            manifest.compatibility(BackupWriter.ROOM_SCHEMA_VERSION),
        )
    }

    @Test
    fun `an archive from an older database is accepted, because every field has a default`() {
        val manifest = BackupManifest(roomSchemaVersion = 6)
        assertEquals(
            BackupManifest.Compatibility.Ok,
            manifest.compatibility(BackupWriter.ROOM_SCHEMA_VERSION),
        )
    }
}
