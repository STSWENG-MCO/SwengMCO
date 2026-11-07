package com.enil.logez.feature.workout.finish

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeTransactionRunner
import com.enil.logez.fakes.FakeWorkoutHeartRateSampleRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** §10.6's M4c row: "Finish-flow ViewModel tests (update-routine prompt logic, ...)". */
@OptIn(ExperimentalCoroutinesApi::class)
class FinishWorkoutViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads the workout's current title, duration and set counts`() = runTest {
        val vm = newViewModel(
            sets = listOf(
                set("s1", completed = true),
                set("s2", completed = false),
            ),
        )
        val state = vm.uiState.value
        assertEquals("Original Title", state.title)
        assertEquals(1, state.completedSetCount)
        assertEquals(1, state.incompleteSetCount)
        // §8.10: the Update Routine Values toggle defaults ON, per save.
        assertTrue(state.updateRoutineValues)
    }

    @Test
    fun `a workout with no routine never prompts about structure`() = runTest {
        val vm = newViewModel(withRoutine = false)
        assertFalse(vm.needsStructurePrompt())
    }

    @Test
    fun `matching structure does not prompt`() = runTest {
        val vm = newViewModel(withRoutine = true, routineSetCount = 1, sets = listOf(set("s1", completed = true)))
        assertFalse(vm.needsStructurePrompt())
    }

    @Test
    fun `an extra completed set prompts`() = runTest {
        val vm = newViewModel(
            withRoutine = true,
            routineSetCount = 1,
            sets = listOf(set("s1", completed = true), set("s2", completed = true, orderIndex = 1)),
        )
        assertTrue(vm.needsStructurePrompt())
    }

    @Test
    fun `an added-but-never-performed set does not prompt, because the save discards it anyway`() = runTest {
        // Regression: counting raw sets here would ask the user whether to restructure their
        // routine over a set that the save transaction is about to purge.
        val vm = newViewModel(
            withRoutine = true,
            routineSetCount = 1,
            sets = listOf(set("s1", completed = true), set("s2", completed = false, orderIndex = 1)),
        )
        assertFalse(vm.needsStructurePrompt())
    }

    @Test
    fun `an exercise where nothing was completed drops out of the comparison`() = runTest {
        // The purge removes its only set, and then the exercise itself — so the saved shape
        // matches a single-exercise routine.
        val vm = newViewModel(
            withRoutine = true,
            routineSetCount = 1,
            extraExercise = true,
            sets = listOf(set("s1", completed = true), set("s2", completed = false, workoutExerciseId = "we2")),
        )
        assertFalse(vm.needsStructurePrompt())
    }

    @Test
    fun `edits are held in memory and only applied on save`() = runTest {
        // 1800s stands in for the elapsed session time WorkoutLoggerViewModel.prepareForFinish()
        // freezes into the row before this screen opens — v4.0 made duration a read-only readout,
        // so the save must carry that value through untouched rather than take an edited one.
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout(durationSeconds = 1800)),
            exercises = listOf(workoutExercise()),
            sets = listOf(set("s1", completed = true)),
        )
        val vm = newViewModel(workoutRepo = workoutRepo)

        vm.updateTitle("Leg Day")

        // Nothing persisted yet — §5.1.8: killing the app here leaves a recoverable IN_PROGRESS workout.
        assertEquals("Original Title", workoutRepo.getById("w1")!!.title)
        assertEquals(WorkoutStatus.IN_PROGRESS, workoutRepo.getById("w1")!!.status)

        vm.save(structureChoice = null)

        assertEquals("Leg Day", workoutRepo.getById("w1")!!.title)
        assertEquals(1800, workoutRepo.getById("w1")!!.durationSeconds)
        assertEquals(WorkoutStatus.COMPLETED, workoutRepo.getById("w1")!!.status)
    }

    @Test
    fun `save is immediately retryable after a failure, without waiting for the error to be dismissed`() = runTest {
        // Regression: save()/discard() used to require saveState == Idle to start, but a failed
        // attempt only reaches Idle once the screen's error snackbar finishes showing (~4s later,
        // via clearSaveError()). In that window the Save button looked enabled but every tap here
        // was silently swallowed — including the user's very next attempt to save.
        val runner = FakeTransactionRunner(failNextCalls = 1)
        val vm = newViewModel(runner = runner)

        vm.save(structureChoice = null)
        assertEquals(SaveState.Failed, vm.saveState.value)

        // The screen hasn't called clearSaveError() yet — the snackbar is still showing — but the
        // user taps Save again anyway. This must not be a no-op.
        vm.save(structureChoice = null)

        assertTrue("a retry right after a failure must actually run, not be swallowed", vm.saveState.value is SaveState.Saved)
        assertEquals(2, runner.transactionCount)
    }

    // --- fixture ---

    private fun newViewModel(
        withRoutine: Boolean = false,
        routineSetCount: Int = 1,
        extraExercise: Boolean = false,
        sets: List<WorkoutSetEntity> = listOf(set("s1", completed = true)),
        workoutRepo: FakeWorkoutRepository? = null,
        runner: FakeTransactionRunner = FakeTransactionRunner(),
    ): FinishWorkoutViewModel {
        val repo = workoutRepo ?: FakeWorkoutRepository(
            workouts = listOf(workout(routineId = if (withRoutine) "r1" else null)),
            exercises = buildList {
                add(workoutExercise())
                if (extraExercise) add(workoutExercise(id = "we2", exerciseId = "ex-2", orderIndex = 1))
            },
            sets = sets,
        )
        val routineRepo = if (withRoutine) {
            FakeRoutineRepository(
                routines = listOf(RoutineEntity(id = "r1", folderId = null, name = "Push", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0)),
                exercises = listOf(RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
                sets = (0 until routineSetCount).map { i ->
                    RoutineSetEntity(
                        id = "rs$i", routineExerciseId = "re1", orderIndex = i, setType = SetType.NORMAL,
                        targetWeightKg = 60.0, targetReps = 8, targetRepRangeMin = null, targetRepRangeMax = null,
                        targetDurationSeconds = null, targetDistanceMeters = null,
                    )
                },
            )
        } else {
            FakeRoutineRepository()
        }
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1"), exercise("ex-2")))
        val clock = FakeClock(currentMillis = 100_000L)
        val updater = PersonalRecordsUpdater(
            repo, exerciseRepo, FakePersonalRecordsRepository(), FakeMeasurementRepository(), FakeSettingsRepository(),
        )
        return FinishWorkoutViewModel(
            SavedStateHandle(mapOf("workoutId" to "w1")),
            repo,
            routineRepo,
            WorkoutFinisher(repo, routineRepo, updater, runner, FakeHealthMetricsSource(), FakeWorkoutHeartRateSampleRepository(), clock),
        )
    }

    private fun workout(routineId: String? = null, durationSeconds: Int = 0) = WorkoutEntity(
        id = "w1", routineId = routineId, title = "Original Title", notes = null,
        status = WorkoutStatus.IN_PROGRESS, startedAt = 10_000L, endedAt = null,
        durationSeconds = durationSeconds, createdAt = 10_000L, updatedAt = 10_000L,
    )

    private fun workoutExercise(id: String = "we1", exerciseId: String = "ex-1", orderIndex: Int = 0) =
        WorkoutExerciseEntity(id = id, workoutId = "w1", exerciseId = exerciseId, orderIndex = orderIndex, supersetGroup = null, restTimerSeconds = null, notes = null)

    private fun set(
        id: String,
        completed: Boolean,
        orderIndex: Int = 0,
        workoutExerciseId: String = "we1",
    ) = WorkoutSetEntity(
        id = id, workoutExerciseId = workoutExerciseId, orderIndex = orderIndex, setType = SetType.NORMAL,
        weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null,
        customMetric = null, isCompleted = completed, completedAt = if (completed) 1L else null,
    )

    private fun exercise(id: String) = Exercise(
        id = id, name = "Ex $id", exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )
}
