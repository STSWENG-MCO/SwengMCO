package com.enil.logez.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.withBarAdded
import com.enil.logez.core.domain.model.withBarRemoved
import com.enil.logez.core.domain.model.withPlateAdded
import com.enil.logez.core.domain.model.withPlateRemoved
import com.enil.logez.core.domain.model.WarmupStep
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.defaultWarmupMethod
import com.enil.logez.core.domain.WidgetRefresher
import com.enil.logez.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * M16 Settings screen — a thin write-through layer over [SettingsRepository]: the current
 * [UserSettings] as observable state plus one persist function per editable row. Every change
 * persists instantly (no Save button; back = done), so there is deliberately no business logic,
 * no draft state, and no validation here.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val widgetRefresher: WidgetRefresher,
) : ViewModel() {
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    // Preferences
    fun setWeightUnit(value: WeightUnit) = write { setWeightUnit(value) }
    fun setDistanceUnit(value: DistanceUnit) = write { setDistanceUnit(value) }
    fun setLengthUnit(value: LengthUnit) = write { setLengthUnit(value) }
    fun setMuscleDiagramVariant(value: MuscleDiagramVariant) = write { setMuscleDiagramVariant(value) }
    // Both change what the widget renders: the target is its denominator, and the first day of
    // week re-buckets the whole day grid and the streak with it.
    fun setWeeklyActiveDayTarget(value: Int) = writeThenRefreshWidget { setWeeklyActiveDayTarget(value) }
    fun setFirstDayOfWeek(value: DayOfWeek) = writeThenRefreshWidget { setFirstDayOfWeek(value) }

    // Workouts
    fun setDefaultRestTimerSeconds(value: Int) = write { setDefaultRestTimerSeconds(value) }
    fun setPreviousValuesMode(value: PreviousValuesMode) = write { setPreviousValuesMode(value) }
    fun setKeepAwake(value: Boolean) = write { setKeepAwake(value) }
    fun setSmartSupersetScrolling(value: Boolean) = write { setSmartSupersetScrolling(value) }
    fun setInlineTimerEnabled(value: Boolean) = write { setInlineTimerEnabled(value) }
    fun setLivePrNotificationEnabled(value: Boolean) = write { setLivePrNotificationEnabled(value) }
    fun setMaxHeartRateBpm(value: Int?) = write { setMaxHeartRateBpm(value) }
    fun setRpeTrackingEnabled(value: Boolean) = write { setRpeTrackingEnabled(value) }
    fun setIncludeWarmupsInStats(value: Boolean) = write { setIncludeWarmupsInStats(value) }
    fun setShowHeatmap(value: Boolean) = write { setShowHeatmap(value) }
    fun setShowGoals(value: Boolean) = write { setShowGoals(value) }

    // Calculators
    fun setPlateCalculatorEnabled(value: Boolean) = write { setPlateCalculatorEnabled(value) }
    fun setWarmupCalculatorEnabled(value: Boolean) = write { setWarmupCalculatorEnabled(value) }

    // M17 plate equipment — the rounding/dedupe/last-bar rules live on PlateEquipment itself
    // (pure, tested); each call persists the whole transformed value through setPlateEquipment.
    fun addBar(kg: Double) = persistPlateEquipment { it.withBarAdded(kg) }
    fun removeBar(kg: Double) = persistPlateEquipment { it.withBarRemoved(kg) }
    fun addPlate(kg: Double) = persistPlateEquipment { it.withPlateAdded(kg) }
    fun removePlate(kg: Double) = persistPlateEquipment { it.withPlateRemoved(kg) }

    private fun persistPlateEquipment(transform: (PlateEquipment) -> PlateEquipment) {
        // Read the CURRENT persisted equipment inside the coroutine, not the eager stateIn
        // snapshot: before DataStore's first emission that snapshot is UserSettings() defaults,
        // and transforming defaults here would silently overwrite a user's customized bars and
        // plates. first() suspends until a real emission, and sequential edits each re-read the
        // just-written value, so back-to-back taps compose instead of clobbering.
        viewModelScope.launch {
            val current = settingsRepository.settings.first().plateEquipment
            settingsRepository.setPlateEquipment(transform(current))
        }
    }

    // M18 Warm-up Sets editor — each edit transforms the whole persisted ladder and writes it back
    // through setWarmupMethod. Indices refer to the CURRENT persisted list (read inside the
    // coroutine, same rationale as persistPlateEquipment); an index that no longer exists is a
    // no-op rather than a crash or a wrong-row edit.
    fun updateWarmupStep(index: Int, step: WarmupStep) = persistWarmupMethod { steps ->
        steps.mapIndexed { i, s -> if (i == index) step else s }
    }

    fun addWarmupStep(step: WarmupStep) = persistWarmupMethod { it + step }

    fun removeWarmupStep(index: Int) = persistWarmupMethod { steps ->
        steps.filterIndexed { i, _ -> i != index }
    }

    /** Swaps the step at [index] with its neighbor toward [delta] (−1 = up, +1 = down); out-of-range is a no-op. */
    fun moveWarmupStep(index: Int, delta: Int) = persistWarmupMethod { steps ->
        val target = index + delta
        if (index !in steps.indices || target !in steps.indices) steps
        else steps.toMutableList().apply { this[index] = this[target].also { this[target] = this[index] } }
    }

    fun resetWarmupMethod() = write { setWarmupMethod(defaultWarmupMethod) }

    private fun persistWarmupMethod(transform: (List<WarmupStep>) -> List<WarmupStep>) {
        // Same read-current-inside-the-coroutine pattern as persistPlateEquipment (see above) —
        // transforming the eager stateIn snapshot before DataStore's first emission would reset a
        // customized ladder back to the 40/60/80 defaults.
        viewModelScope.launch {
            val current = settingsRepository.settings.first().warmupMethod
            settingsRepository.setWarmupMethod(transform(current))
        }
    }

    // Sounds
    fun setTimerSound(value: Int) = write { setTimerSound(value) }
    fun setTimerVolume(value: Float) = write { setTimerVolume(value) }
    fun setSetCompleteVolume(value: Float) = write { setSetCompleteVolume(value) }
    fun setPrVolume(value: Float) = write { setPrVolume(value) }

    private fun writeThenRefreshWidget(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch {
            settingsRepository.block()
            widgetRefresher.refresh()
        }
    }

    private fun write(block: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch { settingsRepository.block() }
    }
}
