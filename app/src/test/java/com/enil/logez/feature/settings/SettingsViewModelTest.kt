package com.enil.logez.feature.settings

import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WarmupStep
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.defaultWarmupMethod
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWidgetRefresher
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** M16: every Settings row's write function must persist through its repository setter. */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val repository = FakeSettingsRepository()
    private val viewModel by lazy { SettingsViewModel(repository, FakeWidgetRefresher()) }

    @Test
    fun `settings state mirrors the repository`() = runTest {
        val custom = UserSettings(weightUnit = WeightUnit.LB, defaultRestTimerSeconds = 120, rpeTrackingEnabled = true)
        val vm = SettingsViewModel(FakeSettingsRepository(custom), FakeWidgetRefresher())
        assertEquals(custom, vm.settings.value)
    }

    // --- Preferences ---

    @Test
    fun `setWeightUnit persists LB`() = runTest {
        viewModel.setWeightUnit(WeightUnit.LB)
        assertEquals(WeightUnit.LB, repository.settings.value.weightUnit)
    }

    @Test
    fun `setDistanceUnit persists MILES`() = runTest {
        viewModel.setDistanceUnit(DistanceUnit.MILES)
        assertEquals(DistanceUnit.MILES, repository.settings.value.distanceUnit)
    }

    @Test
    fun `setLengthUnit persists IN`() = runTest {
        viewModel.setLengthUnit(LengthUnit.IN)
        assertEquals(LengthUnit.IN, repository.settings.value.lengthUnit)
    }

    @Test
    fun `setFirstDayOfWeek persists SUNDAY`() = runTest {
        viewModel.setFirstDayOfWeek(DayOfWeek.SUNDAY)
        assertEquals(DayOfWeek.SUNDAY, repository.settings.value.firstDayOfWeek)
    }

    // --- Workouts ---

    @Test
    fun `setDefaultRestTimerSeconds persists 0 as off and 300 as five minutes`() = runTest {
        viewModel.setDefaultRestTimerSeconds(0)
        assertEquals(0, repository.settings.value.defaultRestTimerSeconds)
        viewModel.setDefaultRestTimerSeconds(300)
        assertEquals(300, repository.settings.value.defaultRestTimerSeconds)
    }

    @Test
    fun `setPreviousValuesMode persists SAME_ROUTINE`() = runTest {
        viewModel.setPreviousValuesMode(PreviousValuesMode.SAME_ROUTINE)
        assertEquals(PreviousValuesMode.SAME_ROUTINE, repository.settings.value.previousValuesMode)
    }

    @Test
    fun `setKeepAwake persists false`() = runTest {
        viewModel.setKeepAwake(false)
        assertFalse(repository.settings.value.keepAwake)
    }

    @Test
    fun `setSmartSupersetScrolling persists false`() = runTest {
        viewModel.setSmartSupersetScrolling(false)
        assertFalse(repository.settings.value.smartSupersetScrolling)
    }

    @Test
    fun `setInlineTimerEnabled persists false`() = runTest {
        viewModel.setInlineTimerEnabled(false)
        assertFalse(repository.settings.value.inlineTimerEnabled)
    }

    @Test
    fun `setLivePrNotificationEnabled persists false`() = runTest {
        viewModel.setLivePrNotificationEnabled(false)
        assertFalse(repository.settings.value.livePrNotificationEnabled)
    }

    @Test
    fun `setRpeTrackingEnabled writes through both ways`() = runTest {
        viewModel.setRpeTrackingEnabled(true)
        assertTrue(repository.settings.value.rpeTrackingEnabled)
        viewModel.setRpeTrackingEnabled(false)
        assertFalse(repository.settings.value.rpeTrackingEnabled)
    }

    @Test
    fun `setIncludeWarmupsInStats persists true`() = runTest {
        viewModel.setIncludeWarmupsInStats(true)
        assertTrue(repository.settings.value.includeWarmupsInStats)
    }

    // --- Calculators ---

    @Test
    fun `setPlateCalculatorEnabled persists false`() = runTest {
        viewModel.setPlateCalculatorEnabled(false)
        assertFalse(repository.settings.value.plateCalculatorEnabled)
    }

    @Test
    fun `setWarmupCalculatorEnabled persists false`() = runTest {
        viewModel.setWarmupCalculatorEnabled(false)
        assertFalse(repository.settings.value.warmupCalculatorEnabled)
    }

    // --- M17 plate equipment (persisted whole through setPlateEquipment) ---

    @Test
    fun `addBar persists a second bar in sorted order`() = runTest {
        viewModel.addBar(15.0)
        assertEquals(listOf(15.0, 20.0), repository.settings.value.plateEquipment.barsKg)
    }

    @Test
    fun `addBar rounds to the quarter-kg grid and skips duplicates`() = runTest {
        viewModel.addBar(17.4) // stored as 17.5
        viewModel.addBar(20.0) // already owned — no-op
        assertEquals(listOf(17.5, 20.0), repository.settings.value.plateEquipment.barsKg)
    }

    @Test
    fun `removeBar never removes the last bar`() = runTest {
        viewModel.removeBar(20.0)
        assertEquals(listOf(20.0), repository.settings.value.plateEquipment.barsKg)
    }

    @Test
    fun `removeBar drops a bar once a second one exists`() = runTest {
        viewModel.addBar(15.0)
        viewModel.removeBar(20.0)
        assertEquals(listOf(15.0), repository.settings.value.plateEquipment.barsKg)
    }

    @Test
    fun `addPlate and removePlate persist the transformed denomination list`() = runTest {
        viewModel.addPlate(0.5)
        assertEquals(listOf(0.5, 1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0), repository.settings.value.plateEquipment.platesKg)
        viewModel.removePlate(25.0)
        assertEquals(listOf(0.5, 1.25, 2.5, 5.0, 10.0, 15.0, 20.0), repository.settings.value.plateEquipment.platesKg)
    }

    // --- M18 Warm-up Sets editor (persisted whole through setWarmupMethod) ---

    @Test
    fun `updateWarmupStep replaces exactly that row`() = runTest {
        viewModel.updateWarmupStep(1, WarmupStep(percent = 0.50, reps = 8))
        assertEquals(
            listOf(WarmupStep(0.40, 5), WarmupStep(0.50, 8), WarmupStep(0.80, 3)),
            repository.settings.value.warmupMethod,
        )
    }

    @Test
    fun `updateWarmupStep with a stale index is a no-op`() = runTest {
        viewModel.updateWarmupStep(9, WarmupStep(percent = 0.50, reps = 8))
        assertEquals(defaultWarmupMethod, repository.settings.value.warmupMethod)
    }

    @Test
    fun `addWarmupStep appends to the persisted ladder`() = runTest {
        viewModel.addWarmupStep(WarmupStep(percent = 0.90, reps = 1))
        assertEquals(defaultWarmupMethod + WarmupStep(0.90, 1), repository.settings.value.warmupMethod)
    }

    @Test
    fun `removeWarmupStep drops that row and can empty the ladder entirely`() = runTest {
        viewModel.removeWarmupStep(0)
        assertEquals(listOf(WarmupStep(0.60, 5), WarmupStep(0.80, 3)), repository.settings.value.warmupMethod)
        viewModel.removeWarmupStep(0)
        viewModel.removeWarmupStep(0)
        assertEquals(emptyList<WarmupStep>(), repository.settings.value.warmupMethod)
    }

    @Test
    fun `moveWarmupStep swaps with its neighbor and ignores out-of-range moves`() = runTest {
        viewModel.moveWarmupStep(2, -1) // 80% up
        assertEquals(
            listOf(WarmupStep(0.40, 5), WarmupStep(0.80, 3), WarmupStep(0.60, 5)),
            repository.settings.value.warmupMethod,
        )
        viewModel.moveWarmupStep(0, -1) // no neighbor above — no-op
        viewModel.moveWarmupStep(2, +1) // no neighbor below — no-op
        assertEquals(
            listOf(WarmupStep(0.40, 5), WarmupStep(0.80, 3), WarmupStep(0.60, 5)),
            repository.settings.value.warmupMethod,
        )
    }

    @Test
    fun `resetWarmupMethod restores the 40-60-80 default after edits`() = runTest {
        viewModel.removeWarmupStep(0)
        viewModel.addWarmupStep(WarmupStep(percent = 0.95, reps = 1))
        viewModel.resetWarmupMethod()
        assertEquals(defaultWarmupMethod, repository.settings.value.warmupMethod)
    }

    @Test
    fun `sequential warm-up edits compose instead of clobbering`() = runTest {
        // The read-current-inside-the-coroutine pattern: each edit re-reads the just-written value.
        viewModel.addWarmupStep(WarmupStep(percent = 0.90, reps = 2))
        viewModel.updateWarmupStep(3, WarmupStep(percent = 0.95, reps = 1))
        assertEquals(defaultWarmupMethod + WarmupStep(0.95, 1), repository.settings.value.warmupMethod)
    }

    // --- Sounds ---

    @Test
    fun `setTimerSound persists tone 3`() = runTest {
        viewModel.setTimerSound(3)
        assertEquals(3, repository.settings.value.timerSound)
    }

    @Test
    fun `setTimerVolume persists 1_0`() = runTest {
        viewModel.setTimerVolume(1.0f)
        assertEquals(1.0f, repository.settings.value.timerVolume)
    }

    @Test
    fun `setSetCompleteVolume persists 0_0`() = runTest {
        viewModel.setSetCompleteVolume(0.0f)
        assertEquals(0.0f, repository.settings.value.setCompleteVolume)
    }

    @Test
    fun `setPrVolume persists 0_33`() = runTest {
        viewModel.setPrVolume(0.33f)
        assertEquals(0.33f, repository.settings.value.prVolume)
    }

    @Test
    fun `setShowHeatmap persists false`() = runTest {
        viewModel.setShowHeatmap(false)
        assertEquals(false, repository.settings.value.showHeatmap)
    }

    @Test
    fun `setShowGoals persists false`() = runTest {
        viewModel.setShowGoals(false)
        assertEquals(false, repository.settings.value.showGoals)
    }
}
