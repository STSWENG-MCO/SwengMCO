package com.enil.logez.feature.measurements

import com.enil.logez.core.domain.calc.BodyMeasurementMetric
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.MeasurementsTrackingMode
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.BodyMeasurement
import com.enil.logez.core.domain.repository.ProgressPhoto
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakeProgressPhotoStore
import com.enil.logez.fakes.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.enil.logez.fakes.FakeMediaFileCleaner

/**
 * The Uri-touching methods (attachPhotoFromCapture / confirmReplaceTodaysPhoto /
 * cancelReplaceTodaysPhoto) are deliberately untested here: constructing a real android.net.Uri
 * throws in a plain JVM unit test with no Robolectric, and this codebase's own established
 * convention (CustomExerciseEditorViewModelTest never exercises ExerciseMediaStore's Uri-taking
 * path either) is to leave that boundary untested rather than pull in Robolectric or a mocking
 * framework for one method (this project's fakes-not-mocks rule, PHASE2_PLAN.md §10.1 rule 2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun entry(date: String, weightKg: Double? = null, waistCm: Double? = null) = BodyMeasurement(
        date = date, weightKg = weightKg, leanMassKg = null, fatPercent = null,
        neckCm = null, shoulderCm = null, chestCm = null,
        leftBicepCm = null, rightBicepCm = null, leftForearmCm = null, rightForearmCm = null,
        abdomenCm = null, waistCm = waistCm, hipsCm = null,
        leftThighCm = null, rightThighCm = null, leftCalfCm = null, rightCalfCm = null,
        updatedAt = 0L,
    )

    private fun viewModel(
        measurementRepo: FakeMeasurementRepository = FakeMeasurementRepository(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        photoStore: FakeProgressPhotoStore = FakeProgressPhotoStore(),
        clock: FakeClock = FakeClock(),
    ) = MeasurementsViewModel(measurementRepo, settingsRepo, photoStore, clock)

    @Test
    fun `an empty history produces an empty, non-loading state`() = runTest {
        val vm = viewModel()
        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.entries.isEmpty())
        assertTrue(state.photos.isEmpty())
        assertTrue(state.chartPoints.isEmpty())
    }

    @Test
    fun `saveEntry reaches the repository`() = runTest {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(measurementRepo = repo)
        vm.saveEntry(entry("2026-08-20", weightKg = 80.0))
        assertEquals(1, vm.uiState.value.entries.size)
        assertEquals(80.0, vm.uiState.value.entries.single().weightKg)
    }

    @Test
    fun `deleteEntry removes the entry for that date only`() = runTest {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(measurementRepo = repo)
        vm.saveEntry(entry("2026-08-20", weightKg = 80.0))
        vm.saveEntry(entry("2026-08-21", weightKg = 81.0))
        vm.deleteEntry("2026-08-20")
        assertEquals(listOf("2026-08-21"), vm.uiState.value.entries.map { it.date })
    }

    @Test
    fun `deletePhoto deletes the photo file after the row`() = runTest {
        val repo = FakeMeasurementRepository()
        val cleaner = FakeMediaFileCleaner()
        val vm = MeasurementsViewModel(repo, FakeSettingsRepository(), FakeProgressPhotoStore(), FakeClock(currentMillis = 0L), cleaner)
        val photo = ProgressPhoto(id = "p1", date = "2026-08-20", filePath = "progress_photos/p1.jpg", createdAt = 0L)
        repo.upsertPhoto(photo)

        vm.deletePhoto(photo)

        assertTrue(vm.uiState.value.photos.isEmpty())
        assertEquals(listOf("progress_photos/p1.jpg"), cleaner.requested)
    }

    @Test
    fun `deletePhoto reaches the repository`() = runTest {
        val repo = FakeMeasurementRepository()
        val vm = viewModel(measurementRepo = repo)
        val photo = ProgressPhoto(id = "p1", date = "2026-08-20", filePath = "progress_photos/p1.jpg", createdAt = 0L)
        repo.upsertPhoto(photo)
        assertEquals(1, vm.uiState.value.photos.size)
        vm.deletePhoto(photo)
        assertTrue(vm.uiState.value.photos.isEmpty())
    }

    @Test
    fun `selectMetric and selectRange recompute chartPoints for the newly selected metric and window`() = runTest {
        val repo = FakeMeasurementRepository()
        val clock = FakeClock(currentMillis = NOW_MILLIS) // 2026-08-22 noon UTC
        val vm = viewModel(measurementRepo = repo, clock = clock)
        vm.saveEntry(entry("2026-08-20", weightKg = 80.0, waistCm = 90.0))
        vm.saveEntry(entry("2020-01-01", weightKg = 70.0, waistCm = 85.0)) // outside any range but ALL_TIME

        // Default metric/range.
        assertEquals(BodyMeasurementMetric.WEIGHT, vm.uiState.value.selectedMetric)
        assertEquals(ChartRange.LAST_3_MONTHS, vm.uiState.value.selectedRange)
        assertEquals(listOf(80.0), vm.uiState.value.chartPoints.map { it.value })

        vm.selectMetric(BodyMeasurementMetric.WAIST)
        assertEquals(listOf(90.0), vm.uiState.value.chartPoints.map { it.value })

        vm.selectRange(ChartRange.ALL_TIME)
        assertEquals(listOf("2020-01-01", "2026-08-20"), vm.uiState.value.chartPoints.map { it.date })
    }

    @Test
    fun `units in state reflect whatever the settings repository is seeded with`() = runTest {
        val settingsRepo = FakeSettingsRepository(UserSettings(weightUnit = WeightUnit.LB, lengthUnit = LengthUnit.IN))
        val vm = viewModel(settingsRepo = settingsRepo)
        assertEquals(WeightUnit.LB, vm.uiState.value.weightUnit)
        assertEquals(LengthUnit.IN, vm.uiState.value.lengthUnit)
    }

    @Test
    fun `dismissPhotoCaptureError clears the flag without touching the repository`() = runTest {
        val vm = viewModel()
        // No public way to set photoCaptureError=true without a real Uri (see class doc) -- this
        // only proves the clear path is a no-op when already false, i.e. it never throws or leaks
        // into unrelated state.
        vm.dismissPhotoCaptureError()
        assertFalse(vm.uiState.value.photoCaptureError)
        assertNull(vm.uiState.value.pendingPhotoReplace)
    }

    @Test
    fun `setTrackingMode persists and is reflected in uiState`() = runTest {
        val settingsRepo = FakeSettingsRepository()
        val vm = viewModel(settingsRepo = settingsRepo)
        assertEquals(MeasurementsTrackingMode.COMPLETE, vm.uiState.value.trackingMode)

        vm.setTrackingMode(MeasurementsTrackingMode.SIMPLIFIED)

        assertEquals(MeasurementsTrackingMode.SIMPLIFIED, vm.uiState.value.trackingMode)
        assertEquals(MeasurementsTrackingMode.SIMPLIFIED, settingsRepo.settings.value.measurementsTrackingMode)
    }

    @Test
    fun `switching to Simplified clamps a Complete-only selected metric, and switching back restores it`() = runTest {
        val settingsRepo = FakeSettingsRepository()
        val vm = viewModel(settingsRepo = settingsRepo)
        vm.selectMetric(BodyMeasurementMetric.WAIST)
        assertEquals(BodyMeasurementMetric.WAIST, vm.uiState.value.selectedMetric)

        vm.setTrackingMode(MeasurementsTrackingMode.SIMPLIFIED)
        assertEquals(BodyMeasurementMetric.WEIGHT, vm.uiState.value.selectedMetric)

        vm.setTrackingMode(MeasurementsTrackingMode.COMPLETE)
        assertEquals(BodyMeasurementMetric.WAIST, vm.uiState.value.selectedMetric)
    }

    private companion object {
        /** Noon UTC 2026-08-22 -- resolves to 2026-08-22 in every plausible test JVM zone. */
        const val NOW_MILLIS = 1_787_400_000_000L
    }
}
