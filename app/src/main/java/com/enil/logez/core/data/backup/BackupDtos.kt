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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The backup's wire format: one DTO per backed-up table.
 *
 * Deliberately separate from the Room entities rather than annotating those, for three reasons.
 * The entities hold enums and enum lists that only the Room type converters understand, so
 * serializing them directly would mean a second, parallel definition of every conversion. Property
 * names here are pinned with @SerialName to the SQL column names, so renaming a Kotlin field
 * cannot silently make every backup ever written unreadable. And a column that exists only to
 * satisfy Room's schema validation stays out of the format entirely.
 *
 * Every property has a default so a backup written by an older schema still decodes — combined
 * with ignoreUnknownKeys on the read side, the format tolerates movement in both directions.
 *
 * `personal_records` has no DTO: it is a derived cache, rebuilt from the sets after a restore.
 */

@Serializable
data class ExerciseDto(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("exercise_type") val exerciseType: String = ExerciseType.WEIGHT_REPS.name,
    @SerialName("primary_muscle_group") val primaryMuscleGroup: String = MuscleGroup.OTHER.name,
    @SerialName("secondary_muscle_groups") val secondaryMuscleGroups: List<String> = emptyList(),
    @SerialName("equipment") val equipment: String = Equipment.NONE.name,
    @SerialName("instructions") val instructions: String = "",
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("is_custom") val isCustom: Boolean = false,
    @SerialName("is_bodyweight_volume_eligible") val isBodyweightVolumeEligible: Boolean = false,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("muscle_heads") val muscleHeads: List<String> = emptyList(),
)

fun ExerciseEntity.toDto() = ExerciseDto(
    id = id, name = name, exerciseType = exerciseType.name,
    primaryMuscleGroup = primaryMuscleGroup.name,
    secondaryMuscleGroups = secondaryMuscleGroups.map { it.name },
    equipment = equipment.name, instructions = instructions, mediaPath = mediaPath,
    isCustom = isCustom, isBodyweightVolumeEligible = isBodyweightVolumeEligible,
    isDeleted = isDeleted, createdAt = createdAt, updatedAt = updatedAt,
    muscleHeads = muscleHeads.map { it.name },
)

fun ExerciseDto.toEntity() = ExerciseEntity(
    id = id, name = name, exerciseType = ExerciseType.valueOf(exerciseType),
    primaryMuscleGroup = MuscleGroup.valueOf(primaryMuscleGroup),
    secondaryMuscleGroups = secondaryMuscleGroups.map { MuscleGroup.valueOf(it) },
    equipment = Equipment.valueOf(equipment), instructions = instructions, mediaPath = mediaPath,
    isCustom = isCustom, isBodyweightVolumeEligible = isBodyweightVolumeEligible,
    isDeleted = isDeleted, createdAt = createdAt, updatedAt = updatedAt,
    muscleHeads = muscleHeads.map { MuscleHead.valueOf(it) },
    // Exists only so Room's schema validation passes; always null in practice and never exported.
    deprecatedPrimaryMuscleHead = null,
)

@Serializable
data class RoutineFolderDto(
    @SerialName("id") val id: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("order_index") val orderIndex: Int = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

fun RoutineFolderEntity.toDto() = RoutineFolderDto(id, name, orderIndex, createdAt, updatedAt)
fun RoutineFolderDto.toEntity() = RoutineFolderEntity(id, name, orderIndex, createdAt, updatedAt)

@Serializable
data class RoutineDto(
    @SerialName("id") val id: String = "",
    @SerialName("folder_id") val folderId: String? = null,
    @SerialName("name") val name: String = "",
    @SerialName("notes") val notes: String? = null,
    @SerialName("order_index") val orderIndex: Int = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("structure") val structure: String = WorkoutStructure.REGULAR.name,
)

fun RoutineEntity.toDto() = RoutineDto(
    id, folderId, name, notes, orderIndex, createdAt, updatedAt, structure.name,
)

fun RoutineDto.toEntity() = RoutineEntity(
    id = id, folderId = folderId, name = name, notes = notes, orderIndex = orderIndex,
    createdAt = createdAt, updatedAt = updatedAt, structure = WorkoutStructure.valueOf(structure),
)

@Serializable
data class RoutineExerciseDto(
    @SerialName("id") val id: String = "",
    @SerialName("routine_id") val routineId: String = "",
    @SerialName("exercise_id") val exerciseId: String = "",
    @SerialName("order_index") val orderIndex: Int = 0,
    @SerialName("superset_group") val supersetGroup: Int? = null,
    @SerialName("rest_timer_seconds") val restTimerSeconds: Int? = null,
    @SerialName("notes") val notes: String? = null,
)

fun RoutineExerciseEntity.toDto() =
    RoutineExerciseDto(id, routineId, exerciseId, orderIndex, supersetGroup, restTimerSeconds, notes)

fun RoutineExerciseDto.toEntity() =
    RoutineExerciseEntity(id, routineId, exerciseId, orderIndex, supersetGroup, restTimerSeconds, notes)

@Serializable
data class RoutineSetDto(
    @SerialName("id") val id: String = "",
    @SerialName("routine_exercise_id") val routineExerciseId: String = "",
    @SerialName("order_index") val orderIndex: Int = 0,
    @SerialName("set_type") val setType: String = SetType.NORMAL.name,
    @SerialName("target_weight_kg") val targetWeightKg: Double? = null,
    @SerialName("target_reps") val targetReps: Int? = null,
    @SerialName("target_rep_range_min") val targetRepRangeMin: Int? = null,
    @SerialName("target_rep_range_max") val targetRepRangeMax: Int? = null,
    @SerialName("target_duration_seconds") val targetDurationSeconds: Int? = null,
    @SerialName("target_distance_meters") val targetDistanceMeters: Double? = null,
)

fun RoutineSetEntity.toDto() = RoutineSetDto(
    id, routineExerciseId, orderIndex, setType.name, targetWeightKg, targetReps,
    targetRepRangeMin, targetRepRangeMax, targetDurationSeconds, targetDistanceMeters,
)

fun RoutineSetDto.toEntity() = RoutineSetEntity(
    id = id, routineExerciseId = routineExerciseId, orderIndex = orderIndex,
    setType = SetType.valueOf(setType), targetWeightKg = targetWeightKg, targetReps = targetReps,
    targetRepRangeMin = targetRepRangeMin, targetRepRangeMax = targetRepRangeMax,
    targetDurationSeconds = targetDurationSeconds, targetDistanceMeters = targetDistanceMeters,
)

@Serializable
data class WorkoutDto(
    @SerialName("id") val id: String = "",
    @SerialName("routine_id") val routineId: String? = null,
    @SerialName("title") val title: String = "",
    @SerialName("notes") val notes: String? = null,
    @SerialName("status") val status: String = WorkoutStatus.COMPLETED.name,
    @SerialName("started_at") val startedAt: Long = 0,
    @SerialName("ended_at") val endedAt: Long? = null,
    @SerialName("duration_seconds") val durationSeconds: Int = 0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
    @SerialName("structure") val structure: String = WorkoutStructure.REGULAR.name,
    @SerialName("kind") val kind: String = WorkoutKind.STRENGTH.name,
)

fun WorkoutEntity.toDto() = WorkoutDto(
    id, routineId, title, notes, status.name, startedAt, endedAt, durationSeconds,
    createdAt, updatedAt, structure.name, kind.name,
)

fun WorkoutDto.toEntity() = WorkoutEntity(
    id = id, routineId = routineId, title = title, notes = notes,
    status = WorkoutStatus.valueOf(status), startedAt = startedAt, endedAt = endedAt,
    durationSeconds = durationSeconds, createdAt = createdAt, updatedAt = updatedAt,
    structure = WorkoutStructure.valueOf(structure), kind = WorkoutKind.valueOf(kind),
)

@Serializable
data class WorkoutExerciseDto(
    @SerialName("id") val id: String = "",
    @SerialName("workout_id") val workoutId: String = "",
    @SerialName("exercise_id") val exerciseId: String = "",
    @SerialName("order_index") val orderIndex: Int = 0,
    @SerialName("superset_group") val supersetGroup: Int? = null,
    @SerialName("rest_timer_seconds") val restTimerSeconds: Int? = null,
    @SerialName("notes") val notes: String? = null,
)

fun WorkoutExerciseEntity.toDto() =
    WorkoutExerciseDto(id, workoutId, exerciseId, orderIndex, supersetGroup, restTimerSeconds, notes)

fun WorkoutExerciseDto.toEntity() =
    WorkoutExerciseEntity(id, workoutId, exerciseId, orderIndex, supersetGroup, restTimerSeconds, notes)

@Serializable
data class WorkoutSetDto(
    @SerialName("id") val id: String = "",
    @SerialName("workout_exercise_id") val workoutExerciseId: String = "",
    @SerialName("order_index") val orderIndex: Int = 0,
    @SerialName("set_type") val setType: String = SetType.NORMAL.name,
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("reps") val reps: Int? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("distance_meters") val distanceMeters: Double? = null,
    @SerialName("rpe") val rpe: Double? = null,
    @SerialName("custom_metric") val customMetric: Double? = null,
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("completed_at") val completedAt: Long? = null,
)

fun WorkoutSetEntity.toDto() = WorkoutSetDto(
    id, workoutExerciseId, orderIndex, setType.name, weightKg, reps, durationSeconds,
    distanceMeters, rpe, customMetric, isCompleted, completedAt,
)

fun WorkoutSetDto.toEntity() = WorkoutSetEntity(
    id = id, workoutExerciseId = workoutExerciseId, orderIndex = orderIndex,
    setType = SetType.valueOf(setType), weightKg = weightKg, reps = reps,
    durationSeconds = durationSeconds, distanceMeters = distanceMeters, rpe = rpe,
    customMetric = customMetric, isCompleted = isCompleted, completedAt = completedAt,
)

@Serializable
data class ActivityTrackDto(
    @SerialName("id") val id: String = "",
    @SerialName("workout_set_id") val workoutSetId: String = "",
    @SerialName("route_polyline") val routePolyline: String? = null,
    @SerialName("point_count") val pointCount: Int = 0,
    @SerialName("avg_accuracy_m") val avgAccuracyM: Double? = null,
    /** Added 2026-09-26 (database v10). Absent from older backups, which restore with null. */
    @SerialName("route_times") val routeTimes: String? = null,
)

fun ActivityTrackEntity.toDto() = ActivityTrackDto(id, workoutSetId, routePolyline, pointCount, avgAccuracyM, routeTimes)
fun ActivityTrackDto.toEntity() = ActivityTrackEntity(id, workoutSetId, routePolyline, pointCount, avgAccuracyM, routeTimes)

@Serializable
data class WorkoutHeartRateSampleDto(
    @SerialName("id") val id: String = "",
    @SerialName("workout_id") val workoutId: String = "",
    @SerialName("recorded_at") val recordedAt: Long = 0,
    @SerialName("bpm") val bpm: Long = 0,
)

fun WorkoutHeartRateSampleEntity.toDto() = WorkoutHeartRateSampleDto(id, workoutId, recordedAt, bpm)
fun WorkoutHeartRateSampleDto.toEntity() = WorkoutHeartRateSampleEntity(id, workoutId, recordedAt, bpm)

@Serializable
data class BodyMeasurementDto(
    @SerialName("date") val date: String = "",
    @SerialName("weight_kg") val weightKg: Double? = null,
    @SerialName("lean_mass_kg") val leanMassKg: Double? = null,
    @SerialName("fat_percent") val fatPercent: Double? = null,
    @SerialName("neck_cm") val neckCm: Double? = null,
    @SerialName("shoulder_cm") val shoulderCm: Double? = null,
    @SerialName("chest_cm") val chestCm: Double? = null,
    @SerialName("left_bicep_cm") val leftBicepCm: Double? = null,
    @SerialName("right_bicep_cm") val rightBicepCm: Double? = null,
    @SerialName("left_forearm_cm") val leftForearmCm: Double? = null,
    @SerialName("right_forearm_cm") val rightForearmCm: Double? = null,
    @SerialName("abdomen_cm") val abdomenCm: Double? = null,
    @SerialName("waist_cm") val waistCm: Double? = null,
    @SerialName("hips_cm") val hipsCm: Double? = null,
    @SerialName("left_thigh_cm") val leftThighCm: Double? = null,
    @SerialName("right_thigh_cm") val rightThighCm: Double? = null,
    @SerialName("left_calf_cm") val leftCalfCm: Double? = null,
    @SerialName("right_calf_cm") val rightCalfCm: Double? = null,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

fun BodyMeasurementEntity.toDto() = BodyMeasurementDto(
    date, weightKg, leanMassKg, fatPercent, neckCm, shoulderCm, chestCm, leftBicepCm,
    rightBicepCm, leftForearmCm, rightForearmCm, abdomenCm, waistCm, hipsCm, leftThighCm,
    rightThighCm, leftCalfCm, rightCalfCm, updatedAt,
)

fun BodyMeasurementDto.toEntity() = BodyMeasurementEntity(
    date, weightKg, leanMassKg, fatPercent, neckCm, shoulderCm, chestCm, leftBicepCm,
    rightBicepCm, leftForearmCm, rightForearmCm, abdomenCm, waistCm, hipsCm, leftThighCm,
    rightThighCm, leftCalfCm, rightCalfCm, updatedAt,
)

@Serializable
data class ProgressPhotoDto(
    @SerialName("id") val id: String = "",
    @SerialName("date") val date: String = "",
    /** Relative to filesDir, which is what makes the media files portable between installs. */
    @SerialName("file_path") val filePath: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
)

fun ProgressPhotoEntity.toDto() = ProgressPhotoDto(id, date, filePath, createdAt)
fun ProgressPhotoDto.toEntity() = ProgressPhotoEntity(id, date, filePath, createdAt)

@Serializable
data class GoalDefinitionDto(
    @SerialName("id") val id: String = "",
    @SerialName("metric") val metric: String = GoalMetric.WORKOUT_COUNT.name,
    @SerialName("period") val period: String = GoalPeriod.WEEKLY.name,
    @SerialName("target_value") val targetValue: Double = 0.0,
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

fun GoalDefinitionEntity.toDto() =
    GoalDefinitionDto(id, metric.name, period.name, targetValue, createdAt, updatedAt)

fun GoalDefinitionDto.toEntity() = GoalDefinitionEntity(
    id = id, metric = GoalMetric.valueOf(metric), period = GoalPeriod.valueOf(period),
    targetValue = targetValue, createdAt = createdAt, updatedAt = updatedAt,
)

@Serializable
data class DailyWellnessTotalDto(
    @SerialName("date") val date: String = "",
    @SerialName("steps") val steps: Long = 0,
    @SerialName("calories_burned") val caloriesBurned: Double? = null,
    @SerialName("updated_at") val updatedAt: Long = 0,
)

fun DailyWellnessTotalEntity.toDto() = DailyWellnessTotalDto(date, steps, caloriesBurned, updatedAt)
fun DailyWellnessTotalDto.toEntity() = DailyWellnessTotalEntity(date, steps, caloriesBurned, updatedAt)
