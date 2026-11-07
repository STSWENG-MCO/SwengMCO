package com.enil.logez.feature.exercises

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.domain.calc.ChartMetric
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.designsystem.BodyDiagramRegions
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.ExerciseHistoryEntry
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun customExercise(id: String = "custom-1") = Exercise(
        id = id,
        name = "My Custom Row",
        exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = MuscleGroup.UPPER_BACK,
        secondaryMuscleGroups = emptyList(),
        equipment = Equipment.MACHINE,
        instructions = "",
        mediaPath = null,
        isCustom = true,
        isBodyweightVolumeEligible = false,
        isDeleted = false,
        createdAt = 100L,
        updatedAt = 100L,
    )

    private fun seedExercise(id: String = "seed-1") = customExercise(id).copy(isCustom = false)

    @Test
    fun `duplicate creates a new custom copy with a fresh id and no history`() = runTest {
        val original = seedExercise()
        val history = listOf(
            ExerciseHistoryEntry(
                workoutId = "w1", workoutTitle = "Push Day", workoutStartedAt = 0, setId = "s1",
                setOrderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5,
                durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null,
            ),
        )
        val exerciseRepo = FakeExerciseRepository(listOf(original))
        val workoutRepo = FakeWorkoutRepository(historyByExercise = mapOf(original.id to history))
        val vm = ExerciseDetailViewModel(
            SavedStateHandle(mapOf("exerciseId" to original.id)),
            exerciseRepo,
            workoutRepo,
            FakePersonalRecordsRepository(),
            FakeMeasurementRepository(),
            FakeSettingsRepository(),
            FakeClock(currentMillis = 7_000L),
        )

        val newId = vm.duplicate()

        assertNotNull(newId)
        val copy = exerciseRepo.getById(newId!!)!!
        assertTrue(copy.isCustom)
        assertEquals(original.name, copy.name)
        assertFalse(copy.id == original.id)
        assertEquals(7_000L, copy.createdAt)
        // The duplicate's own history is separately queried per-id — the fake has none registered for newId,
        // which is exactly "no history attached" (§5.2).
        assertTrue(workoutRepo.getExerciseHistory(newId).isEmpty())
    }

    @Test
    fun `delete soft-deletes a custom exercise but it remains resolvable (history stays valid)`() = runTest {
        val custom = customExercise()
        val exerciseRepo = FakeExerciseRepository(listOf(custom))
        val vm = ExerciseDetailViewModel(
            SavedStateHandle(mapOf("exerciseId" to custom.id)),
            exerciseRepo,
            FakeWorkoutRepository(),
            FakePersonalRecordsRepository(),
            FakeMeasurementRepository(),
            FakeSettingsRepository(),
            FakeClock(),
        )

        var deletedCallback = false
        vm.delete { deletedCallback = true }

        assertTrue(deletedCallback)
        val afterDelete = exerciseRepo.getById(custom.id)
        assertNotNull(afterDelete) // never hard-deleted
        assertTrue(afterDelete!!.isDeleted)
    }

    @Test
    fun `delete also works for seed exercises (Owner directive 2026-08-26)`() = runTest {
        val seed = seedExercise()
        val exerciseRepo = FakeExerciseRepository(listOf(seed))
        val vm = ExerciseDetailViewModel(
            SavedStateHandle(mapOf("exerciseId" to seed.id)),
            exerciseRepo,
            FakeWorkoutRepository(),
            FakePersonalRecordsRepository(),
            FakeMeasurementRepository(),
            FakeSettingsRepository(),
            FakeClock(),
        )

        var deletedCallback = false
        vm.delete { deletedCallback = true }

        assertTrue(deletedCallback)
        assertTrue(exerciseRepo.getById(seed.id)!!.isDeleted)
    }

    // --- M6a Summary tab (§5.2 / §8.9 / §8.5) ---

    /** Noon UTC, 2026-08-22 — §8.9's vector `today` in every zone the JVM is likely to run in. */
    private val nowMillis = 1_787_400_000_000L
    private val dayMillis = 86_400_000L

    private fun statSet(id: String, weightKg: Double, reps: Int, workoutId: String, startedAt: Long, setType: SetType = SetType.NORMAL) = StatSet(
        setId = id, workoutId = workoutId, workoutStartedAt = startedAt, orderIndex = 0, setType = setType,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null,
        customMetric = null, isCompleted = true,
    )

    private fun summaryViewModel(
        exercise: Exercise,
        statSets: List<StatSet> = emptyList(),
        records: List<PersonalRecordEntity> = emptyList(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
    ) = ExerciseDetailViewModel(
        SavedStateHandle(mapOf("exerciseId" to exercise.id)),
        FakeExerciseRepository(listOf(exercise)),
        FakeWorkoutRepository(statSetsByExercise = mapOf(exercise.id to statSets)),
        FakePersonalRecordsRepository(records),
        FakeMeasurementRepository(),
        settingsRepo,
        FakeClock(currentMillis = nowMillis),
    )

    @Test
    fun `summary is the honest empty state until the exercise has an included set`() = runTest {
        val vm = summaryViewModel(seedExercise())
        val summary = vm.uiState.value.summary
        assertFalse(summary.hasAnyLoggedSets)
        // Metrics still resolve so the tab's chrome is stable, but nothing is plotted.
        assertEquals(ChartMetric.HEAVIEST_WEIGHT, summary.selectedMetric)
        assertTrue(summary.points.isEmpty())
        assertTrue(summary.personalRecords.isEmpty())
        assertTrue(summary.setRecords.isEmpty())
    }

    @Test
    fun `the default range is 3 months and its window filters out older workouts`() = runTest {
        val recent = statSet("s1", 100.0, 5, workoutId = "w-recent", startedAt = nowMillis - 10 * dayMillis)
        val old = statSet("s2", 120.0, 5, workoutId = "w-old", startedAt = nowMillis - 200 * dayMillis)
        val vm = summaryViewModel(seedExercise(), statSets = listOf(recent, old))

        val summary = vm.uiState.value.summary
        assertEquals(ChartRange.LAST_3_MONTHS, summary.selectedRange)
        assertEquals(listOf("w-recent"), summary.points.map { it.workoutId })

        vm.selectRange(ChartRange.ALL_TIME)
        assertEquals(listOf("w-old", "w-recent"), vm.uiState.value.summary.points.map { it.workoutId })
    }

    @Test
    fun `switching metric recomputes point values over the same workouts`() = runTest {
        val sets = listOf(
            statSet("s1", 100.0, 1, workoutId = "w1", startedAt = nowMillis - 5 * dayMillis),
            statSet("s2", 90.0, 5, workoutId = "w1", startedAt = nowMillis - 5 * dayMillis),
            statSet("s3", 80.0, 10, workoutId = "w1", startedAt = nowMillis - 5 * dayMillis),
        )
        val vm = summaryViewModel(seedExercise(), statSets = sets)

        assertEquals(100.0, vm.uiState.value.summary.points.single().value, 1e-9)

        vm.selectMetric(ChartMetric.SESSION_VOLUME)
        assertEquals(1350.0, vm.uiState.value.summary.points.single().value, 1e-9)

        vm.selectMetric(ChartMetric.TOTAL_REPS)
        assertEquals(16.0, vm.uiState.value.summary.points.single().value, 1e-9)
    }

    @Test
    fun `personal records list follows PrType declaration order regardless of cache order`() = runTest {
        val exercise = seedExercise()
        val records = listOf(
            PersonalRecordEntity(id = "r2", exerciseId = exercise.id, workoutId = "w1", workoutSetId = "s1", prType = PrType.BEST_SET_VOLUME, value = 500.0, achievedAt = 1L),
            PersonalRecordEntity(id = "r1", exerciseId = exercise.id, workoutId = "w1", workoutSetId = "s1", prType = PrType.HEAVIEST_WEIGHT, value = 105.0, achievedAt = 1L),
        )
        val set = statSet("s1", 105.0, 5, workoutId = "w1", startedAt = nowMillis - dayMillis)
        val vm = summaryViewModel(exercise, statSets = listOf(set), records = records)

        assertEquals(
            listOf(PrType.HEAVIEST_WEIGHT, PrType.BEST_SET_VOLUME),
            vm.uiState.value.summary.personalRecords.map { it.prType },
        )
        assertEquals(105.0, vm.uiState.value.summary.personalRecords.first().value, 1e-9)
    }

    @Test
    fun `set records appear for weight-reps types and never for others`() = runTest {
        val sets = listOf(
            statSet("s1", 100.0, 5, workoutId = "w1", startedAt = nowMillis - dayMillis),
            statSet("s2", 105.0, 3, workoutId = "w1", startedAt = nowMillis - dayMillis),
        )
        val weightReps = summaryViewModel(seedExercise(), statSets = sets)
        assertEquals(listOf(3 to 105.0, 5 to 100.0), weightReps.uiState.value.summary.setRecords.map { it.reps to it.weightKg })

        val repsOnly = summaryViewModel(seedExercise("seed-2").copy(exerciseType = ExerciseType.REPS_ONLY), statSets = sets)
        assertTrue(repsOnly.uiState.value.summary.setRecords.isEmpty())
    }

    @Test
    fun `a warm-up-only history still counts as never-logged for the summary empty state`() = runTest {
        val warmupOnly = listOf(statSet("s1", 60.0, 10, workoutId = "w1", startedAt = nowMillis - dayMillis, setType = SetType.WARMUP))
        val vm = summaryViewModel(seedExercise(), statSets = warmupOnly)
        assertFalse(vm.uiState.value.summary.hasAnyLoggedSets)
    }

    // --- Muscles-worked diagram (per-exercise body diagram on the Summary tab) ---

    @Test
    fun `muscleIntensity weights the primary group full and every secondary group half`() = runTest {
        val exercise = seedExercise().copy(
            primaryMuscleGroup = MuscleGroup.CHEST,
            secondaryMuscleGroups = listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
        )
        val vm = summaryViewModel(exercise)

        assertEquals(
            mapOf(MuscleGroup.CHEST to 1.0f, MuscleGroup.TRICEPS to 0.5f, MuscleGroup.SHOULDERS to 0.5f),
            vm.uiState.value.muscleIntensity,
        )
    }

    @Test
    fun `an exercise with no secondary groups lights only its primary`() = runTest {
        val exercise = seedExercise().copy(primaryMuscleGroup = MuscleGroup.BICEPS, secondaryMuscleGroups = emptyList())
        val vm = summaryViewModel(exercise)
        assertEquals(mapOf(MuscleGroup.BICEPS to 1.0f), vm.uiState.value.muscleIntensity)
    }

    /**
     * The diagram is metadata about the exercise, not a stat derived from history — so it must be
     * populated even when the Summary tab is otherwise showing its never-logged empty state (that
     * is exactly when "what does this train?" is most useful).
     */
    @Test
    fun `muscleIntensity is populated even for an exercise that has never been logged`() = runTest {
        val vm = summaryViewModel(seedExercise().copy(primaryMuscleGroup = MuscleGroup.LATS))
        assertFalse(vm.uiState.value.summary.hasAnyLoggedSets)
        assertEquals(mapOf(MuscleGroup.LATS to 1.0f), vm.uiState.value.muscleIntensity)
    }

    @Test
    fun `muscleDiagramVariant reflects whatever the settings repository is seeded with`() = runTest {
        val vm = summaryViewModel(
            seedExercise(),
            settingsRepo = FakeSettingsRepository(UserSettings(muscleDiagramVariant = MuscleDiagramVariant.FEMALE)),
        )
        assertEquals(MuscleDiagramVariant.FEMALE, vm.uiState.value.muscleDiagramVariant)
    }

    /**
     * CARDIO/FULL_BODY/OTHER have no drawable region, so the screen skips the whole section for
     * them ([com.enil.logez.core.designsystem.BodyDiagramRegions.MAPPABLE]) — this pins the
     * ViewModel half of that contract: nothing in the intensity map is renderable.
     */
    @Test
    fun `a cardio exercise produces no mappable muscle regions to highlight`() = runTest {
        val vm = summaryViewModel(seedExercise().copy(primaryMuscleGroup = MuscleGroup.CARDIO, secondaryMuscleGroups = emptyList()))
        assertTrue(vm.uiState.value.muscleIntensity.keys.none { it in BodyDiagramRegions.MAPPABLE })
    }
}
