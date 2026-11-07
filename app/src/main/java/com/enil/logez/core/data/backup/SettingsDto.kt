package com.enil.logez.core.data.backup

import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.MeasurementsTrackingMode
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WarmupStep
import com.enil.logez.core.domain.model.WeightUnit
import java.time.DayOfWeek
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The user's preferences, as carried in a backup.
 *
 * `PlateEquipment` and `WarmupStep` are embedded directly rather than restated, because they are
 * already serializable and are already exactly what the settings store writes to disk — so the
 * backup's shape for them is the shape they already have.
 *
 * `firstDayOfWeek` is a `java.time.DayOfWeek`, which has no serializer, so it travels as its name.
 */
@Serializable
data class SettingsDto(
    @SerialName("weight_unit") val weightUnit: String = WeightUnit.KG.name,
    @SerialName("distance_unit") val distanceUnit: String = DistanceUnit.KM.name,
    @SerialName("length_unit") val lengthUnit: String = LengthUnit.CM.name,
    @SerialName("muscle_diagram_variant") val muscleDiagramVariant: String = MuscleDiagramVariant.MALE.name,
    @SerialName("first_day_of_week") val firstDayOfWeek: String = DayOfWeek.MONDAY.name,
    @SerialName("per_exercise_unit_overrides") val perExerciseUnitOverrides: Map<String, String> = emptyMap(),
    @SerialName("default_rest_timer_seconds") val defaultRestTimerSeconds: Int = 90,
    @SerialName("timer_sound") val timerSound: Int = 1,
    @SerialName("timer_volume") val timerVolume: Float = 0.66f,
    @SerialName("set_complete_volume") val setCompleteVolume: Float = 0.66f,
    @SerialName("pr_volume") val prVolume: Float = 0.66f,
    @SerialName("previous_values_mode") val previousValuesMode: String = PreviousValuesMode.ANY_WORKOUT.name,
    @SerialName("warmup_calculator_enabled") val warmupCalculatorEnabled: Boolean = true,
    @SerialName("warmup_method") val warmupMethod: List<WarmupStep> = emptyList(),
    @SerialName("include_warmups_in_stats") val includeWarmupsInStats: Boolean = false,
    @SerialName("keep_awake") val keepAwake: Boolean = true,
    @SerialName("plate_calculator_enabled") val plateCalculatorEnabled: Boolean = true,
    @SerialName("plate_equipment") val plateEquipment: PlateEquipment = PlateEquipment(),
    @SerialName("rpe_tracking_enabled") val rpeTrackingEnabled: Boolean = false,
    @SerialName("smart_superset_scrolling") val smartSupersetScrolling: Boolean = true,
    @SerialName("inline_timer_enabled") val inlineTimerEnabled: Boolean = true,
    @SerialName("live_pr_notification_enabled") val livePrNotificationEnabled: Boolean = true,
    @SerialName("max_heart_rate_bpm") val maxHeartRateBpm: Int? = null,
    @SerialName("show_heatmap") val showHeatmap: Boolean = true,
    @SerialName("show_goals") val showGoals: Boolean = true,
    @SerialName("measurements_tracking_mode") val measurementsTrackingMode: String = MeasurementsTrackingMode.COMPLETE.name,
    @SerialName("weekly_active_day_target") val weeklyActiveDayTarget: Int = 4,
)

fun UserSettings.toDto() = SettingsDto(
    weightUnit = weightUnit.name,
    distanceUnit = distanceUnit.name,
    lengthUnit = lengthUnit.name,
    muscleDiagramVariant = muscleDiagramVariant.name,
    firstDayOfWeek = firstDayOfWeek.name,
    perExerciseUnitOverrides = perExerciseUnitOverrides.mapValues { it.value.name },
    defaultRestTimerSeconds = defaultRestTimerSeconds,
    timerSound = timerSound,
    timerVolume = timerVolume,
    setCompleteVolume = setCompleteVolume,
    prVolume = prVolume,
    previousValuesMode = previousValuesMode.name,
    warmupCalculatorEnabled = warmupCalculatorEnabled,
    warmupMethod = warmupMethod,
    includeWarmupsInStats = includeWarmupsInStats,
    keepAwake = keepAwake,
    plateCalculatorEnabled = plateCalculatorEnabled,
    plateEquipment = plateEquipment,
    rpeTrackingEnabled = rpeTrackingEnabled,
    smartSupersetScrolling = smartSupersetScrolling,
    inlineTimerEnabled = inlineTimerEnabled,
    livePrNotificationEnabled = livePrNotificationEnabled,
    maxHeartRateBpm = maxHeartRateBpm,
    showHeatmap = showHeatmap,
    showGoals = showGoals,
    measurementsTrackingMode = measurementsTrackingMode.name,
    weeklyActiveDayTarget = weeklyActiveDayTarget,
)

fun SettingsDto.toUserSettings() = UserSettings(
    weightUnit = WeightUnit.valueOf(weightUnit),
    distanceUnit = DistanceUnit.valueOf(distanceUnit),
    lengthUnit = LengthUnit.valueOf(lengthUnit),
    muscleDiagramVariant = MuscleDiagramVariant.valueOf(muscleDiagramVariant),
    firstDayOfWeek = DayOfWeek.valueOf(firstDayOfWeek),
    perExerciseUnitOverrides = perExerciseUnitOverrides.mapValues { WeightUnit.valueOf(it.value) },
    defaultRestTimerSeconds = defaultRestTimerSeconds,
    timerSound = timerSound,
    timerVolume = timerVolume,
    setCompleteVolume = setCompleteVolume,
    prVolume = prVolume,
    previousValuesMode = PreviousValuesMode.valueOf(previousValuesMode),
    warmupCalculatorEnabled = warmupCalculatorEnabled,
    warmupMethod = warmupMethod,
    includeWarmupsInStats = includeWarmupsInStats,
    keepAwake = keepAwake,
    plateCalculatorEnabled = plateCalculatorEnabled,
    plateEquipment = plateEquipment,
    rpeTrackingEnabled = rpeTrackingEnabled,
    smartSupersetScrolling = smartSupersetScrolling,
    inlineTimerEnabled = inlineTimerEnabled,
    livePrNotificationEnabled = livePrNotificationEnabled,
    maxHeartRateBpm = maxHeartRateBpm,
    showHeatmap = showHeatmap,
    showGoals = showGoals,
    measurementsTrackingMode = MeasurementsTrackingMode.valueOf(measurementsTrackingMode),
    weeklyActiveDayTarget = weeklyActiveDayTarget,
)
