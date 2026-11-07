package com.enil.logez.feature.workout

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.fakes.FakeTransactionRunner
import com.enil.logez.feature.history.WorkoutEditor
import com.enil.logez.feature.workout.finish.LivePrDetector
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import com.enil.logez.feature.workout.session.SetCompletionUseCase
import com.enil.logez.feature.workout.session.WorkoutSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLoggerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun exercise(id: String, name: String, type: ExerciseType = ExerciseType.WEIGHT_REPS, equipment: Equipment = Equipment.BARBELL) = Exercise(
        id = id, name = name, exerciseType = type, primaryMuscleGroup = MuscleGroup.CHEST, secondaryMuscleGroups = emptyList(),
        equipment = equipment, instructions = "", mediaPath = null, isCustom = false, isBodyweightVolumeEligible = false,
        isDeleted = false, createdAt = 0, updatedAt = 0,
    )

    private fun newViewModel(
        workoutId: String = "w1",
        workoutRepo: FakeWorkoutRepository = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout(workoutId))),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        clock: FakeClock = FakeClock(currentMillis = 10_000L),
        sessionController: WorkoutSessionController = WorkoutSessionController(FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher())),
        isEditMode: Boolean = false,
        recordsRepo: FakePersonalRecordsRepository = FakePersonalRecordsRepository(),
    ): WorkoutLoggerViewModel {
        val setCompletionUseCase = SetCompletionUseCase(workoutRepo, settingsRepo, sessionController, clock)
        val livePrDetector = LivePrDetector(
            workoutRepo, exerciseRepo, recordsRepo, FakeMeasurementRepository(), clock,
        )
        val updater = PersonalRecordsUpdater(workoutRepo, exerciseRepo, recordsRepo, FakeMeasurementRepository(), settingsRepo)
        val editor = WorkoutEditor(workoutRepo, updater, FakeTransactionRunner(), clock)
        return WorkoutLoggerViewModel(
            SavedStateHandle(
                buildMap<String, Any> {
                    put("workoutId", workoutId)
                    if (isEditMode) put(WorkoutLoggerViewModel.EDIT_MODE_ARG, true)
                },
            ),
            workoutRepo, exerciseRepo, settingsRepo,
            sessionController, setCompletionUseCase, livePrDetector, editor, FakeHealthMetricsSource(), clock,
        )
    }

    private fun aCompletedWorkout(id: String, startedAt: Long = 5_000L, durationSeconds: Int = 1800) = WorkoutEntity(
        id = id, routineId = null, title = "Push Day", notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = startedAt, endedAt = startedAt + durationSeconds * 1000L, durationSeconds = durationSeconds,
        createdAt = startedAt, updatedAt = startedAt,
    )

    private fun editFixture(
        sets: List<WorkoutSetEntity> = listOf(
            WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L),
        ),
    ): FakeWorkoutRepository = FakeWorkoutRepository(
        workouts = listOf(aCompletedWorkout("w1")),
        exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
        sets = sets,
    )

    // --- §5.1.7 RPE picker ---

    @Test
    fun `the RPE column is hidden by default and appears once the setting is on`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val vmOff = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo, settingsRepo = FakeSettingsRepository(UserSettings(rpeTrackingEnabled = false)))
        val vmOn = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo, settingsRepo = FakeSettingsRepository(UserSettings(rpeTrackingEnabled = true)))
        assertFalse(vmOff.uiState.value.rpeTrackingEnabled)
        assertTrue(vmOn.uiState.value.rpeTrackingEnabled)
    }

    // --- Regression: batched set loading must key by workout-exercise INSTANCE id, not exercise
    // id. Nothing dedups the picker or addExercises, so the same exercise can appear twice in one
    // workout; keying the loaded sets by exercise id hands every duplicate card a merged (or
    // last-wins) set list — double counts, shifted PREVIOUS pairing, corrupted orderIndex on
    // addSet. ---

    @Test
    fun `duplicate instances of the same exercise each load only their own sets`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                WorkoutExerciseEntity(id = "we2", workoutId = "w1", exerciseId = "ex-1", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = listOf(
                WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
                WorkoutSetEntity(id = "s2", workoutExerciseId = "we1", orderIndex = 1, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
                WorkoutSetEntity(id = "s3", workoutExerciseId = "we2", orderIndex = 0, setType = SetType.NORMAL, weightKg = 40.0, reps = 12, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            ),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        val cards = vm.uiState.value.exercises
        assertEquals(listOf("we1", "we2"), cards.map { it.id })
        // Each card carries exactly its own instance's sets — never the twin's, never a merge.
        assertEquals(listOf("s1", "s2"), cards[0].sets.map { it.id })
        assertEquals(listOf("s3"), cards[1].sets.map { it.id })
    }

    // --- M16: Settings is reachable mid-session from the logger's overflow menu, with plain
    // navigation keeping this ViewModel alive underneath — so these settings must apply live,
    // not as an init-time snapshot that only a fresh logger nav entry would refresh ---

    @Test
    fun `mid-session toggles to RPE, keep-awake and inline timer apply without a reload`() = runTest {
        val settingsRepo = FakeSettingsRepository()
        val vm = newViewModel(settingsRepo = settingsRepo)
        assertFalse(vm.uiState.value.rpeTrackingEnabled)
        assertTrue(vm.uiState.value.keepAwakeEnabled)
        assertTrue(vm.uiState.value.inlineTimerEnabled)

        settingsRepo.setRpeTrackingEnabled(true)
        settingsRepo.setKeepAwake(false)
        settingsRepo.setInlineTimerEnabled(false)

        assertTrue(vm.uiState.value.rpeTrackingEnabled)
        assertFalse(vm.uiState.value.keepAwakeEnabled)
        assertFalse(vm.uiState.value.inlineTimerEnabled)
    }

    // --- M17 §5.1.5 Plate Calculator: config plumbing (the sheet itself is thin — the math is
    // covered by PlateCalculatorTest, the equipment rules by PlateEquipmentTest) ---

    @Test
    fun `mid-session plate calculator setting, unit and equipment changes apply without a reload`() = runTest {
        val settingsRepo = FakeSettingsRepository()
        val vm = newViewModel(settingsRepo = settingsRepo)
        assertTrue(vm.uiState.value.plateCalculator.enabled)
        assertEquals(WeightUnit.KG, vm.uiState.value.plateCalculator.weightUnit)
        assertEquals(listOf(20.0), vm.uiState.value.plateCalculator.equipment.barsKg)

        settingsRepo.setPlateCalculatorEnabled(false)
        settingsRepo.setWeightUnit(WeightUnit.LB)
        settingsRepo.setPlateEquipment(PlateEquipment(barsKg = listOf(15.0, 20.0), platesKg = listOf(2.5, 5.0)))

        assertFalse(vm.uiState.value.plateCalculator.enabled)
        assertEquals(WeightUnit.LB, vm.uiState.value.plateCalculator.weightUnit)
        assertEquals(listOf(15.0, 20.0), vm.uiState.value.plateCalculator.equipment.barsKg)
        assertEquals(listOf(2.5, 5.0), vm.uiState.value.plateCalculator.equipment.platesKg)
    }

    @Test
    fun `loaded exercises carry their equipment so barbell rows can gate the affordance`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)
        assertEquals(Equipment.BARBELL, vm.uiState.value.exercises[0].equipment)
    }

    @Test
    fun `addExercises carries the picked exercise's equipment into the ui model`() = runTest {
        val vm = newViewModel(exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))))
        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        assertEquals(Equipment.BARBELL, vm.uiState.value.exercises.single().equipment)
    }

    @Test
    fun `edit mode also exposes the plate calculator config`() = runTest {
        val vm = newViewModel(
            workoutRepo = editFixture(),
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            isEditMode = true,
        )
        assertTrue(vm.uiState.value.plateCalculator.enabled)
        assertEquals(Equipment.BARBELL, vm.uiState.value.exercises[0].equipment)
    }

    @Test
    fun `switching previous-values mode mid-session re-resolves the PREVIOUS column`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1", routineId = "r1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = null, reps = null, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
            statSetsByExercise = mapOf(
                "ex-1" to listOf(
                    // The most recent session of this exercise was outside the routine...
                    StatSet(setId = "any1", workoutId = "wAny", workoutStartedAt = 3_000L, orderIndex = 0, setType = SetType.NORMAL, weightKg = 80.0, reps = 8, durationSeconds = null, distanceMeters = null, customMetric = null, isCompleted = true, rpe = null, routineId = null),
                    // ...while the last same-routine session lifted less.
                    StatSet(setId = "same1", workoutId = "wSame", workoutStartedAt = 2_000L, orderIndex = 0, setType = SetType.NORMAL, weightKg = 50.0, reps = 5, durationSeconds = null, distanceMeters = null, customMetric = null, isCompleted = true, rpe = null, routineId = "r1"),
                ),
            ),
        )
        val settingsRepo = FakeSettingsRepository()
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo, settingsRepo = settingsRepo)
        assertEquals("80 kg x 8", vm.uiState.value.exercises[0].sets[0].previousLabel)

        settingsRepo.setPreviousValuesMode(PreviousValuesMode.SAME_ROUTINE)

        assertEquals("50 kg x 5", vm.uiState.value.exercises[0].sets[0].previousLabel)
    }

    @Test
    fun `updateRpe writes through immediately in live logging`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.updateRpe("we1", "s1", 8.5)

        assertEquals(8.5, vm.uiState.value.exercises[0].sets[0].rpe)
        assertEquals(8.5, workoutRepo.getSetsForWorkoutExercise("we1").single().rpe)
    }

    @Test
    fun `updateRpe can clear a value back to null, live`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = 7.0, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.updateRpe("we1", "s1", null)

        assertNull(vm.uiState.value.exercises[0].sets[0].rpe)
        assertNull(workoutRepo.getSetsForWorkoutExercise("we1").single().rpe)
    }

    @Test
    fun `updateRpe in edit mode is held in memory and does not touch Room until save`() = runTest {
        val workoutRepo = editFixture()
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            isEditMode = true,
        )

        vm.updateRpe("we1", "s1", 9.0)

        assertEquals(9.0, vm.uiState.value.exercises[0].sets[0].rpe)
        assertNull("nothing may reach Room before Save", workoutRepo.getSetsForWorkoutExercise("we1").single().rpe)

        vm.saveEdit()

        assertEquals(9.0, workoutRepo.getSetsForWorkoutExercise("we1").single().rpe)
    }

        // --- M5b edit mode (§5.1.10) ---

    @Test
    fun `edit mode holds every change in memory and writes nothing until save`() = runTest {
        // The defining difference from live logging, which is write-through on every keystroke.
        val workoutRepo = editFixture()
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            isEditMode = true,
        )

        vm.updateWeight("we1", "s1", 120.0)
        vm.updateReps("we1", "s1", 3)

        assertEquals(120.0, vm.uiState.value.exercises[0].sets[0].weightKg) // shown
        val stored = workoutRepo.getSetsForWorkoutExercise("we1").single()
        assertEquals("nothing may reach Room before Save", 100.0, stored.weightKg!!, 1e-9)
        assertEquals(5, stored.reps)
    }

    @Test
    fun `saving an edit persists the in-memory values in one go`() = runTest {
        val workoutRepo = editFixture()
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            isEditMode = true,
        )

        vm.updateWeight("we1", "s1", 120.0)
        vm.updateEditedDuration(2700)
        vm.saveEdit()

        val stored = workoutRepo.getSetsForWorkoutExercise("we1").single()
        assertEquals(120.0, stored.weightKg!!, 1e-9)
        assertEquals(2700, workoutRepo.getById("w1")!!.durationSeconds)
        assertEquals(WorkoutStatus.COMPLETED, workoutRepo.getById("w1")!!.status)
        assertTrue(vm.editSaveState.value is EditSaveState.Saved)
    }

    @Test
    fun `edit mode seeds the date and duration from the workout being edited`() = runTest {
        val vm = newViewModel(
            workoutRepo = editFixture(),
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            isEditMode = true,
        )

        assertTrue(vm.uiState.value.isEditMode)
        assertEquals(5_000L, vm.uiState.value.editedStartedAtMillis)
        assertEquals(1800, vm.uiState.value.editedDurationSeconds)
    }

    @Test
    fun `removing the last exercise blocks save`() = runTest {
        // §5.1.10: "Removing every exercise blocks Save ('Delete the workout instead')."
        val workoutRepo = editFixture()
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            isEditMode = true,
        )
        assertTrue(vm.uiState.value.canSaveEdit)

        vm.removeExercise("we1")

        assertFalse(vm.uiState.value.canSaveEdit)
        vm.saveEdit()
        assertTrue("a blocked save must not run", vm.editSaveState.value is EditSaveState.Idle)
    }

    @Test
    fun `edit mode counts the sets a save would discard`() = runTest {
        val vm = newViewModel(
            workoutRepo = editFixture(
                sets = listOf(
                    WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L),
                    WorkoutSetEntity(id = "s2", workoutExerciseId = "we1", orderIndex = 1, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
                ),
            ),
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            isEditMode = true,
        )

        assertEquals(1, vm.uncompletedSetCount())
    }

    @Test
    fun `un-checking a set in edit mode does not persist either`() = runTest {
        val workoutRepo = editFixture()
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            isEditMode = true,
        )

        vm.toggleCheck("we1", "s1")

        assertFalse(vm.uiState.value.exercises[0].sets[0].isCompleted)
        assertTrue("Room still holds the completed set until Save", workoutRepo.getSetsForWorkoutExercise("we1").single().isCompleted)
    }

    // --- M5b review regressions: the persist seam must be exhaustive ---

    @Test
    fun `replacing an exercise in edit mode does not touch Room before save`() = runTest {
        // The worst of the seam's holes: replaceWorkoutExerciseExercise rewrites the exercise id AND
        // flips every set to uncompleted, so a bypass corrupted a COMPLETED workout on pure Discard.
        val workoutRepo = editFixture()
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Incline Press"))),
            isEditMode = true,
        )

        vm.replaceExercise("we1", exercise("ex-2", "Incline Press"))

        assertEquals("ex-2", vm.uiState.value.exercises[0].exerciseId) // shown
        assertEquals("ex-1", workoutRepo.getExercisesForWorkout("w1").single().exerciseId) // not persisted
        assertTrue("the completed set must not be flipped", workoutRepo.getSetsForWorkoutExercise("we1").single().isCompleted)
    }

    @Test
    fun `adding an exercise in edit mode does not touch Room before save`() = runTest {
        val workoutRepo = editFixture()
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Squat"))),
            isEditMode = true,
        )

        vm.addExercises(listOf(exercise("ex-2", "Squat")))

        assertEquals(2, vm.uiState.value.exercises.size)
        assertEquals(1, workoutRepo.getExercisesForWorkout("w1").size)
    }

    @Test
    fun `adding a set in edit mode does not touch Room before save`() = runTest {
        val workoutRepo = editFixture()
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            isEditMode = true,
        )

        vm.addSet("we1")

        assertEquals(2, vm.uiState.value.exercises[0].sets.size)
        assertEquals(1, workoutRepo.getSetsForWorkoutExercise("we1").size)
    }

    @Test
    fun `building a superset in edit mode does not touch Room before save`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(aCompletedWorkout("w1")),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                WorkoutExerciseEntity(id = "we2", workoutId = "w1", exerciseId = "ex-2", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = listOf(
                WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L),
                WorkoutSetEntity(id = "s2", workoutExerciseId = "we2", orderIndex = 0, setType = SetType.NORMAL, weightKg = 50.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L),
            ),
        )
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench"), exercise("ex-2", "Row"))),
            isEditMode = true,
        )

        vm.startSupersetSelection("we1")
        vm.confirmSupersetTarget("we2")

        assertNotNull(vm.uiState.value.exercises[0].supersetGroup)
        assertTrue("no superset_group may reach Room before Save", workoutRepo.getExercisesForWorkout("w1").all { it.supersetGroup == null })
    }

    @Test
    fun `saving an edit preserves each set's original completedAt`() = runTest {
        // The UI model used to drop completed_at, so every save wrote is_completed = 1 with a null
        // timestamp and quietly removed the workout from the Library's recently-logged tier.
        val workoutRepo = editFixture(
            sets = listOf(
                WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 7_777L),
            ),
        )
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            isEditMode = true,
        )

        vm.updateWeight("we1", "s1", 110.0)
        vm.saveEdit()

        assertEquals(7_777L, workoutRepo.getSetsForWorkoutExercise("we1").single().completedAt)
    }

    @Test
    fun `a set first checked during an edit is stamped inside the workout, not at the current clock`() = runTest {
        val workoutRepo = editFixture(
            sets = listOf(
                WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L),
                WorkoutSetEntity(id = "s2", workoutExerciseId = "we1", orderIndex = 1, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            ),
        )
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            clock = FakeClock(currentMillis = 9_000_000L),
            isEditMode = true,
        )

        vm.toggleCheck("we1", "s2") // check the previously-unchecked set
        vm.saveEdit()

        val saved = workoutRepo.getSetsForWorkoutExercise("we1").first { it.id == "s2" }
        assertEquals("stamped with the workout's own start, not today", 5_000L, saved.completedAt)
    }

    @Test
    fun `unchecking every set blocks save rather than emptying the workout`() = runTest {
        // Purging on save would otherwise leave a COMPLETED workout with zero exercises — the state
        // the finish flow explicitly refuses to create.
        val workoutRepo = editFixture()
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            isEditMode = true,
        )
        assertTrue(vm.uiState.value.canSaveEdit)

        vm.toggleCheck("we1", "s1") // now nothing is completed

        assertFalse(vm.uiState.value.canSaveEdit)
        vm.saveEdit()
        assertTrue(vm.editSaveState.value is EditSaveState.Idle)
        assertEquals(1, workoutRepo.getExercisesForWorkout("w1").size)
    }

    private fun anInProgressWorkout(id: String, routineId: String? = null, startedAt: Long = 5_000L) = WorkoutEntity(
        id = id, routineId = routineId, title = "Push Day", notes = null, status = WorkoutStatus.IN_PROGRESS,
        startedAt = startedAt, endedAt = null, durationSeconds = 0, createdAt = startedAt, updatedAt = startedAt,
    )

    @Test
    fun `loads exercises and sets from the workout, sorted by orderIndex`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        val state = vm.uiState.value
        assertEquals("Push Day", state.title)
        assertEquals(1, state.exercises.size)
        assertEquals("Bench Press", state.exercises[0].exerciseName)
        assertEquals(60.0, state.exercises[0].sets[0].weightKg)
        assertEquals(8, state.exercises[0].sets[0].reps)
    }

    @Test
    fun `toggleCheck marks a normal set completed and persists it`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo, clock = FakeClock(currentMillis = 20_000L))

        val accepted = vm.toggleCheck("we1", "s1")

        assertTrue(accepted)
        assertTrue(vm.uiState.value.exercises[0].sets[0].isCompleted)
        val persisted = workoutRepo.getSetsForWorkoutExercise("we1").single()
        assertTrue(persisted.isCompleted)
        assertEquals(20_000L, persisted.completedAt)
    }

    @Test
    fun `toggleCheck rejects a FAILURE set with zero or blank reps`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.FAILURE, weightKg = 60.0, reps = null, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        val accepted = vm.toggleCheck("we1", "s1")

        assertFalse(accepted)
        assertFalse(vm.uiState.value.exercises[0].sets[0].isCompleted)
        assertTrue(vm.uiState.value.exercises[0].sets[0].failureError)
        assertFalse(workoutRepo.getSetsForWorkoutExercise("we1").single().isCompleted)
    }

    @Test
    fun `editing weight does not disturb the set's orderIndex or completion state`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(
                WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 999L),
                WorkoutSetEntity(id = "s2", workoutExerciseId = "we1", orderIndex = 1, setType = SetType.NORMAL, weightKg = 65.0, reps = 6, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            ),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.updateWeight("we1", "s2", 70.0)

        val s1 = workoutRepo.getSetsForWorkoutExercise("we1").first { it.id == "s1" }
        val s2 = workoutRepo.getSetsForWorkoutExercise("we1").first { it.id == "s2" }
        assertTrue(s1.isCompleted) // untouched set's completion survives editing a sibling
        assertEquals(999L, s1.completedAt)
        assertEquals(1, s2.orderIndex) // the edited set's own position is untouched
        assertEquals(70.0, s2.weightKg)
    }

    @Test
    fun `addSet copies the previous set's values with a fresh id`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.addSet("we1")

        val sets = vm.uiState.value.exercises[0].sets
        assertEquals(2, sets.size)
        assertEquals(60.0, sets[1].weightKg)
        assertEquals(8, sets[1].reps)
        assertFalse(sets[1].isCompleted) // new row starts uncompleted even though copied from a completed one
    }

    @Test
    fun `replaceExercise carries over shared fields, clears the rest, and uncompletes every set`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press", ExerciseType.WEIGHT_REPS)))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.replaceExercise("we1", exercise("ex-2", "Plank", ExerciseType.WEIGHT_DURATION))

        val updated = vm.uiState.value.exercises[0]
        assertEquals("ex-2", updated.exerciseId)
        assertEquals(60.0, updated.sets[0].weightKg) // WEIGHT shared by both types
        assertNull(updated.sets[0].reps) // REPS dropped by the new type
        assertFalse(updated.sets[0].isCompleted) // §5.1.9: completed sets are discarded on replace, not re-attributed

        val persisted = workoutRepo.getSetsForWorkoutExercise("we1").single()
        assertFalse(persisted.isCompleted)
        assertNull(persisted.completedAt)
        assertEquals("ex-2", workoutRepo.getExercisesForWorkout("w1").single().exerciseId)
    }

    @Test
    fun `superset grouping and removal cleans up orphaned solo groups`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Incline Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                WorkoutExerciseEntity(id = "we2", workoutId = "w1", exerciseId = "ex-2", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.startSupersetSelection("we1")
        vm.confirmSupersetTarget("we2")
        val grouped = vm.uiState.value.exercises
        assertNotNull(grouped[0].supersetGroup)
        assertEquals(grouped[0].supersetGroup, grouped[1].supersetGroup)

        vm.removeExercise("we2")
        val remaining = vm.uiState.value.exercises.single()
        assertNull(remaining.supersetGroup) // orphaned solo member cleaned up
        assertNull(workoutRepo.getExercisesForWorkout("w1").single().supersetGroup)
    }

    @Test
    fun `reorderExercises persists the new order`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Incline Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                WorkoutExerciseEntity(id = "we2", workoutId = "w1", exerciseId = "ex-2", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.reorderExercises(listOf("we2", "we1"))

        assertEquals(listOf("we2", "we1"), vm.uiState.value.exercises.map { it.id })
        assertEquals(0, workoutRepo.getExercisesForWorkout("w1").first { it.id == "we2" }.orderIndex)
        assertEquals(1, workoutRepo.getExercisesForWorkout("w1").first { it.id == "we1" }.orderIndex)
    }

    @Test
    fun `reorderExercises appends any exercise the caller's id list omits, rather than dropping it`() = runTest {
        // M20a: the screen sources the id list from an optimistic copy that can be a stale or
        // partial snapshot (e.g. taken before an exercise was added mid-drag) -- an omitted id must
        // never vanish from the workout.
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Incline Press"), exercise("ex-3", "Row")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                WorkoutExerciseEntity(id = "we2", workoutId = "w1", exerciseId = "ex-2", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
                WorkoutExerciseEntity(id = "we3", workoutId = "w1", exerciseId = "ex-3", orderIndex = 2, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.reorderExercises(listOf("we3", "we1")) // we2 omitted

        assertEquals(listOf("we3", "we1", "we2"), vm.uiState.value.exercises.map { it.id })
        assertEquals(3, workoutRepo.getExercisesForWorkout("w1").size)
        assertEquals(2, workoutRepo.getExercisesForWorkout("w1").first { it.id == "we2" }.orderIndex)
    }

    @Test
    fun `reorderExercises in edit mode holds the new order in memory and writes nothing until save, which stamps it from list position`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(aCompletedWorkout("w1")),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                WorkoutExerciseEntity(id = "we2", workoutId = "w1", exerciseId = "ex-2", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = listOf(
                WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L),
                WorkoutSetEntity(id = "s2", workoutExerciseId = "we2", orderIndex = 0, setType = SetType.NORMAL, weightKg = 50.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L),
            ),
        )
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Incline Press"))),
            isEditMode = true,
        )

        vm.reorderExercises(listOf("we2", "we1"))

        assertEquals(listOf("we2", "we1"), vm.uiState.value.exercises.map { it.id })
        assertEquals("nothing may reach Room before Save", 0, workoutRepo.getExercisesForWorkout("w1").first { it.id == "we1" }.orderIndex)
        assertEquals(1, workoutRepo.getExercisesForWorkout("w1").first { it.id == "we2" }.orderIndex)

        vm.saveEdit()

        assertEquals(0, workoutRepo.getExercisesForWorkout("w1").first { it.id == "we2" }.orderIndex)
        assertEquals(1, workoutRepo.getExercisesForWorkout("w1").first { it.id == "we1" }.orderIndex)
    }

    @Test
    fun `prepareForFinish freezes the duration but leaves the workout IN_PROGRESS for the Save screen`() = runTest {
        val clock = FakeClock(currentMillis = 10_000L)
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1", startedAt = 10_000L)))
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        sessionController.startSession("w1")
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo, clock = clock, sessionController = sessionController)

        clock.currentMillis = 70_000L
        val ok = vm.prepareForFinish()

        assertTrue(ok)
        val workout = workoutRepo.getById("w1")!!
        assertEquals(60, workout.durationSeconds) // 60s of session-controller elapsed time
        // §5.1.8: nothing is finalized until the Save screen's transaction — killing the app here
        // must leave a recoverable IN_PROGRESS workout, not a half-completed one.
        assertEquals(WorkoutStatus.IN_PROGRESS, workout.status)
        assertNull(workout.endedAt)
    }

    @Test
    fun `discard deletes the workout entirely`() = runTest {
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1")))
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo)

        vm.discard()

        assertNull(workoutRepo.getById("w1"))
    }

    @Test
    fun `addExercises auto-fills from the previous COMPLETED session when one exists`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val previous = mapOf(
            "ex-1" to listOf(
                StatSet(setId = "old1", workoutId = "wOld", workoutStartedAt = 1L, orderIndex = 0, setType = SetType.NORMAL, weightKg = 55.0, reps = 9, durationSeconds = null, distanceMeters = null, customMetric = null, isCompleted = true, rpe = null, routineId = null),
            ),
        )
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1")), statSetsByExercise = previous)
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))

        val added = vm.uiState.value.exercises.single()
        assertEquals(55.0, added.sets[0].weightKg)
        assertEquals(9, added.sets[0].reps)
        assertFalse(added.sets[0].isCompleted)
    }

    @Test
    fun `exercise added to an empty-start workout gets one blank row while keeping previous context`() = runTest {
        val clock = FakeClock(currentMillis = 10_000L)
        val controller = WorkoutSessionController(
            FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()),
        )
        controller.startSession("w1", waitForFirstExercise = true)
        val previous = mapOf(
            "ex-1" to listOf(
                StatSet(setId = "old1", workoutId = "old", workoutStartedAt = 1L, orderIndex = 0, setType = SetType.NORMAL, weightKg = 55.0, reps = 9, durationSeconds = null, distanceMeters = null, customMetric = null, isCompleted = true, rpe = 8.0, routineId = null),
                StatSet(setId = "old2", workoutId = "old", workoutStartedAt = 1L, orderIndex = 1, setType = SetType.NORMAL, weightKg = 60.0, reps = 7, durationSeconds = null, distanceMeters = null, customMetric = null, isCompleted = true, rpe = 9.0, routineId = null),
            ),
        )
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            statSetsByExercise = previous,
        )
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            clock = clock,
            sessionController = controller,
        )

        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))

        val set = vm.uiState.value.exercises.single().sets.single()
        assertNull(set.weightKg)
        assertNull(set.reps)
        assertEquals("55 kg x 9", set.previousLabel)
        assertEquals(1, workoutRepo.getSetsForWorkoutExercise(vm.uiState.value.exercises.single().id).size)
    }

    @Test
    fun `empty workout timer starts on first exercise and resets when that exercise is replaced during grace`() = runTest {
        val clock = FakeClock(currentMillis = 10_000L)
        val controller = WorkoutSessionController(
            FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()),
        )
        controller.startSession("w1", waitForFirstExercise = true)
        val vm = newViewModel(
            clock = clock,
            sessionController = controller,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Squat"))),
        )

        clock.currentMillis = 70_000L
        assertEquals(0L, controller.elapsedSeconds())
        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        clock.currentMillis = 80_000L
        assertEquals(10L, controller.elapsedSeconds())

        val startingBlockId = vm.uiState.value.exercises.single().id
        vm.replaceExercise(startingBlockId, exercise("ex-2", "Squat"))

        assertEquals(0L, controller.elapsedSeconds())
        assertFalse(controller.state.value.isPaused)
    }

    @Test
    fun `empty workout grace no longer resets the timer after five minutes`() = runTest {
        val clock = FakeClock(currentMillis = 0L)
        val controller = WorkoutSessionController(
            FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()),
        )
        controller.startSession("w1", waitForFirstExercise = true)
        val vm = newViewModel(
            clock = clock,
            sessionController = controller,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Squat"))),
        )
        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        clock.currentMillis = 300_000L

        val startingBlockId = vm.uiState.value.exercises.single().id
        vm.replaceExercise(startingBlockId, exercise("ex-2", "Squat"))

        assertEquals(300L, controller.elapsedSeconds())
    }

    @Test
    fun `removing the only starting exercise during grace resets and pauses until another is added`() = runTest {
        val clock = FakeClock(currentMillis = 0L)
        val controller = WorkoutSessionController(
            FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()),
        )
        controller.startSession("w1", waitForFirstExercise = true)
        val vm = newViewModel(clock = clock, sessionController = controller)
        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        clock.currentMillis = 40_000L

        vm.removeExercise(vm.uiState.value.exercises.single().id)
        clock.currentMillis = 90_000L

        assertEquals(0L, controller.elapsedSeconds())
        assertTrue(controller.state.value.isPaused)
    }

    @Test
    fun `addExercises seeds one blank set when the exercise has never been logged`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val vm = newViewModel(exerciseRepo = exerciseRepo)

        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))

        val added = vm.uiState.value.exercises.single()
        assertEquals(1, added.sets.size)
        assertNull(added.sets[0].weightKg)
    }

    @Test
    fun `toggleCheck completing a set starts a rest timer end-to-end through the session controller`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = 60, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo)

        vm.toggleCheck("we1", "s1")

        assertEquals(60_000L, vm.restRemainingMillisFlow.first())
    }

    @Test
    fun `togglePause pauses and resumes the session's elapsed timer`() = runTest {
        val clock = FakeClock(currentMillis = 0L)
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1", startedAt = 0L)))
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        sessionController.startSession("w1")
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo, clock = clock, sessionController = sessionController)

        clock.currentMillis = 20_000L
        vm.togglePause()
        assertTrue(vm.uiState.value.isPaused)
        clock.currentMillis = 999_000L // must not count while paused
        assertEquals(20L, vm.elapsedSecondsFlow.first())

        vm.togglePause()
        assertFalse(vm.uiState.value.isPaused)
    }

    @Test
    fun `prepareForFinish keeps the stored duration when no live session is tracking this workout`() = runTest {
        // Regression: re-entering Finish after a previous attempt (which already ended the
        // session) read the empty session as 0s and clobbered the real duration with zero.
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1", startedAt = 10_000L).copy(durationSeconds = 1800)),
        )
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        // Deliberately NOT started — mirrors a session already ended by an earlier Finish.
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo, sessionController = sessionController)

        vm.prepareForFinish()

        assertEquals(1800, workoutRepo.getById("w1")!!.durationSeconds)
    }

    @Test
    fun `a second Finish after backing out of the Save screen keeps the duration the first one stored`() = runTest {
        // The real repro the previous test missed by pre-seeding the duration: it is the FIRST
        // prepareForFinish that stores 2700s, and the second call — same ViewModel instance,
        // reached by backing out of the Save screen — that used to overwrite it with the
        // durationSeconds = 0 the stale in-memory entity still carried from init.
        val clock = FakeClock(currentMillis = 10_000L)
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1", startedAt = 10_000L)))
        val sessionController = WorkoutSessionController(
            FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()),
        )
        sessionController.startSession("w1")
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo, clock = clock, sessionController = sessionController)

        clock.currentMillis = 10_000L + 2_700_000L // 45 minutes of training
        vm.prepareForFinish()
        assertEquals(2700, workoutRepo.getById("w1")!!.durationSeconds)

        // Back on the Logger, tap Finish again. endSession() already ran, so there is no live
        // session and the stored value must survive.
        vm.prepareForFinish()

        assertEquals(2700, workoutRepo.getById("w1")!!.durationSeconds)
    }

    @Test
    fun `prepareForFinish clears the session so the mini-bar and service both stand down`() = runTest {
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1", startedAt = 10_000L)))
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(currentMillis = 70_000L), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        sessionController.startSession("w1")
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo, clock = FakeClock(currentMillis = 70_000L), sessionController = sessionController)

        vm.prepareForFinish()

        assertNull(sessionController.state.value.workoutId)
    }

    @Test
    fun `discard clears the session so the mini-bar and service both stand down`() = runTest {
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1")))
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        sessionController.startSession("w1")
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo, sessionController = sessionController)

        vm.discard()

        assertNull(sessionController.state.value.workoutId)
    }

    @Test
    fun `discard clears the running rest timer before deleting, so it can't fire for a gone workout`() = runTest {
        val workoutRepo = FakeWorkoutRepository(workouts = listOf(anInProgressWorkout("w1")))
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        sessionController.startSession("w1")
        sessionController.startRestTimer("we1", 90)
        val vm = newViewModel(workoutId = "w1", workoutRepo = workoutRepo, sessionController = sessionController)

        vm.discard()

        assertNull(sessionController.state.value.restDeadlineElapsedRealtimeMillis)
        assertNull(workoutRepo.getById("w1"))
    }

    @Test
    fun `completing a set stops and commits its running inline timer instead of orphaning it`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Plank", ExerciseType.DURATION)))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = 0, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = null, reps = null, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val elapsedClock = FakeElapsedRealtimeClock(currentMillis = 0L)
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), elapsedClock, CoroutineScope(UnconfinedTestDispatcher()))
        sessionController.startSession("w1")
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo, sessionController = sessionController)

        vm.startInlineTimer("we1", "s1")
        elapsedClock.currentMillis = 42_000L
        vm.toggleCheck("we1", "s1")

        // Without the stop-on-complete, the timer would keep ticking with no UI control left to
        // stop it (the play/pause button only renders for uncompleted sets) and its elapsed value
        // would never reach durationSeconds.
        assertNull(sessionController.state.value.inlineTimer)
        assertEquals(42, workoutRepo.getSetsForWorkoutExercise("we1").single().durationSeconds)
    }

    // --- M11 circuit mode ---

    private fun aCircuitWorkout(id: String) = anInProgressWorkout(id).copy(structure = WorkoutStructure.CIRCUIT)

    private fun circuitFixture(): FakeWorkoutRepository = FakeWorkoutRepository(
        workouts = listOf(aCircuitWorkout("w1")),
        exercises = listOf(
            WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
            WorkoutExerciseEntity(id = "we2", workoutId = "w1", exerciseId = "ex-2", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
        ),
        sets = listOf(
            WorkoutSetEntity(id = "a0", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            WorkoutSetEntity(id = "a1", workoutExerciseId = "we1", orderIndex = 1, setType = SetType.NORMAL, weightKg = 65.0, reps = 6, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            WorkoutSetEntity(id = "b0", workoutExerciseId = "we2", orderIndex = 0, setType = SetType.NORMAL, weightKg = 20.0, reps = 12, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            WorkoutSetEntity(id = "b1", workoutExerciseId = "we2", orderIndex = 1, setType = SetType.NORMAL, weightKg = 22.5, reps = 10, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
        ),
    )

    private fun circuitExerciseRepo() = FakeExerciseRepository(listOf(exercise("ex-1", "Kettlebell Swing"), exercise("ex-2", "Goblet Squat")))

    @Test
    fun `a workout started from a circuit routine reports CIRCUIT structure in the ui state`() = runTest {
        val vm = newViewModel(workoutRepo = circuitFixture(), exerciseRepo = circuitExerciseRepo())
        assertEquals(WorkoutStructure.CIRCUIT, vm.uiState.value.structure)
    }

    @Test
    fun `addRound appends one row to EVERY exercise, seeded from that exercise's previous round, and persists contiguous orderIndex`() = runTest {
        val workoutRepo = circuitFixture()
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = circuitExerciseRepo())

        vm.addRound()

        val state = vm.uiState.value.exercises
        assertEquals(listOf(3, 3), state.map { it.sets.size })
        assertEquals(65.0, state[0].sets[2].weightKg) // copied from we1's round 2
        assertEquals(6, state[0].sets[2].reps)
        assertEquals(22.5, state[1].sets[2].weightKg) // copied from we2's round 2
        assertFalse(state[1].sets[2].isCompleted)

        val persisted1 = workoutRepo.getSetsForWorkoutExercise("we1")
        val persisted2 = workoutRepo.getSetsForWorkoutExercise("we2")
        assertEquals(listOf(0, 1, 2), persisted1.map { it.orderIndex })
        assertEquals(listOf(0, 1, 2), persisted2.map { it.orderIndex })
        assertEquals(65.0, persisted1[2].weightKg)
    }

    @Test
    fun `removeRound drops that round's row from every exercise and re-indexes later rounds contiguously`() = runTest {
        val workoutRepo = circuitFixture()
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = circuitExerciseRepo())

        vm.removeRound(0)

        val state = vm.uiState.value.exercises
        assertEquals(listOf(1, 1), state.map { it.sets.size })
        assertEquals("a1", state[0].sets[0].id)
        assertEquals("b1", state[1].sets[0].id)

        // Persisted: round 1's rows gone, the survivors re-homed at orderIndex 0.
        val persisted1 = workoutRepo.getSetsForWorkoutExercise("we1").single()
        val persisted2 = workoutRepo.getSetsForWorkoutExercise("we2").single()
        assertEquals("a1", persisted1.id)
        assertEquals(0, persisted1.orderIndex)
        assertEquals("b1", persisted2.id)
        assertEquals(0, persisted2.orderIndex)
    }

    @Test
    fun `removeRound with unequal set counts only touches exercises that actually have the round`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(aCircuitWorkout("w1")),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                WorkoutExerciseEntity(id = "we2", workoutId = "w1", exerciseId = "ex-2", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = listOf(
                WorkoutSetEntity(id = "a0", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
                WorkoutSetEntity(id = "a1", workoutExerciseId = "we1", orderIndex = 1, setType = SetType.NORMAL, weightKg = 65.0, reps = 6, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
                WorkoutSetEntity(id = "b0", workoutExerciseId = "we2", orderIndex = 0, setType = SetType.NORMAL, weightKg = 20.0, reps = 12, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            ),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = circuitExerciseRepo())

        vm.removeRound(1) // we2 has no round 2 — must not crash or disturb its single row

        assertEquals(listOf(1, 1), vm.uiState.value.exercises.map { it.sets.size })
        assertEquals("a0", workoutRepo.getSetsForWorkoutExercise("we1").single().id)
        assertEquals("b0", workoutRepo.getSetsForWorkoutExercise("we2").single().id)
    }

    @Test
    fun `roundHasLoggedValues is true when any exercise's row at that index has a logged field`() = runTest {
        val vm = newViewModel(workoutRepo = circuitFixture(), exerciseRepo = circuitExerciseRepo())

        // Both rounds in circuitFixture have real weight/reps logged (isCompleted is false, but
        // the OR-chain checks every field independently -- see the class doc comment).
        assertTrue(vm.roundHasLoggedValues(0))
        assertTrue(vm.roundHasLoggedValues(1))
    }

    @Test
    fun `roundHasLoggedValues is false when every exercise's row at that index is fully blank`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(aCircuitWorkout("w1")),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                WorkoutExerciseEntity(id = "we2", workoutId = "w1", exerciseId = "ex-2", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = listOf(
                WorkoutSetEntity(id = "a0", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = null, reps = null, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
                WorkoutSetEntity(id = "b0", workoutExerciseId = "we2", orderIndex = 0, setType = SetType.NORMAL, weightKg = null, reps = null, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            ),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = circuitExerciseRepo())

        assertFalse(vm.roundHasLoggedValues(0))
    }

    @Test
    fun `roundHasLoggedValues is true when a row is completed even with every numeric field null`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(aCircuitWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(
                WorkoutSetEntity(id = "a0", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = null, reps = null, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1_000L),
            ),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = circuitExerciseRepo())

        assertTrue(vm.roundHasLoggedValues(0))
    }

    @Test
    fun `roundHasLoggedValues on an out-of-range index is false, not a crash`() = runTest {
        val vm = newViewModel(workoutRepo = circuitFixture(), exerciseRepo = circuitExerciseRepo())

        assertFalse(vm.roundHasLoggedValues(99))
    }

    @Test
    fun `addExercises mid-circuit gives the newcomer exactly one row per existing round`() = runTest {
        val workoutRepo = circuitFixture()
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = circuitExerciseRepo())

        vm.addExercises(listOf(exercise("ex-3", "Burpee")))

        val added = vm.uiState.value.exercises.last()
        assertEquals("ex-3", added.exerciseId)
        assertEquals(2, added.sets.size) // matches the circuit's current round count
        val persisted = workoutRepo.getSetsForWorkoutExercise(added.id)
        assertEquals(listOf(0, 1), persisted.map { it.orderIndex })
    }

    @Test
    fun `addExercises mid-circuit auto-fills each round from PREVIOUS where a matching round exists`() = runTest {
        val previous = mapOf(
            "ex-3" to listOf(
                StatSet(setId = "old1", workoutId = "wOld", workoutStartedAt = 1L, orderIndex = 0, setType = SetType.NORMAL, weightKg = 40.0, reps = 10, durationSeconds = null, distanceMeters = null, customMetric = null, isCompleted = true, rpe = null, routineId = null),
            ),
        )
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(aCircuitWorkout("w1")),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = listOf(
                WorkoutSetEntity(id = "a0", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 60.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
                WorkoutSetEntity(id = "a1", workoutExerciseId = "we1", orderIndex = 1, setType = SetType.NORMAL, weightKg = 65.0, reps = 6, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            ),
            statSetsByExercise = previous,
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = circuitExerciseRepo())

        vm.addExercises(listOf(exercise("ex-3", "Burpee")))

        val added = vm.uiState.value.exercises.last()
        assertEquals(2, added.sets.size)
        assertEquals(40.0, added.sets[0].weightKg) // round 1 auto-filled from PREVIOUS
        assertNull(added.sets[1].weightKg) // no matching previous round — blank, not invented
    }

    @Test
    fun `addSet and removeSet are inert in circuit mode - set counts move only via rounds`() = runTest {
        val workoutRepo = circuitFixture()
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = circuitExerciseRepo())

        vm.addSet("we1")
        vm.removeSet("we1", "a0")

        assertEquals(listOf(2, 2), vm.uiState.value.exercises.map { it.sets.size })
        assertEquals(2, workoutRepo.getSetsForWorkoutExercise("we1").size)
    }

    @Test
    fun `WARMUP is refused in circuit mode but other set types still apply`() = runTest {
        val workoutRepo = circuitFixture()
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = circuitExerciseRepo())

        vm.updateSetType("we1", "a0", SetType.WARMUP)
        assertEquals(SetType.NORMAL, vm.uiState.value.exercises[0].sets[0].setType)

        vm.updateSetType("we1", "a0", SetType.DROPSET)
        assertEquals(SetType.DROPSET, vm.uiState.value.exercises[0].sets[0].setType)
        assertEquals(SetType.DROPSET, workoutRepo.getSetsForWorkoutExercise("we1").first { it.id == "a0" }.setType)
    }

    @Test
    fun `superset selection is refused in circuit mode`() = runTest {
        val vm = newViewModel(workoutRepo = circuitFixture(), exerciseRepo = circuitExerciseRepo())

        vm.startSupersetSelection("we1")

        assertFalse(vm.uiState.value.supersetSelectionActive)
    }

    @Test
    fun `checking a circuit row scrolls to the next incomplete row of the same round, wrapping into the next round`() = runTest {
        val vm = newViewModel(workoutRepo = circuitFixture(), exerciseRepo = circuitExerciseRepo())
        val scrolled = mutableListOf<Int>()
        val job = launch(UnconfinedTestDispatcher()) { vm.scrollToRound.collect { scrolled.add(it) } }

        vm.toggleCheck("we1", "a0") // round 1, first exercise -> b0 still open in round 1
        vm.toggleCheck("we2", "b0") // round 1 done -> round 2

        assertEquals(listOf(0, 1), scrolled)
        job.cancel()
    }

    @Test
    fun `deleting a set clears its running inline timer instead of leaving a dangling pointer`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Plank", ExerciseType.DURATION)))
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(anInProgressWorkout("w1")),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = null, reps = null, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null)),
        )
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        sessionController.startSession("w1")
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = exerciseRepo, sessionController = sessionController)

        vm.startInlineTimer("we1", "s1")
        vm.removeSet("we1", "s1")

        assertNull(sessionController.state.value.inlineTimer)
    }

    // --- M18 §5.1.6 Warm-up Calculator: "Add warm-up sets" ---

    private fun warmupFixture(
        sets: List<WorkoutSetEntity> = listOf(
            WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
        ),
    ): FakeWorkoutRepository = FakeWorkoutRepository(
        workouts = listOf(anInProgressWorkout("w1")),
        exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
        sets = sets,
    )

    @Test
    fun `addWarmupSets prepends the default 40-60-80 ladder above set 1 and re-indexes, write-through`() = runTest {
        // Barbell, 100 kg working weight, default method and equipment → literally 40/60/80 kg
        // (each exactly plate-loadable on the default 20 kg bar — see WarmupCalculatorTest).
        val workoutRepo = warmupFixture()
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))))

        vm.addWarmupSets("we1")

        val sets = vm.uiState.value.exercises[0].sets
        assertEquals(listOf(SetType.WARMUP, SetType.WARMUP, SetType.WARMUP, SetType.NORMAL), sets.map { it.setType })
        assertEquals(listOf(40.0, 60.0, 80.0, 100.0), sets.map { it.weightKg })
        assertEquals(listOf(5, 5, 3, 5), sets.map { it.reps })
        assertFalse(sets[0].isCompleted)

        // Write-through: the ladder is persisted at orderIndex 0..2 and the original set re-homed at 3.
        val persisted = workoutRepo.getSetsForWorkoutExercise("we1").sortedBy { it.orderIndex }
        assertEquals(listOf(0, 1, 2, 3), persisted.map { it.orderIndex })
        assertEquals(listOf(40.0, 60.0, 80.0, 100.0), persisted.map { it.weightKg })
        assertEquals(listOf(SetType.WARMUP, SetType.WARMUP, SetType.WARMUP, SetType.NORMAL), persisted.map { it.setType })
        assertEquals("s1", persisted[3].id)
    }

    @Test
    fun `addWarmupSets takes the working weight from the first non-WARMUP set, skipping existing warm-ups`() = runTest {
        val workoutRepo = warmupFixture(
            sets = listOf(
                WorkoutSetEntity(id = "w0", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.WARMUP, weightKg = 50.0, reps = 10, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
                WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 1, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            ),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))))

        vm.addWarmupSets("we1")

        // The ladder derives from 100 kg (the NORMAL set), never the 50 kg warm-up above it.
        val sets = vm.uiState.value.exercises[0].sets
        assertEquals(listOf(40.0, 60.0, 80.0, 50.0, 100.0), sets.map { it.weightKg })
    }

    @Test
    fun `re-invoking addWarmupSets prepends another ladder and keeps orderIndex contiguous`() = runTest {
        // §5.1.6 edge case: "Re-invoking does not delete previously inserted warm-ups (it just
        // prepends again — matching the 'insert' verb)".
        val workoutRepo = warmupFixture()
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))))

        vm.addWarmupSets("we1")
        vm.addWarmupSets("we1")

        val sets = vm.uiState.value.exercises[0].sets
        assertEquals(7, sets.size)
        assertEquals(listOf(40.0, 60.0, 80.0, 40.0, 60.0, 80.0, 100.0), sets.map { it.weightKg })
        val persisted = workoutRepo.getSetsForWorkoutExercise("we1").sortedBy { it.orderIndex }
        assertEquals((0..6).toList(), persisted.map { it.orderIndex })
        assertEquals(SetType.NORMAL, persisted[6].setType)
    }

    @Test
    fun `addWarmupSets is a no-op when no working set has a weight yet`() = runTest {
        val workoutRepo = warmupFixture(
            sets = listOf(
                WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = null, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            ),
        )
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))))

        vm.addWarmupSets("we1")

        assertEquals(1, vm.uiState.value.exercises[0].sets.size)
        assertEquals(1, workoutRepo.getSetsForWorkoutExercise("we1").size)
    }

    @Test
    fun `addWarmupSets is a no-op when the warm-up calculator setting is off`() = runTest {
        val workoutRepo = warmupFixture()
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            settingsRepo = FakeSettingsRepository(UserSettings(warmupCalculatorEnabled = false)),
        )

        vm.addWarmupSets("we1")

        assertFalse(vm.uiState.value.warmupCalculatorEnabled)
        assertEquals(1, vm.uiState.value.exercises[0].sets.size)
        assertEquals(1, workoutRepo.getSetsForWorkoutExercise("we1").size)
    }

    @Test
    fun `addWarmupSets is refused in circuit mode and the menu gate reads force-false there`() = runTest {
        // M11/M18 invariant: WARMUP rows would break row-index == round. The card never shows the
        // item (uiState gate) and the ViewModel refuses even a direct call (backstop).
        val workoutRepo = circuitFixture()
        val vm = newViewModel(workoutRepo = workoutRepo, exerciseRepo = circuitExerciseRepo())

        assertFalse("gate must be force-false in a circuit even with the setting on", vm.uiState.value.warmupCalculatorEnabled)
        vm.addWarmupSets("we1")

        assertEquals(listOf(2, 2), vm.uiState.value.exercises.map { it.sets.size })
        assertTrue(workoutRepo.getSetsForWorkoutExercise("we1").none { it.setType == SetType.WARMUP })
    }

    @Test
    fun `the warm-up gate is on for a regular workout when the setting is on`() = runTest {
        val vm = newViewModel(workoutRepo = warmupFixture(), exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))))
        assertTrue(vm.uiState.value.warmupCalculatorEnabled)
    }

    @Test
    fun `addWarmupSets on a non-barbell exercise rounds to the 2 point 5 kg grid`() = runTest {
        // Dumbbell, 41 kg working weight: 16.4 → 17.5, 24.6 → 25, 32.8 → 32.5 (hand-computed).
        val workoutRepo = warmupFixture(
            sets = listOf(
                WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 41.0, reps = 8, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = false, completedAt = null),
            ),
        )
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Dumbbell Press", equipment = Equipment.DUMBBELL))),
        )

        vm.addWarmupSets("we1")

        assertEquals(listOf(17.5, 25.0, 32.5, 41.0), vm.uiState.value.exercises[0].sets.map { it.weightKg })
    }

    @Test
    fun `addWarmupSets in edit mode is held in memory and does not touch Room until save`() = runTest {
        val workoutRepo = editFixture()
        val vm = newViewModel(
            workoutRepo = workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            isEditMode = true,
        )

        vm.addWarmupSets("we1")

        assertEquals(4, vm.uiState.value.exercises[0].sets.size) // shown
        assertEquals(1, workoutRepo.getSetsForWorkoutExercise("we1").size) // not persisted before Save
    }
}
