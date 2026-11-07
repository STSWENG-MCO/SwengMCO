package com.enil.logez.core.data.backup

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
import com.enil.logez.core.domain.model.MuscleHead
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every backed-up table must survive entity → DTO → JSON → DTO → entity unchanged. Anything that
 * does not is data the user silently loses on restore, which is the one operation where a silent
 * loss is unacceptable.
 *
 * Fixtures deliberately populate every nullable column and use multi-element enum lists, because
 * a round trip over all-null rows proves almost nothing.
 */
class BackupDtoRoundTripTest {
    private val write = BackupFormat.jsonWrite
    private val read = BackupFormat.jsonRead

    private inline fun <reified D> roundTrip(dto: D): D where D : Any =
        read.decodeFromString<D>(write.encodeToString(dto))

    @Test
    fun `exercise survives, including both enum lists`() {
        val entity = ExerciseEntity(
            id = "e1", name = "Bench Press", exerciseType = ExerciseType.WEIGHT_REPS,
            primaryMuscleGroup = MuscleGroup.CHEST,
            secondaryMuscleGroups = listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
            equipment = Equipment.BARBELL, instructions = "1. Lie down\n2. **Brace**",
            mediaPath = "exercise_media/abc.jpg", isCustom = true,
            isBodyweightVolumeEligible = true, isDeleted = true,
            createdAt = 10, updatedAt = 20,
            muscleHeads = listOf(MuscleHead.UPPER_CHEST, MuscleHead.TRICEPS_LONG_HEAD),
            deprecatedPrimaryMuscleHead = null,
        )
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `a soft-deleted exercise keeps its flag, because restore needs the row for its foreign keys`() {
        val entity = ExerciseEntity(
            id = "e2", name = "Retired", exerciseType = ExerciseType.WEIGHT_REPS,
            primaryMuscleGroup = MuscleGroup.OTHER, secondaryMuscleGroups = emptyList(),
            equipment = Equipment.NONE, instructions = "", mediaPath = null, isCustom = false,
            isBodyweightVolumeEligible = false, isDeleted = true, createdAt = 1, updatedAt = 1,
            muscleHeads = emptyList(), deprecatedPrimaryMuscleHead = null,
        )
        assertTrue(roundTrip(entity.toDto()).toEntity().isDeleted)
    }

    @Test
    fun `routine folder survives`() {
        val entity = RoutineFolderEntity("f1", "Push", 3, 10, 20)
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `routine survives, including its structure discriminator`() {
        val entity = RoutineEntity(
            id = "r1", folderId = "f1", name = "Push A", notes = "heavy",
            orderIndex = 2, createdAt = 10, updatedAt = 20, structure = WorkoutStructure.CIRCUIT,
        )
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `routine exercise survives`() {
        val entity = RoutineExerciseEntity("re1", "r1", "e1", 1, 2, 90, "pause at chest")
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `routine set survives every target column`() {
        val entity = RoutineSetEntity(
            id = "rs1", routineExerciseId = "re1", orderIndex = 0, setType = SetType.WARMUP,
            targetWeightKg = 60.5, targetReps = 8, targetRepRangeMin = 6, targetRepRangeMax = 10,
            targetDurationSeconds = 45, targetDistanceMeters = 1200.0,
        )
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `workout survives, including kind and structure`() {
        val entity = WorkoutEntity(
            id = "w1", routineId = "r1", title = "Push Day", notes = "felt strong, back tight",
            status = WorkoutStatus.COMPLETED, startedAt = 100, endedAt = 200, durationSeconds = 3600,
            createdAt = 100, updatedAt = 200, structure = WorkoutStructure.CIRCUIT,
            kind = WorkoutKind.GPS_TRACKED,
        )
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `workout exercise survives`() {
        val entity = WorkoutExerciseEntity("we1", "w1", "e1", 0, 1, 120, "notes")
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `workout set survives every metric, including the one the CSV drops`() {
        val entity = WorkoutSetEntity(
            id = "ws1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.DROPSET,
            weightKg = 102.5, reps = 5, durationSeconds = 60, distanceMeters = 5250.0,
            rpe = 8.5, customMetric = 42.0, isCompleted = true, completedAt = 300,
        )
        // customMetric has no CSV column, which is exactly why the JSON backup has to carry it.
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `activity track survives, so a GPS route is not lost on restore`() {
        val entity = ActivityTrackEntity("t1", "ws1", "_p~iF~ps|U_ulLnnqC", 412, 4.5, routeTimes = "AEE")
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `an activity track from a backup made before route times existed restores with none`() {
        val oldLine = """{"id":"t1","workout_set_id":"ws1","route_polyline":"abc","point_count":3,"avg_accuracy_m":4.5}"""
        val restored = BackupFormat.jsonRead.decodeFromString(ActivityTrackDto.serializer(), oldLine).toEntity()
        assertEquals(null, restored.routeTimes)
        assertEquals("abc", restored.routePolyline)
    }

    @Test
    fun `heart rate sample survives`() {
        val entity = WorkoutHeartRateSampleEntity("hr1", "w1", 150, 132L)
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `body measurement survives all seventeen metrics`() {
        val entity = BodyMeasurementEntity(
            date = "2026-09-22", weightKg = 82.4, leanMassKg = 65.1, fatPercent = 18.2,
            neckCm = 38.0, shoulderCm = 120.0, chestCm = 102.0, leftBicepCm = 36.5,
            rightBicepCm = 36.8, leftForearmCm = 29.0, rightForearmCm = 29.2, abdomenCm = 84.0,
            waistCm = 80.0, hipsCm = 96.0, leftThighCm = 58.0, rightThighCm = 58.4,
            leftCalfCm = 38.0, rightCalfCm = 38.2, updatedAt = 500,
        )
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `progress photo survives with its filesDir-relative path`() {
        val entity = ProgressPhotoEntity("p1", "2026-09-22", "progress_photos/abc.jpg", 600)
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `goal survives, so a restore does not silently drop the user's targets`() {
        val entity = GoalDefinitionEntity("g1", GoalMetric.WORKOUT_COUNT, GoalPeriod.WEEKLY, 4.0, 10, 20)
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `daily wellness total survives`() {
        val entity = DailyWellnessTotalEntity("2026-09-22", 9312L, 512.5, 700)
        assertEquals(entity, roundTrip(entity.toDto()).toEntity())
    }

    @Test
    fun `column names are the wire format, so renaming a Kotlin field cannot break old backups`() {
        val json = write.encodeToString(
            WorkoutSetEntity(
                id = "ws1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL,
                weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null,
                rpe = null, customMetric = null, isCompleted = true, completedAt = 1,
            ).toDto(),
        )
        assertTrue(json, json.contains("\"workout_exercise_id\""))
        assertTrue(json, json.contains("\"weight_kg\""))
        assertTrue(json, json.contains("\"is_completed\""))
    }

    @Test
    fun `a backup missing a column this app knows about still decodes`() {
        // The forward-compatibility case: a backup written before `kind` existed.
        val old = """{"id":"w1","title":"Legacy","status":"COMPLETED","started_at":5}"""
        val entity = read.decodeFromString<WorkoutDto>(old).toEntity()
        assertEquals("w1", entity.id)
        assertEquals(WorkoutKind.STRENGTH, entity.kind)
        assertEquals(WorkoutStructure.REGULAR, entity.structure)
    }

    @Test
    fun `a backup carrying a column this app does not know about still decodes`() {
        val future = """{"id":"f1","name":"Folder","order_index":0,"created_at":1,"updated_at":2,"colour":"red"}"""
        assertEquals("Folder", read.decodeFromString<RoutineFolderDto>(future).toEntity().name)
    }
}
