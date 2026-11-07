package com.enil.logez.feature.analytics

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.BodyRegion
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import java.time.LocalDate
import java.time.ZoneId
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

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Noon UTC 2026-08-22 — resolves to 2026-08-22 in every plausible test JVM zone. */
    private val nowMillis = 1_787_400_000_000L

    private fun millisOn(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 3_600_000L

    private fun exercise(id: String, name: String, muscle: MuscleGroup) = Exercise(
        id = id, name = name, exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = muscle,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )

    private fun completedWorkout(id: String, date: String, durationSeconds: Int = 3600) = WorkoutEntity(
        id = id, routineId = null, title = "W$id", notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = millisOn(date), endedAt = millisOn(date) + durationSeconds * 1000L,
        durationSeconds = durationSeconds, createdAt = millisOn(date), updatedAt = millisOn(date),
    )

    private fun workoutExercise(id: String, workoutId: String, exerciseId: String) = WorkoutExerciseEntity(
        id = id, workoutId = workoutId, exerciseId = exerciseId, orderIndex = 0, supersetGroup = null,
        restTimerSeconds = null, notes = null,
    )

    private fun set(id: String, weId: String, weightKg: Double, reps: Int, type: SetType = SetType.NORMAL) = WorkoutSetEntity(
        id = id, workoutExerciseId = weId, orderIndex = 0, setType = type, weightKg = weightKg, reps = reps,
        durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L,
    )

    private fun fixtureRepos(): Pair<FakeWorkoutRepository, FakeExerciseRepository> {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(completedWorkout("w1", "2026-08-18", 1800), completedWorkout("w2", "2026-08-11", 1200)),
            exercises = listOf(
                workoutExercise("we1", "w1", "ex-bench"),
                workoutExercise("we2", "w2", "ex-row"),
            ),
            sets = listOf(
                set("s1", "we1", 100.0, 5),
                set("s2", "we1", 100.0, 5),
                set("s3", "we2", 60.0, 10),
            ),
        )
        val exerciseRepo = FakeExerciseRepository(
            listOf(exercise("ex-bench", "Bench Press", MuscleGroup.CHEST), exercise("ex-row", "Row", MuscleGroup.UPPER_BACK)),
        )
        return workoutRepo to exerciseRepo
    }

    private fun newViewModel(
        focus: String? = null,
        repos: Pair<FakeWorkoutRepository, FakeExerciseRepository> = fixtureRepos(),
        healthMetricsSource: FakeHealthMetricsSource = FakeHealthMetricsSource(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
    ): AnalyticsViewModel = AnalyticsViewModel(
        SavedStateHandle(buildMap { focus?.let { put(AnalyticsViewModel.FOCUS_ARG, it) } }),
        repos.first, repos.second, settingsRepo, healthMetricsSource, FakeClock(currentMillis = nowMillis),
        // The screen's RefreshOnResume drives the first load (no init load) — mirror it here.
    ).also { it.refresh() }

    @Test
    fun `weekly volume bars sum set volumes and the two workouts land in different weeks`() = runTest {
        val vm = newViewModel()
        val bars = vm.uiState.value.training.bars
        // w2 (11 Aug, week of 10 Aug): 600. w1 (18 Aug, week of 17 Aug): 1000.
        assertEquals(listOf(600.0, 1000.0), bars.map { it.value })
        assertTrue(vm.uiState.value.hasAnyWorkouts)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `muscleDiagramVariant reflects whatever the settings repository is seeded with`() = runTest {
        val vm = newViewModel(settingsRepo = FakeSettingsRepository(UserSettings(muscleDiagramVariant = MuscleDiagramVariant.FEMALE)))
        assertEquals(MuscleDiagramVariant.FEMALE, vm.uiState.value.muscleDiagramVariant)
    }

    @Test
    fun `the focus nav arg pre-selects the training metric`() = runTest {
        val vm = newViewModel(focus = TrainingMetric.FREQUENCY.name)
        assertEquals(TrainingMetric.FREQUENCY, vm.uiState.value.training.metric)
        assertEquals(listOf(1.0, 1.0), vm.uiState.value.training.bars.map { it.value })
    }

    @Test
    fun `the training range is shared across every metric, not picked per metric`() = runTest {
        val vm = newViewModel()
        vm.selectTrainingRange(ChartRange.LAST_30_DAYS)
        vm.selectTrainingMetric(TrainingMetric.FREQUENCY)
        assertEquals(ChartRange.LAST_30_DAYS, vm.uiState.value.training.range)
        vm.selectTrainingMetric(TrainingMetric.VOLUME)
        assertEquals(ChartRange.LAST_30_DAYS, vm.uiState.value.training.range)
    }

    @Test
    fun `selecting a range while on one metric is still in effect after switching metrics twice`() = runTest {
        val vm = newViewModel()
        vm.selectTrainingMetric(TrainingMetric.DURATION)
        vm.selectTrainingRange(ChartRange.ALL_TIME)
        vm.selectTrainingMetric(TrainingMetric.REPS)
        vm.selectTrainingMetric(TrainingMetric.VOLUME)
        assertEquals(ChartRange.ALL_TIME, vm.uiState.value.training.range)
    }

    @Test
    fun `muscle distribution attributes sets to primary muscle with period totals`() = runTest {
        val vm = newViewModel()
        val card = vm.uiState.value.distribution
        assertEquals(
            listOf(MuscleGroup.CHEST to 2, MuscleGroup.UPPER_BACK to 1),
            card.current.map { it.group to it.setCount },
        )
        assertEquals(2, card.totals.workouts)
        assertEquals(3000L, card.totals.durationSeconds)
        assertEquals(1600.0, card.totals.volumeKg, 1e-9)
        assertEquals(3, card.totals.sets)
        // M20c: the muscle-balance wheel's 8 fixed axes for the same fixture (Chest 2, Back 1 via
        // UPPER_BACK, six regions at zero) -- percentages recomputed over the region-only total (3).
        assertEquals(
            listOf(BodyRegion.CHEST to (2 to 67), BodyRegion.BACK to (1 to 33)),
            card.balance.filter { it.setCount > 0 }.map { it.region to (it.setCount to it.sharePercent) },
        )
        assertEquals(8, card.balance.size)
        assertEquals(BodyRegion.entries.toList(), card.balance.map { it.region })
    }

    @Test
    fun `toggling a muscle removes it from the set-count stat and toggling again restores it`() = runTest {
        val vm = newViewModel()
        vm.toggleMuscle(MuscleGroup.CHEST)
        val filtered = vm.uiState.value.setCounts
        assertFalse(filtered.muscles.single { it.group == MuscleGroup.CHEST }.included)
        assertEquals(1, filtered.bars.sumOf { it.setCount })
        vm.toggleMuscle(MuscleGroup.CHEST)
        assertEquals(3, vm.uiState.value.setCounts.bars.sumOf { it.setCount })
    }

    @Test
    fun `main exercises rank by distinct workout count`() = runTest {
        val vm = newViewModel()
        val rows = vm.uiState.value.mainExercises.rows
        assertEquals(listOf("Bench Press" to 1, "Row" to 1), rows.map { it.exerciseName to it.workoutCount })
    }

    @Test
    fun `body card defaults to the most recent week and normalizes intensities`() = runTest {
        val vm = newViewModel()
        val body = vm.uiState.value.body
        assertEquals(LocalDate.parse("2026-08-17"), body.selectedWeek)
        assertEquals(1.0f, body.intensities[MuscleGroup.CHEST])
        vm.selectBodyWeek(LocalDate.parse("2026-08-10"))
        assertEquals(1.0f, vm.uiState.value.body.intensities[MuscleGroup.UPPER_BACK])
    }

    @Test
    fun `a refresh drops the positional bar selection so it cannot re-anchor to a shifted week`() = runTest {
        val vm = newViewModel()
        vm.selectTrainingBar(0)
        assertEquals(0, vm.uiState.value.training.selectedBar)
        vm.refresh()
        assertEquals(null, vm.uiState.value.training.selectedBar)
    }

    @Test
    fun `no workouts yields the honest dashboard empty state`() = runTest {
        val vm = newViewModel(repos = FakeWorkoutRepository() to FakeExerciseRepository())
        assertFalse(vm.uiState.value.hasAnyWorkouts)
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test
    fun `steps card is unavailable when Health Connect has nothing to show`() = runTest {
        val vm = newViewModel(
            healthMetricsSource = FakeHealthMetricsSource(
                availabilityValue = com.enil.logez.core.wellness.HealthConnectAvailability.Unavailable,
            ),
        )
        assertFalse(vm.uiState.value.steps.available)
        assertEquals(emptyList<DailyStepBar>(), vm.uiState.value.steps.bars)
    }

    @Test
    fun `steps card loads the last 30 days once Health Connect is available and granted`() = runTest {
        val history = listOf(
            com.enil.logez.core.wellness.DailyStepCount(LocalDate.parse("2026-08-10"), 5_000L),
            com.enil.logez.core.wellness.DailyStepCount(LocalDate.parse("2026-08-11"), 8_200L),
        )
        val healthMetricsSource = FakeHealthMetricsSource(
            availabilityValue = com.enil.logez.core.wellness.HealthConnectAvailability.Available,
            permissionsGranted = true,
            stepsHistory = history,
        )
        val vm = newViewModel(healthMetricsSource = healthMetricsSource)

        assertTrue(vm.uiState.value.steps.available)
        assertEquals(listOf(5_000L, 8_200L), vm.uiState.value.steps.bars.map { it.steps })
        assertEquals(1, healthMetricsSource.queriedStepsRanges.size)
    }

    @Test
    fun `tapping a steps bar selects it, and a refresh drops the selection`() = runTest {
        val history = listOf(com.enil.logez.core.wellness.DailyStepCount(LocalDate.parse("2026-08-10"), 5_000L))
        val vm = newViewModel(
            healthMetricsSource = FakeHealthMetricsSource(
                availabilityValue = com.enil.logez.core.wellness.HealthConnectAvailability.Available,
                permissionsGranted = true,
                stepsHistory = history,
            ),
        )

        vm.selectStepsBar(0)
        assertEquals(0, vm.uiState.value.steps.selectedBar)
        vm.refresh()
        assertEquals(null, vm.uiState.value.steps.selectedBar)
    }
}
