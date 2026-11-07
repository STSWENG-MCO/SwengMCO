package com.enil.logez.core.data

import androidx.room.TypeConverter
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.GoalMetric
import com.enil.logez.core.domain.model.GoalPeriod
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.MuscleHead
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Explicit enum-as-string and JSON-list converters (PHASE2_PLAN.md §10.2 DAO-test requirement:
 * "TypeConverters (enum ↔ string, secondaryMuscleGroups JSON-list, LocalDate)"). Written out
 * explicitly rather than relying on Room's native enum support, so the wire format (`.name`,
 * never `.ordinal`) is unambiguous and independent of enum declaration order.
 *
 * Where an enum has an honest "unknown" member (OTHER / NORMAL / REGULAR / STRENGTH), a stored
 * name the enum no longer knows decodes to it instead of throwing, and the two list columns drop
 * such entries -- so renaming a constant degrades a row rather than crashing every read of it.
 * The remaining single-value enums (exercise type, PR type, status, goal metric/period) have no
 * safe stand-in and still throw: a wrong value there is real corruption, and any rename of those
 * needs a data migration.
 */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromSetType(value: SetType): String = value.name

    @TypeConverter
    fun toSetType(value: String): SetType = runCatching { SetType.valueOf(value) }.getOrDefault(SetType.NORMAL)

    @TypeConverter
    fun fromExerciseType(value: ExerciseType): String = value.name

    @TypeConverter
    fun toExerciseType(value: String): ExerciseType = ExerciseType.valueOf(value)

    @TypeConverter
    fun fromMuscleGroup(value: MuscleGroup): String = value.name

    @TypeConverter
    fun toMuscleGroup(value: String): MuscleGroup = runCatching { MuscleGroup.valueOf(value) }.getOrDefault(MuscleGroup.OTHER)

    @TypeConverter
    fun fromEquipment(value: Equipment): String = value.name

    @TypeConverter
    fun toEquipment(value: String): Equipment = runCatching { Equipment.valueOf(value) }.getOrDefault(Equipment.OTHER)

    @TypeConverter
    fun fromPrType(value: PrType): String = value.name

    @TypeConverter
    fun toPrType(value: String): PrType = PrType.valueOf(value)

    @TypeConverter
    fun fromWorkoutStatus(value: WorkoutStatus): String = value.name

    @TypeConverter
    fun toWorkoutStatus(value: String): WorkoutStatus = WorkoutStatus.valueOf(value)

    @TypeConverter
    fun fromWorkoutStructure(value: WorkoutStructure): String = value.name

    @TypeConverter
    fun toWorkoutStructure(value: String): WorkoutStructure = runCatching { WorkoutStructure.valueOf(value) }.getOrDefault(WorkoutStructure.REGULAR)

    @TypeConverter
    fun fromWorkoutKind(value: WorkoutKind): String = value.name

    @TypeConverter
    fun toWorkoutKind(value: String): WorkoutKind = runCatching { WorkoutKind.valueOf(value) }.getOrDefault(WorkoutKind.STRENGTH)

    @TypeConverter
    fun fromGoalMetric(value: GoalMetric): String = value.name

    @TypeConverter
    fun toGoalMetric(value: String): GoalMetric = GoalMetric.valueOf(value)

    @TypeConverter
    fun fromGoalPeriod(value: GoalPeriod): String = value.name

    @TypeConverter
    fun toGoalPeriod(value: String): GoalPeriod = GoalPeriod.valueOf(value)

    @TypeConverter
    fun fromMuscleGroupList(value: List<MuscleGroup>): String =
        json.encodeToString(value.map { it.name })

    @TypeConverter
    fun toMuscleGroupList(value: String): List<MuscleGroup> =
        if (value.isBlank()) emptyList()
        else json.decodeFromString<List<String>>(value).mapNotNull { name -> runCatching { MuscleGroup.valueOf(name) }.getOrNull() }

    /** A checklist, not a single pick (M8e revision — Owner feedback) -- empty for every exercise that doesn't specify any. */
    @TypeConverter
    fun fromMuscleHeadList(value: List<MuscleHead>): String =
        json.encodeToString(value.map { it.name })

    @TypeConverter
    fun toMuscleHeadList(value: String): List<MuscleHead> =
        if (value.isBlank()) emptyList()
        else json.decodeFromString<List<String>>(value).mapNotNull { name -> runCatching { MuscleHead.valueOf(name) }.getOrNull() }

    /**
     * Superseded by [fromMuscleHeadList]/[toMuscleHeadList] (M8e revision), kept only because
     * [com.enil.logez.core.data.entity.ExerciseEntity.deprecatedPrimaryMuscleHead] must stay
     * declared in the entity for Room's schema validation -- see that field's KDoc. The column
     * isn't inert: pre-M8e rows can still carry a real single-pick value, so [toMuscleHead] gets
     * the same guarded read as [toMuscleHeadList] -- a renamed [MuscleHead] constant would
     * otherwise throw out of `SELECT *` on every exercise-library read, not just degrade a field.
     */
    @TypeConverter
    fun fromMuscleHead(value: MuscleHead?): String? = value?.name

    @TypeConverter
    fun toMuscleHead(value: String?): MuscleHead? = value?.let { runCatching { MuscleHead.valueOf(it) }.getOrNull() }
}
