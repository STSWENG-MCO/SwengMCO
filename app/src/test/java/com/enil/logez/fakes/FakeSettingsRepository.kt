package com.enil.logez.fakes

import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.MeasurementsTrackingMode
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WarmupStep
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.SettingsRepository
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2). */
class FakeSettingsRepository(initial: UserSettings = UserSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings = state

    override suspend fun setWeightUnit(value: WeightUnit) { state.value = state.value.copy(weightUnit = value) }
    override suspend fun setDistanceUnit(value: DistanceUnit) { state.value = state.value.copy(distanceUnit = value) }
    override suspend fun setLengthUnit(value: LengthUnit) { state.value = state.value.copy(lengthUnit = value) }
    override suspend fun setMuscleDiagramVariant(value: MuscleDiagramVariant) { state.value = state.value.copy(muscleDiagramVariant = value) }
    override suspend fun setFirstDayOfWeek(value: DayOfWeek) { state.value = state.value.copy(firstDayOfWeek = value) }
    override suspend fun setPerExerciseUnitOverride(exerciseId: String, unit: WeightUnit?) {
        state.value = state.value.copy(
            perExerciseUnitOverrides = if (unit == null) state.value.perExerciseUnitOverrides - exerciseId else state.value.perExerciseUnitOverrides + (exerciseId to unit),
        )
    }
    override suspend fun setDefaultRestTimerSeconds(value: Int) { state.value = state.value.copy(defaultRestTimerSeconds = value) }
    override suspend fun setTimerSound(value: Int) { state.value = state.value.copy(timerSound = value) }
    override suspend fun setTimerVolume(value: Float) { state.value = state.value.copy(timerVolume = value) }
    override suspend fun setSetCompleteVolume(value: Float) { state.value = state.value.copy(setCompleteVolume = value) }
    override suspend fun setPrVolume(value: Float) { state.value = state.value.copy(prVolume = value) }
    override suspend fun setPreviousValuesMode(value: PreviousValuesMode) { state.value = state.value.copy(previousValuesMode = value) }
    override suspend fun setWarmupCalculatorEnabled(value: Boolean) { state.value = state.value.copy(warmupCalculatorEnabled = value) }
    override suspend fun setWarmupMethod(value: List<WarmupStep>) { state.value = state.value.copy(warmupMethod = value) }
    override suspend fun setIncludeWarmupsInStats(value: Boolean) { state.value = state.value.copy(includeWarmupsInStats = value) }
    override suspend fun setKeepAwake(value: Boolean) { state.value = state.value.copy(keepAwake = value) }
    override suspend fun setPlateCalculatorEnabled(value: Boolean) { state.value = state.value.copy(plateCalculatorEnabled = value) }
    override suspend fun setPlateEquipment(value: PlateEquipment) { state.value = state.value.copy(plateEquipment = value) }
    override suspend fun setRpeTrackingEnabled(value: Boolean) { state.value = state.value.copy(rpeTrackingEnabled = value) }
    override suspend fun setSmartSupersetScrolling(value: Boolean) { state.value = state.value.copy(smartSupersetScrolling = value) }
    override suspend fun setInlineTimerEnabled(value: Boolean) { state.value = state.value.copy(inlineTimerEnabled = value) }
    override suspend fun setLivePrNotificationEnabled(value: Boolean) { state.value = state.value.copy(livePrNotificationEnabled = value) }
    override suspend fun setMaxHeartRateBpm(value: Int?) { state.value = state.value.copy(maxHeartRateBpm = value) }
    override suspend fun setShowHeatmap(value: Boolean) { state.value = state.value.copy(showHeatmap = value) }
    override suspend fun setShowGoals(value: Boolean) { state.value = state.value.copy(showGoals = value) }
    override suspend fun setMeasurementsTrackingMode(value: MeasurementsTrackingMode) { state.value = state.value.copy(measurementsTrackingMode = value) }
    override suspend fun setWeeklyActiveDayTarget(value: Int) { state.value = state.value.copy(weeklyActiveDayTarget = value) }
    override suspend fun replaceAll(settings: UserSettings) { state.value = settings }
}
