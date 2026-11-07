package com.enil.logez.feature.routines

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeRoutineRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineBuilderViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun exercise(id: String, name: String, type: ExerciseType = ExerciseType.WEIGHT_REPS) = Exercise(
        id = id,
        name = name,
        exerciseType = type,
        primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(),
        equipment = Equipment.BARBELL,
        instructions = "",
        mediaPath = null,
        isCustom = false,
        isBodyweightVolumeEligible = false,
        isDeleted = false,
        createdAt = 0,
        updatedAt = 0,
    )

    private fun newViewModel(
        routineId: String? = null,
        folderId: String? = null,
        routineRepo: FakeRoutineRepository = FakeRoutineRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
        clock: FakeClock = FakeClock(currentMillis = 5_000L),
    ) = RoutineBuilderViewModel(
        SavedStateHandle(buildMap { routineId?.let { put("routineId", it) }; folderId?.let { put("folderId", it) } }),
        routineRepo,
        exerciseRepo,
        FakeSettingsRepository(),
        clock,
    )

    @Test
    fun `create mode save is blocked with no title or no exercises`() = runTest {
        val repo = FakeRoutineRepository()
        val vm = newViewModel(routineRepo = repo)

        assertNull(vm.save()) // blank title, zero exercises

        vm.onTitleChange("Push Day")
        assertNull(vm.save()) // title set but still zero exercises

        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        val savedId = vm.save()
        assertNotNull(savedId)
    }

    @Test
    fun `create mode save writes a routine at the top of its bucket with correct exercise and set entities`() = runTest {
        val repo = FakeRoutineRepository(routines = listOf(RoutineEntity(id = "existing", folderId = null, name = "Old", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0)))
        val vm = newViewModel(routineRepo = repo, clock = FakeClock(currentMillis = 9_000L))

        vm.onTitleChange("Push Day")
        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        val savedId = vm.save()

        assertNotNull(savedId)
        val saved = repo.getRoutineById(savedId!!)!!
        assertEquals("Push Day", saved.name)
        assertEquals(0, saved.orderIndex) // new routine at top
        assertEquals(1, repo.getRoutineById("existing")!!.orderIndex) // existing pushed down
        assertEquals(9_000L, saved.createdAt)

        val exercises = repo.getExercisesForRoutine(savedId)
        assertEquals(1, exercises.size)
        assertEquals("ex-1", exercises[0].exerciseId)
        val sets = repo.getSetsForRoutineExercise(exercises[0].id)
        assertEquals(1, sets.size) // one empty set seeded on add
    }

    @Test
    fun `reorderExercises reorders the draft and save stamps orderIndex from the new position`() = runTest {
        val repo = FakeRoutineRepository()
        val vm = newViewModel(routineRepo = repo)
        vm.onTitleChange("Push Day")
        vm.addExercises(listOf(exercise("ex-a", "A"), exercise("ex-b", "B"), exercise("ex-c", "C")))
        val draftIds = vm.uiState.value.exercises.map { it.id } // [a, b, c] as draft row ids

        // M20a: the drag handle commits the full permuted id list exactly once on drop.
        vm.reorderExercises(listOf(draftIds[2], draftIds[0], draftIds[1]))

        assertEquals(listOf("ex-c", "ex-a", "ex-b"), vm.uiState.value.exercises.map { it.exerciseId })
        val savedId = vm.save()!!
        val persisted = repo.getExercisesForRoutine(savedId).sortedBy { it.orderIndex }
        assertEquals(listOf("ex-c", "ex-a", "ex-b"), persisted.map { it.exerciseId })
        assertEquals(listOf(0, 1, 2), persisted.map { it.orderIndex })
    }

    @Test
    fun `reorderExercises appends any exercise the caller's id list omits, rather than dropping it`() = runTest {
        // M20a: the screen sources the id list from an optimistic copy that can be a stale or
        // partial snapshot (e.g. taken before an exercise was added mid-drag) -- an omitted id must
        // never vanish from the draft.
        val repo = FakeRoutineRepository()
        val vm = newViewModel(routineRepo = repo)
        vm.onTitleChange("Push Day")
        vm.addExercises(listOf(exercise("ex-a", "A"), exercise("ex-b", "B"), exercise("ex-c", "C")))
        val draftIds = vm.uiState.value.exercises.map { it.id } // [a, b, c]

        vm.reorderExercises(listOf(draftIds[2], draftIds[0])) // b omitted

        assertEquals(listOf("ex-c", "ex-a", "ex-b"), vm.uiState.value.exercises.map { it.exerciseId })
        val savedId = vm.save()!!
        assertEquals(3, repo.getExercisesForRoutine(savedId).size)
    }

    @Test
    fun `edit mode loads existing structure and save replaces it in place without moving position`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val routineRepo = FakeRoutineRepository(
            routines = listOf(RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = "old notes", orderIndex = 3, createdAt = 100L, updatedAt = 100L)),
            exercises = listOf(RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(RoutineSetEntity(id = "s1", routineExerciseId = "re1", orderIndex = 0, setType = SetType.NORMAL, targetWeightKg = 60.0, targetReps = 8, targetRepRangeMin = null, targetRepRangeMax = null, targetDurationSeconds = null, targetDistanceMeters = null)),
        )
        val vm = newViewModel(routineId = "r1", routineRepo = routineRepo, exerciseRepo = exerciseRepo, clock = FakeClock(currentMillis = 20_000L))

        assertTrue(vm.isEditMode)
        assertEquals("Push Day", vm.uiState.value.title)
        assertEquals(1, vm.uiState.value.exercises.size)
        assertEquals(60.0, vm.uiState.value.exercises[0].sets[0].targetWeightKg)

        vm.onTitleChange("Push Day (renamed)")
        val savedId = vm.save()

        assertEquals("r1", savedId)
        val saved = routineRepo.getRoutineById("r1")!!
        assertEquals("Push Day (renamed)", saved.name)
        assertEquals(3, saved.orderIndex) // unchanged — edit-mode save doesn't re-home the routine
        assertEquals(100L, saved.createdAt) // unchanged
        assertEquals("old notes", saved.notes) // round-tripped even though the builder has no notes field
    }

    @Test
    fun `toggling rep range mode seeds min from the exact value, and back again keeps min as the exact value`() = runTest {
        val vm = newViewModel()
        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        val exerciseId = vm.uiState.value.exercises[0].id
        val setId = vm.uiState.value.exercises[0].sets[0].id
        vm.updateReps(exerciseId, setId, 8)

        vm.toggleRepRangeMode(exerciseId)

        var set = vm.uiState.value.exercises[0].sets[0]
        assertTrue(vm.uiState.value.exercises[0].isRepRangeMode)
        assertEquals(8, set.targetRepRangeMin)
        assertNull(set.targetReps)

        vm.updateRepRangeMax(exerciseId, setId, 12)
        vm.toggleRepRangeMode(exerciseId) // back to exact

        set = vm.uiState.value.exercises[0].sets[0]
        assertFalse(vm.uiState.value.exercises[0].isRepRangeMode)
        assertEquals(8, set.targetReps) // "toggling back keeps min as targetReps" (§5.1.2)
        assertNull(set.targetRepRangeMin)
        assertNull(set.targetRepRangeMax)
    }

    @Test
    fun `add set copies the previous set's targets with type reset to normal`() = runTest {
        val vm = newViewModel()
        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        val exerciseId = vm.uiState.value.exercises[0].id
        val firstSetId = vm.uiState.value.exercises[0].sets[0].id
        vm.updateWeight(exerciseId, firstSetId, 100.0)
        vm.updateReps(exerciseId, firstSetId, 5)
        vm.updateSetType(exerciseId, firstSetId, SetType.WARMUP)

        vm.addSet(exerciseId)

        val sets = vm.uiState.value.exercises[0].sets
        assertEquals(2, sets.size)
        assertEquals(100.0, sets[1].targetWeightKg)
        assertEquals(5, sets[1].targetReps)
        assertEquals(SetType.NORMAL, sets[1].setType) // reset, not copied
    }

    @Test
    fun `replace exercise preserves shared target fields and clears the rest`() = runTest {
        val vm = newViewModel()
        vm.addExercises(listOf(exercise("ex-1", "Bench Press", ExerciseType.WEIGHT_REPS)))
        val exerciseId = vm.uiState.value.exercises[0].id
        val setId = vm.uiState.value.exercises[0].sets[0].id
        vm.updateWeight(exerciseId, setId, 60.0)
        vm.updateReps(exerciseId, setId, 10)

        vm.replaceExercise(exerciseId, exercise("ex-2", "Plank Hold", ExerciseType.WEIGHT_DURATION))

        val updated = vm.uiState.value.exercises[0]
        assertEquals("ex-2", updated.exerciseId)
        assertEquals(ExerciseType.WEIGHT_DURATION, updated.exerciseType)
        assertEquals(60.0, updated.sets[0].targetWeightKg) // WEIGHT shared by both types — kept
        assertNull(updated.sets[0].targetReps) // REPS dropped by the new type — cleared
        assertNull(updated.sets[0].targetDurationSeconds) // new type has DURATION but old value never existed
    }

    @Test
    fun `replace exercise to a type without reps turns off rep range mode`() = runTest {
        val vm = newViewModel()
        vm.addExercises(listOf(exercise("ex-1", "Bench Press", ExerciseType.WEIGHT_REPS)))
        val exerciseId = vm.uiState.value.exercises[0].id
        vm.toggleRepRangeMode(exerciseId)
        assertTrue(vm.uiState.value.exercises[0].isRepRangeMode)

        vm.replaceExercise(exerciseId, exercise("ex-2", "Plank Hold", ExerciseType.DURATION))

        assertFalse(vm.uiState.value.exercises[0].isRepRangeMode)
    }

    @Test
    fun `confirming a superset target groups both exercises under the same group`() = runTest {
        val vm = newViewModel()
        vm.addExercises(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Incline Press")))
        val (first, second) = vm.uiState.value.exercises

        vm.startSupersetSelection(first.id)
        assertTrue(vm.uiState.value.supersetSelectionActive)
        vm.confirmSupersetTarget(second.id)

        val exercises = vm.uiState.value.exercises
        assertNotNull(exercises[0].supersetGroup)
        assertEquals(exercises[0].supersetGroup, exercises[1].supersetGroup)
        assertFalse(vm.uiState.value.supersetSelectionActive)
    }

    @Test
    fun `removing an exercise from a two-member superset clears the tag from the remaining solo member`() = runTest {
        val vm = newViewModel()
        vm.addExercises(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Incline Press")))
        val (first, second) = vm.uiState.value.exercises
        vm.startSupersetSelection(first.id)
        vm.confirmSupersetTarget(second.id)

        vm.removeExercise(first.id)

        val remaining = vm.uiState.value.exercises.single()
        assertNull(remaining.supersetGroup) // orphaned solo group cleaned up
    }

    @Test
    fun `isDirty is false right after load and true after any edit`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press")))
        val routineRepo = FakeRoutineRepository(
            routines = listOf(RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0)),
        )
        val vm = newViewModel(routineId = "r1", routineRepo = routineRepo, exerciseRepo = exerciseRepo)

        assertFalse(vm.uiState.value.isDirty)
        vm.onTitleChange("Push Day v2")
        assertTrue(vm.uiState.value.isDirty)
    }

    @Test
    fun `new routine is created with the folderId passed via SavedStateHandle`() = runTest {
        val repo = FakeRoutineRepository(folders = listOf(RoutineFolderEntity(id = "f1", name = "Push Pull", orderIndex = 0, createdAt = 0, updatedAt = 0)))
        val vm = newViewModel(folderId = "f1", routineRepo = repo)

        vm.onTitleChange("Push Day")
        vm.addExercises(listOf(exercise("ex-1", "Bench Press")))
        val savedId = vm.save()!!

        assertEquals("f1", repo.getRoutineById(savedId)!!.folderId)
    }

    // --- M11 circuit templates ---

    @Test
    fun `switching a draft to CIRCUIT pads every exercise to the largest set count, clears supersets and coerces warm-ups`() = runTest {
        val vm = newViewModel()
        vm.onTitleChange("Conditioning")
        vm.addExercises(listOf(exercise("ex-1", "Kettlebell Swing"), exercise("ex-2", "Goblet Squat")))
        // ex-1 grows to 3 sets, one of them WARMUP; ex-2 stays at 1.
        val ex1 = vm.uiState.value.exercises[0]
        vm.addSet(ex1.id)
        vm.addSet(ex1.id)
        vm.updateSetType(ex1.id, vm.uiState.value.exercises[0].sets[0].id, SetType.WARMUP)
        vm.updateWeight(ex1.id, vm.uiState.value.exercises[0].sets[2].id, 24.0)
        vm.startSupersetSelection(ex1.id)
        vm.confirmSupersetTarget(vm.uiState.value.exercises[1].id)

        vm.setStructure(WorkoutStructure.CIRCUIT)

        val state = vm.uiState.value
        assertEquals(WorkoutStructure.CIRCUIT, state.structure)
        assertEquals(3, state.rounds)
        assertEquals(listOf(3, 3), state.exercises.map { it.sets.size }) // ex-2 padded up
        assertTrue(state.exercises.all { it.supersetGroup == null }) // circuit IS the sequence
        assertTrue(state.exercises[0].sets.none { it.setType == SetType.WARMUP }) // coerced to NORMAL
        assertEquals(24.0, state.exercises[0].sets[2].targetWeightKg) // values untouched
    }

    @Test
    fun `addRound appends one target row to every exercise, copying that exercise's previous round`() = runTest {
        val vm = newViewModel()
        vm.onTitleChange("Conditioning")
        vm.setStructure(WorkoutStructure.CIRCUIT)
        vm.addExercises(listOf(exercise("ex-1", "Kettlebell Swing"), exercise("ex-2", "Goblet Squat")))
        val exercises = vm.uiState.value.exercises
        vm.updateWeight(exercises[0].id, exercises[0].sets[0].id, 24.0)
        vm.updateReps(exercises[0].id, exercises[0].sets[0].id, 15)
        vm.updateWeight(exercises[1].id, exercises[1].sets[0].id, 16.0)

        vm.addRound()

        val state = vm.uiState.value
        assertEquals(2, state.rounds)
        assertEquals(listOf(2, 2), state.exercises.map { it.sets.size })
        assertEquals(24.0, state.exercises[0].sets[1].targetWeightKg) // each exercise copies ITS OWN last round
        assertEquals(15, state.exercises[0].sets[1].targetReps)
        assertEquals(16.0, state.exercises[1].sets[1].targetWeightKg)
    }

    @Test
    fun `removeLastRound drops the last row everywhere and refuses to go below one round`() = runTest {
        val vm = newViewModel()
        vm.onTitleChange("Conditioning")
        vm.setStructure(WorkoutStructure.CIRCUIT)
        vm.addExercises(listOf(exercise("ex-1", "Kettlebell Swing"), exercise("ex-2", "Goblet Squat")))
        vm.addRound()
        assertEquals(2, vm.uiState.value.rounds)

        vm.removeLastRound()
        assertEquals(1, vm.uiState.value.rounds)
        assertEquals(listOf(1, 1), vm.uiState.value.exercises.map { it.sets.size })

        vm.removeLastRound() // already at the minimum
        assertEquals(1, vm.uiState.value.rounds)
        assertEquals(listOf(1, 1), vm.uiState.value.exercises.map { it.sets.size })
    }

    @Test
    fun `adding an exercise to a circuit draft seeds exactly rounds rows`() = runTest {
        val vm = newViewModel()
        vm.onTitleChange("Conditioning")
        vm.setStructure(WorkoutStructure.CIRCUIT)
        vm.addExercises(listOf(exercise("ex-1", "Kettlebell Swing")))
        vm.addRound()
        vm.addRound() // 3 rounds

        vm.addExercises(listOf(exercise("ex-2", "Goblet Squat")))

        assertEquals(listOf(3, 3), vm.uiState.value.exercises.map { it.sets.size })
    }

    @Test
    fun `per-exercise addSet and removeSet are inert on a circuit draft`() = runTest {
        val vm = newViewModel()
        vm.onTitleChange("Conditioning")
        vm.setStructure(WorkoutStructure.CIRCUIT)
        vm.addExercises(listOf(exercise("ex-1", "Kettlebell Swing")))
        val ex = vm.uiState.value.exercises.single()

        vm.addSet(ex.id)
        vm.removeSet(ex.id, vm.uiState.value.exercises.single().sets[0].id)

        assertEquals(1, vm.uiState.value.exercises.single().sets.size)
    }

    @Test
    fun `save persists the CIRCUIT structure and a circuit routine loads back as one`() = runTest {
        val repo = FakeRoutineRepository()
        val vm = newViewModel(routineRepo = repo)
        vm.onTitleChange("Conditioning")
        vm.setStructure(WorkoutStructure.CIRCUIT)
        vm.addExercises(listOf(exercise("ex-1", "Kettlebell Swing")))
        vm.addRound()

        val savedId = vm.save()!!
        assertEquals(WorkoutStructure.CIRCUIT, repo.getRoutineById(savedId)!!.structure)

        // Reopen in edit mode: structure and rounds come back, and setStructure is refused.
        val editVm = newViewModel(
            routineId = savedId,
            routineRepo = repo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Kettlebell Swing"))),
        )
        assertEquals(WorkoutStructure.CIRCUIT, editVm.uiState.value.structure)
        assertEquals(2, editVm.uiState.value.rounds)
        editVm.setStructure(WorkoutStructure.REGULAR)
        assertEquals(WorkoutStructure.CIRCUIT, editVm.uiState.value.structure) // immutable after creation
    }

    @Test
    fun `loading a circuit routine with drifted unequal set counts repairs it to a rectangle`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Kettlebell Swing"), exercise("ex-2", "Goblet Squat")))
        val routineRepo = FakeRoutineRepository(
            routines = listOf(
                RoutineEntity(
                    id = "r1", folderId = null, name = "Conditioning", notes = null, orderIndex = 0,
                    createdAt = 0, updatedAt = 0, structure = WorkoutStructure.CIRCUIT,
                ),
            ),
            exercises = listOf(
                RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                RoutineExerciseEntity(id = "re2", routineId = "r1", exerciseId = "ex-2", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = listOf(
                RoutineSetEntity(id = "s1", routineExerciseId = "re1", orderIndex = 0, setType = SetType.NORMAL, targetWeightKg = 24.0, targetReps = 15, targetRepRangeMin = null, targetRepRangeMax = null, targetDurationSeconds = null, targetDistanceMeters = null),
                RoutineSetEntity(id = "s2", routineExerciseId = "re1", orderIndex = 1, setType = SetType.NORMAL, targetWeightKg = 24.0, targetReps = 12, targetRepRangeMin = null, targetRepRangeMax = null, targetDurationSeconds = null, targetDistanceMeters = null),
                RoutineSetEntity(id = "s3", routineExerciseId = "re2", orderIndex = 0, setType = SetType.NORMAL, targetWeightKg = 16.0, targetReps = 10, targetRepRangeMin = null, targetRepRangeMax = null, targetDurationSeconds = null, targetDistanceMeters = null),
            ),
        )

        val vm = newViewModel(routineId = "r1", routineRepo = routineRepo, exerciseRepo = exerciseRepo)

        val state = vm.uiState.value
        assertEquals(2, state.rounds)
        assertEquals(listOf(2, 2), state.exercises.map { it.sets.size })
        // The short exercise's pad copies its own last round's targets.
        assertEquals(16.0, state.exercises[1].sets[1].targetWeightKg)
        assertEquals(10, state.exercises[1].sets[1].targetReps)
    }
}
