package com.enil.logez.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.MeasurementsTrackingMode
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WarmupStep
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.defaultPlateEquipment
import com.enil.logez.core.domain.model.defaultWarmupMethod
import com.enil.logez.core.domain.repository.SettingsRepository
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** PHASE2_PLAN.md §5.2 Settings tree — every key/default exactly as specified there. */
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    private object Keys {
        val WEIGHT_UNIT = stringPreferencesKey("weightUnit")
        val DISTANCE_UNIT = stringPreferencesKey("distanceUnit")
        val LENGTH_UNIT = stringPreferencesKey("lengthUnit")
        val MUSCLE_DIAGRAM_VARIANT = stringPreferencesKey("muscleDiagramVariant")
        val FIRST_DAY_OF_WEEK = stringPreferencesKey("firstDayOfWeek")
        val PER_EXERCISE_UNIT_OVERRIDES = stringPreferencesKey("perExerciseUnitOverrides")

        val DEFAULT_REST_TIMER_SECONDS = intPreferencesKey("defaultRestTimerSeconds")
        val TIMER_SOUND = intPreferencesKey("timerSound")
        // Owner, 2026-09-03: volumes became continuous sliders (Float, 0f-1f), not the old
        // Off/Quiet/Normal/Loud enum. New key NAMES on purpose — reusing "timerVolume" etc. under a
        // floatPreferencesKey would try to read a previously-stored String through a Float-typed
        // key and crash (Preferences.Key lookup is name-keyed with the type erased). The old string
        // keys stay declared, read-only, purely so LEGACY_VOLUME below can backfill a returning
        // user's prior choice on first read.
        val TIMER_VOLUME = floatPreferencesKey("timerVolumeF")
        val SET_COMPLETE_VOLUME = floatPreferencesKey("setCompleteVolumeF")
        val PR_VOLUME = floatPreferencesKey("prVolumeF")
        val TIMER_VOLUME_LEGACY = stringPreferencesKey("timerVolume")
        val SET_COMPLETE_VOLUME_LEGACY = stringPreferencesKey("setCompleteVolume")
        val PR_VOLUME_LEGACY = stringPreferencesKey("prVolume")
        val PREVIOUS_VALUES_MODE = stringPreferencesKey("previousValuesMode")
        val WARMUP_CALCULATOR_ENABLED = booleanPreferencesKey("warmupCalculatorEnabled")
        val WARMUP_METHOD = stringPreferencesKey("warmupMethod")
        val INCLUDE_WARMUPS_IN_STATS = booleanPreferencesKey("includeWarmupsInStats")
        val KEEP_AWAKE = booleanPreferencesKey("keepAwake")
        val PLATE_CALCULATOR_ENABLED = booleanPreferencesKey("plateCalculatorEnabled")
        val PLATE_EQUIPMENT = stringPreferencesKey("plateEquipment")
        val RPE_TRACKING_ENABLED = booleanPreferencesKey("rpeTrackingEnabled")
        val SMART_SUPERSET_SCROLLING = booleanPreferencesKey("smartSupersetScrolling")
        val INLINE_TIMER_ENABLED = booleanPreferencesKey("inlineTimerEnabled")
        val LIVE_PR_NOTIFICATION_ENABLED = booleanPreferencesKey("livePrNotificationEnabled")
        val MAX_HEART_RATE_BPM = intPreferencesKey("maxHeartRateBpm")
        val SHOW_HEATMAP = booleanPreferencesKey("showHeatmap")
        val SHOW_GOALS = booleanPreferencesKey("showGoals")
        val MEASUREMENTS_TRACKING_MODE = stringPreferencesKey("measurementsTrackingMode")
        val WEEKLY_ACTIVE_DAY_TARGET = intPreferencesKey("weeklyActiveDayTarget")
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** The old OFF/LOW/NORMAL/HIGH enum's float mapping, reimplemented here only to backfill a
     * returning user's prior volume choice the first time it's read under the new float key. */
    private fun legacyVolumeLevelToFloat(name: String?): Float? = when (name) {
        "OFF" -> 0f
        "LOW" -> 0.33f
        "NORMAL" -> 0.66f
        "HIGH" -> 1f
        else -> null
    }

    override val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        val defaults = UserSettings()
        UserSettings(
            weightUnit = prefs[Keys.WEIGHT_UNIT]?.toEnumOrNull<WeightUnit>() ?: defaults.weightUnit,
            distanceUnit = prefs[Keys.DISTANCE_UNIT]?.toEnumOrNull<DistanceUnit>() ?: defaults.distanceUnit,
            lengthUnit = prefs[Keys.LENGTH_UNIT]?.toEnumOrNull<LengthUnit>() ?: defaults.lengthUnit,
            muscleDiagramVariant = prefs[Keys.MUSCLE_DIAGRAM_VARIANT]?.toEnumOrNull<MuscleDiagramVariant>() ?: defaults.muscleDiagramVariant,
            firstDayOfWeek = prefs[Keys.FIRST_DAY_OF_WEEK]?.toEnumOrNull<DayOfWeek>() ?: defaults.firstDayOfWeek,
            perExerciseUnitOverrides = prefs[Keys.PER_EXERCISE_UNIT_OVERRIDES]?.let {
                runCatching { json.decodeFromString<Map<String, WeightUnit>>(it) }.getOrNull()
            } ?: defaults.perExerciseUnitOverrides,
            defaultRestTimerSeconds = prefs[Keys.DEFAULT_REST_TIMER_SECONDS] ?: defaults.defaultRestTimerSeconds,
            timerSound = prefs[Keys.TIMER_SOUND] ?: defaults.timerSound,
            timerVolume = prefs[Keys.TIMER_VOLUME] ?: legacyVolumeLevelToFloat(prefs[Keys.TIMER_VOLUME_LEGACY]) ?: defaults.timerVolume,
            setCompleteVolume = prefs[Keys.SET_COMPLETE_VOLUME] ?: legacyVolumeLevelToFloat(prefs[Keys.SET_COMPLETE_VOLUME_LEGACY]) ?: defaults.setCompleteVolume,
            prVolume = prefs[Keys.PR_VOLUME] ?: legacyVolumeLevelToFloat(prefs[Keys.PR_VOLUME_LEGACY]) ?: defaults.prVolume,
            previousValuesMode = prefs[Keys.PREVIOUS_VALUES_MODE]?.toEnumOrNull<PreviousValuesMode>() ?: defaults.previousValuesMode,
            warmupCalculatorEnabled = prefs[Keys.WARMUP_CALCULATOR_ENABLED] ?: defaults.warmupCalculatorEnabled,
            warmupMethod = prefs[Keys.WARMUP_METHOD]?.let { runCatching { json.decodeFromString<List<WarmupStep>>(it) }.getOrNull() } ?: defaultWarmupMethod,
            includeWarmupsInStats = prefs[Keys.INCLUDE_WARMUPS_IN_STATS] ?: defaults.includeWarmupsInStats,
            keepAwake = prefs[Keys.KEEP_AWAKE] ?: defaults.keepAwake,
            plateCalculatorEnabled = prefs[Keys.PLATE_CALCULATOR_ENABLED] ?: defaults.plateCalculatorEnabled,
            plateEquipment = prefs[Keys.PLATE_EQUIPMENT]?.let { runCatching { json.decodeFromString<PlateEquipment>(it) }.getOrNull() } ?: defaultPlateEquipment,
            rpeTrackingEnabled = prefs[Keys.RPE_TRACKING_ENABLED] ?: defaults.rpeTrackingEnabled,
            smartSupersetScrolling = prefs[Keys.SMART_SUPERSET_SCROLLING] ?: defaults.smartSupersetScrolling,
            inlineTimerEnabled = prefs[Keys.INLINE_TIMER_ENABLED] ?: defaults.inlineTimerEnabled,
            livePrNotificationEnabled = prefs[Keys.LIVE_PR_NOTIFICATION_ENABLED] ?: defaults.livePrNotificationEnabled,
            maxHeartRateBpm = prefs[Keys.MAX_HEART_RATE_BPM] ?: defaults.maxHeartRateBpm,
            showHeatmap = prefs[Keys.SHOW_HEATMAP] ?: defaults.showHeatmap,
            showGoals = prefs[Keys.SHOW_GOALS] ?: defaults.showGoals,
            measurementsTrackingMode = prefs[Keys.MEASUREMENTS_TRACKING_MODE]?.toEnumOrNull<MeasurementsTrackingMode>() ?: defaults.measurementsTrackingMode,
            weeklyActiveDayTarget = prefs[Keys.WEEKLY_ACTIVE_DAY_TARGET] ?: defaults.weeklyActiveDayTarget,
        )
    }

    override suspend fun setWeightUnit(value: WeightUnit) = edit { it[Keys.WEIGHT_UNIT] = value.name }
    override suspend fun setDistanceUnit(value: DistanceUnit) = edit { it[Keys.DISTANCE_UNIT] = value.name }
    override suspend fun setLengthUnit(value: LengthUnit) = edit { it[Keys.LENGTH_UNIT] = value.name }
    override suspend fun setMuscleDiagramVariant(value: MuscleDiagramVariant) = edit { it[Keys.MUSCLE_DIAGRAM_VARIANT] = value.name }
    override suspend fun setFirstDayOfWeek(value: DayOfWeek) = edit { it[Keys.FIRST_DAY_OF_WEEK] = value.name }

    override suspend fun setPerExerciseUnitOverride(exerciseId: String, unit: WeightUnit?) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PER_EXERCISE_UNIT_OVERRIDES]?.let { json.decodeFromString<Map<String, WeightUnit>>(it) } ?: emptyMap()
            val updated = if (unit == null) current - exerciseId else current + (exerciseId to unit)
            prefs[Keys.PER_EXERCISE_UNIT_OVERRIDES] = json.encodeToString(updated)
        }
    }

    override suspend fun setDefaultRestTimerSeconds(value: Int) = edit { it[Keys.DEFAULT_REST_TIMER_SECONDS] = value }
    override suspend fun setTimerSound(value: Int) = edit { it[Keys.TIMER_SOUND] = value }
    override suspend fun setTimerVolume(value: Float) = edit { it[Keys.TIMER_VOLUME] = value }
    override suspend fun setSetCompleteVolume(value: Float) = edit { it[Keys.SET_COMPLETE_VOLUME] = value }
    override suspend fun setPrVolume(value: Float) = edit { it[Keys.PR_VOLUME] = value }
    override suspend fun setPreviousValuesMode(value: PreviousValuesMode) = edit { it[Keys.PREVIOUS_VALUES_MODE] = value.name }
    override suspend fun setWarmupCalculatorEnabled(value: Boolean) = edit { it[Keys.WARMUP_CALCULATOR_ENABLED] = value }
    override suspend fun setWarmupMethod(value: List<WarmupStep>) = edit { it[Keys.WARMUP_METHOD] = json.encodeToString(value) }
    override suspend fun setIncludeWarmupsInStats(value: Boolean) = edit { it[Keys.INCLUDE_WARMUPS_IN_STATS] = value }
    override suspend fun setKeepAwake(value: Boolean) = edit { it[Keys.KEEP_AWAKE] = value }
    override suspend fun setPlateCalculatorEnabled(value: Boolean) = edit { it[Keys.PLATE_CALCULATOR_ENABLED] = value }
    override suspend fun setPlateEquipment(value: PlateEquipment) = edit { it[Keys.PLATE_EQUIPMENT] = json.encodeToString(value) }
    override suspend fun setRpeTrackingEnabled(value: Boolean) = edit { it[Keys.RPE_TRACKING_ENABLED] = value }
    override suspend fun setSmartSupersetScrolling(value: Boolean) = edit { it[Keys.SMART_SUPERSET_SCROLLING] = value }
    override suspend fun setInlineTimerEnabled(value: Boolean) = edit { it[Keys.INLINE_TIMER_ENABLED] = value }
    override suspend fun setLivePrNotificationEnabled(value: Boolean) = edit { it[Keys.LIVE_PR_NOTIFICATION_ENABLED] = value }
    override suspend fun setMaxHeartRateBpm(value: Int?) = edit { if (value != null) it[Keys.MAX_HEART_RATE_BPM] = value else it.remove(Keys.MAX_HEART_RATE_BPM) }
    override suspend fun setShowHeatmap(value: Boolean) = edit { it[Keys.SHOW_HEATMAP] = value }
    override suspend fun setShowGoals(value: Boolean) = edit { it[Keys.SHOW_GOALS] = value }
    override suspend fun setMeasurementsTrackingMode(value: MeasurementsTrackingMode) = edit { it[Keys.MEASUREMENTS_TRACKING_MODE] = value.name }
    override suspend fun setWeeklyActiveDayTarget(value: Int) = edit { it[Keys.WEEKLY_ACTIVE_DAY_TARGET] = value }

    override suspend fun replaceAll(settings: UserSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.WEIGHT_UNIT] = settings.weightUnit.name
            prefs[Keys.DISTANCE_UNIT] = settings.distanceUnit.name
            prefs[Keys.LENGTH_UNIT] = settings.lengthUnit.name
            prefs[Keys.MUSCLE_DIAGRAM_VARIANT] = settings.muscleDiagramVariant.name
            prefs[Keys.FIRST_DAY_OF_WEEK] = settings.firstDayOfWeek.name
            prefs[Keys.PER_EXERCISE_UNIT_OVERRIDES] =
                json.encodeToString(settings.perExerciseUnitOverrides.mapValues { it.value.name })
            prefs[Keys.DEFAULT_REST_TIMER_SECONDS] = settings.defaultRestTimerSeconds
            prefs[Keys.TIMER_SOUND] = settings.timerSound
            prefs[Keys.TIMER_VOLUME] = settings.timerVolume
            prefs[Keys.SET_COMPLETE_VOLUME] = settings.setCompleteVolume
            prefs[Keys.PR_VOLUME] = settings.prVolume
            prefs[Keys.PREVIOUS_VALUES_MODE] = settings.previousValuesMode.name
            prefs[Keys.WARMUP_CALCULATOR_ENABLED] = settings.warmupCalculatorEnabled
            prefs[Keys.WARMUP_METHOD] = json.encodeToString(settings.warmupMethod)
            prefs[Keys.INCLUDE_WARMUPS_IN_STATS] = settings.includeWarmupsInStats
            prefs[Keys.KEEP_AWAKE] = settings.keepAwake
            prefs[Keys.PLATE_CALCULATOR_ENABLED] = settings.plateCalculatorEnabled
            prefs[Keys.PLATE_EQUIPMENT] = json.encodeToString(settings.plateEquipment)
            prefs[Keys.RPE_TRACKING_ENABLED] = settings.rpeTrackingEnabled
            prefs[Keys.SMART_SUPERSET_SCROLLING] = settings.smartSupersetScrolling
            prefs[Keys.INLINE_TIMER_ENABLED] = settings.inlineTimerEnabled
            prefs[Keys.LIVE_PR_NOTIFICATION_ENABLED] = settings.livePrNotificationEnabled
            prefs[Keys.SHOW_HEATMAP] = settings.showHeatmap
            prefs[Keys.SHOW_GOALS] = settings.showGoals
            prefs[Keys.MEASUREMENTS_TRACKING_MODE] = settings.measurementsTrackingMode.name
            prefs[Keys.WEEKLY_ACTIVE_DAY_TARGET] = settings.weeklyActiveDayTarget

            // Removed, not skipped: dataStore.edit merges, so restoring a backup with no max heart
            // rate over a device that has one would otherwise silently keep the device's value.
            if (settings.maxHeartRateBpm != null) {
                prefs[Keys.MAX_HEART_RATE_BPM] = settings.maxHeartRateBpm
            } else {
                prefs.remove(Keys.MAX_HEART_RATE_BPM)
            }

            // The float keys above supersede these. Left behind, the backfill that reads them
            // could resurrect a stale volume if a float key were ever cleared.
            prefs.remove(Keys.TIMER_VOLUME_LEGACY)
            prefs.remove(Keys.SET_COMPLETE_VOLUME_LEGACY)
            prefs.remove(Keys.PR_VOLUME_LEGACY)

            // lastAppliedSeedVersion is deliberately untouched here -- it belongs to the installed
            // app's seed asset, not to the user's data. The restore resets it separately.
        }
    }

    /**
     * A stored name the current enum no longer has decodes as null, so the caller falls back to
     * the default. Throwing here would escape `dataStore.data.map` and cancel every collector of
     * the settings Flow -- effectively the whole app -- with no way back short of clearing app
     * data. A renamed constant is exactly the kind of change a contributor makes without knowing
     * a data migration is required.
     */
    private inline fun <reified E : Enum<E>> String.toEnumOrNull(): E? =
        runCatching { enumValueOf<E>(this) }.getOrNull()

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        dataStore.edit(block)
    }
}
