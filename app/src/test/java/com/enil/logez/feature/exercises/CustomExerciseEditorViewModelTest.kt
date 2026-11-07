package com.enil.logez.feature.exercises

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.MuscleHead
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseMediaStore
import com.enil.logez.fakes.FakeExerciseRepository
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
class CustomExerciseEditorViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun seedExercise(id: String = "existing-1") = Exercise(
        id = id,
        name = "Bench Press (Barbell)",
        exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = listOf(MuscleGroup.TRICEPS),
        equipment = Equipment.BARBELL,
        instructions = "",
        mediaPath = null,
        isCustom = true,
        isBodyweightVolumeEligible = false,
        isDeleted = false,
        createdAt = 500L,
        updatedAt = 500L,
    )

    @Test
    fun `create mode saves a new custom exercise with the entered fields`() = runTest {
        val repo = FakeExerciseRepository()
        val clock = FakeClock(currentMillis = 9_000L)
        val vm = CustomExerciseEditorViewModel(SavedStateHandle(), repo, FakeExerciseMediaStore(), clock)

        vm.onNameChange("Cable Row")
        vm.onEquipmentChange(Equipment.MACHINE)
        vm.onPrimaryMuscleChange(MuscleGroup.UPPER_BACK)
        vm.onExerciseTypeChange(ExerciseType.REPS_ONLY)

        var saved = false
        vm.save { saved = true }

        assertTrue(saved)
        val stored = repo.allIncludingDeleted().single()
        assertEquals("Cable Row", stored.name)
        assertEquals(Equipment.MACHINE, stored.equipment)
        assertEquals(MuscleGroup.UPPER_BACK, stored.primaryMuscleGroup)
        assertEquals(ExerciseType.REPS_ONLY, stored.exerciseType)
        assertTrue(stored.isCustom)
        assertFalse(stored.isBodyweightVolumeEligible)
        assertEquals(9_000L, stored.createdAt)
        assertEquals(9_000L, stored.updatedAt)
    }

    @Test
    fun `blank name is rejected and nothing is saved`() = runTest {
        val repo = FakeExerciseRepository()
        val vm = CustomExerciseEditorViewModel(SavedStateHandle(), repo, FakeExerciseMediaStore(), FakeClock())

        var saved = false
        vm.save { saved = true }

        assertFalse(saved)
        assertTrue(vm.uiState.value.nameError)
        assertTrue(repo.allIncludingDeleted().isEmpty())
    }

    @Test
    fun `edit mode loads the existing exercise's fields`() = runTest {
        val repo = FakeExerciseRepository(listOf(seedExercise()))
        val vm = CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf("exerciseId" to "existing-1")),
            repo,
            FakeExerciseMediaStore(),
            FakeClock(),
        )

        assertTrue(vm.isEditMode)
        assertEquals("Bench Press (Barbell)", vm.uiState.value.name)
        assertEquals(Equipment.BARBELL, vm.uiState.value.equipment)
        assertEquals(MuscleGroup.CHEST, vm.uiState.value.primaryMuscleGroup)
        assertEquals(ExerciseType.WEIGHT_REPS, vm.uiState.value.exerciseType)
    }

    @Test
    fun `exercise type is immutable in edit mode`() = runTest {
        val repo = FakeExerciseRepository(listOf(seedExercise()))
        val vm = CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf("exerciseId" to "existing-1")),
            repo,
            FakeExerciseMediaStore(),
            FakeClock(),
        )

        vm.onExerciseTypeChange(ExerciseType.REPS_ONLY) // no-op — type can't change after creation

        assertEquals(ExerciseType.WEIGHT_REPS, vm.uiState.value.exerciseType)
    }

    @Test
    fun `editing preserves the original id and createdAt while updating updatedAt`() = runTest {
        val repo = FakeExerciseRepository(listOf(seedExercise()))
        val clock = FakeClock(currentMillis = 20_000L)
        val vm = CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf("exerciseId" to "existing-1")),
            repo,
            FakeExerciseMediaStore(),
            clock,
        )

        vm.onNameChange("Bench Press (Barbell) — renamed")
        vm.save {}

        val stored = repo.getById("existing-1")!!
        assertEquals("existing-1", stored.id)
        assertEquals(500L, stored.createdAt) // unchanged
        assertEquals(20_000L, stored.updatedAt)
        assertEquals(1, repo.allIncludingDeleted().size) // no duplicate row created
    }

    @Test
    fun `editing a seed exercise sets isCustom, exempting it from future seed syncs`() = runTest {
        val seed = seedExercise(id = "seed-1").copy(isCustom = false)
        val repo = FakeExerciseRepository(listOf(seed))
        val vm = CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf("exerciseId" to "seed-1")),
            repo,
            FakeExerciseMediaStore(),
            FakeClock(),
        )

        vm.onNameChange("Bench Press (Barbell) — renamed")
        vm.save {}

        assertTrue(repo.getById("seed-1")!!.isCustom)
    }

    @Test
    fun `editing an unrelated field preserves instructions and bodyweight eligibility untouched`() = runTest {
        val seed = seedExercise(id = "seed-1").copy(
            isCustom = false,
            instructions = "Grip the bar slightly wider than shoulder width.",
            isBodyweightVolumeEligible = true,
        )
        val repo = FakeExerciseRepository(listOf(seed))
        val vm = CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf("exerciseId" to "seed-1")),
            repo,
            FakeExerciseMediaStore(),
            FakeClock(),
        )

        vm.onEquipmentChange(Equipment.MACHINE) // touch an unrelated field, then save
        vm.save {}

        val stored = repo.getById("seed-1")!!
        assertEquals("Grip the bar slightly wider than shoulder width.", stored.instructions)
        assertTrue(stored.isBodyweightVolumeEligible)
    }

    @Test
    fun `instructions load into UI state and an edit is saved, trimmed, on any exercise including a seed one`() = runTest {
        val seed = seedExercise(id = "seed-1").copy(isCustom = false, instructions = "Old step 1.")
        val repo = FakeExerciseRepository(listOf(seed))
        val vm = CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf("exerciseId" to "seed-1")),
            repo,
            FakeExerciseMediaStore(),
            FakeClock(),
        )

        assertEquals("Old step 1.", vm.uiState.value.instructions)

        vm.onInstructionsChange("  Step 1: grip the bar.\nStep 2: brace and lift.  ")
        vm.save {}

        val stored = repo.getById("seed-1")!!
        assertEquals("Step 1: grip the bar.\nStep 2: brace and lift.", stored.instructions)
        assertTrue(stored.isCustom)
    }

    @Test
    fun `instructions carrying Markdown emphasis are stored verbatim`() = runTest {
        // M20g: the editor writes RichTextState.toMarkdown() into this same field, so the
        // storage path must stay format-agnostic -- no escaping, no stripping of asterisks.
        val seed = seedExercise(id = "seed-1").copy(isCustom = false)
        val repo = FakeExerciseRepository(listOf(seed))
        val vm = CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf("exerciseId" to "seed-1")),
            repo,
            FakeExerciseMediaStore(),
            FakeClock(),
        )

        vm.onInstructionsChange("Grip the **bar** wide.\nLower with *control*.")
        vm.save {}

        assertEquals("Grip the **bar** wide.\nLower with *control*.", repo.getById("seed-1")!!.instructions)
    }

    @Test
    fun `a muscle head can be picked for a group that has heads and is saved`() = runTest {
        val repo = FakeExerciseRepository()
        val vm = CustomExerciseEditorViewModel(SavedStateHandle(), repo, FakeExerciseMediaStore(), FakeClock())

        vm.onNameChange("Lateral Raise")
        vm.onPrimaryMuscleChange(MuscleGroup.SHOULDERS)
        vm.onMuscleHeadToggle(MuscleHead.LATERAL_DELTOID)
        vm.save {}

        assertEquals(listOf(MuscleHead.LATERAL_DELTOID), repo.allIncludingDeleted().single().muscleHeads)
    }

    @Test
    fun `a checklist -- more than one head of the same group can be picked at once`() = runTest {
        val repo = FakeExerciseRepository()
        val vm = CustomExerciseEditorViewModel(SavedStateHandle(), repo, FakeExerciseMediaStore(), FakeClock())

        vm.onNameChange("Arnold Press") // a real example of an exercise hitting more than one delt head
        vm.onPrimaryMuscleChange(MuscleGroup.SHOULDERS)
        vm.onMuscleHeadToggle(MuscleHead.ANTERIOR_DELTOID)
        vm.onMuscleHeadToggle(MuscleHead.LATERAL_DELTOID)
        vm.save {}

        assertEquals(
            setOf(MuscleHead.ANTERIOR_DELTOID, MuscleHead.LATERAL_DELTOID),
            repo.allIncludingDeleted().single().muscleHeads.toSet(),
        )
    }

    @Test
    fun `toggling an already-picked head off removes it, leaving the others`() = runTest {
        val repo = FakeExerciseRepository()
        val vm = CustomExerciseEditorViewModel(SavedStateHandle(), repo, FakeExerciseMediaStore(), FakeClock())

        vm.onPrimaryMuscleChange(MuscleGroup.SHOULDERS)
        vm.onMuscleHeadToggle(MuscleHead.ANTERIOR_DELTOID)
        vm.onMuscleHeadToggle(MuscleHead.LATERAL_DELTOID)
        vm.onMuscleHeadToggle(MuscleHead.ANTERIOR_DELTOID) // toggle back off

        assertEquals(setOf(MuscleHead.LATERAL_DELTOID), vm.uiState.value.muscleHeads)
    }

    @Test
    fun `changing the primary muscle group clears previously picked heads`() = runTest {
        val repo = FakeExerciseRepository()
        val vm = CustomExerciseEditorViewModel(SavedStateHandle(), repo, FakeExerciseMediaStore(), FakeClock())

        vm.onNameChange("Something")
        vm.onPrimaryMuscleChange(MuscleGroup.SHOULDERS)
        vm.onMuscleHeadToggle(MuscleHead.POSTERIOR_DELTOID)
        vm.onPrimaryMuscleChange(MuscleGroup.CHEST) // different group -- the old heads no longer apply

        assertTrue(vm.uiState.value.muscleHeads.isEmpty())
    }

    @Test
    fun `a group with no tracked heads never saves any muscle head, even if some were set before switching`() = runTest {
        val repo = FakeExerciseRepository()
        val vm = CustomExerciseEditorViewModel(SavedStateHandle(), repo, FakeExerciseMediaStore(), FakeClock())

        vm.onNameChange("Crunch")
        vm.onPrimaryMuscleChange(MuscleGroup.SHOULDERS)
        vm.onMuscleHeadToggle(MuscleHead.ANTERIOR_DELTOID)
        vm.onPrimaryMuscleChange(MuscleGroup.ABDOMINALS) // ABDOMINALS has no tracked heads
        vm.save {}

        assertTrue(repo.allIncludingDeleted().single().muscleHeads.isEmpty())
    }

    @Test
    fun `re-selecting the already-selected primary group does not clear picked heads`() = runTest {
        val repo = FakeExerciseRepository()
        val vm = CustomExerciseEditorViewModel(SavedStateHandle(), repo, FakeExerciseMediaStore(), FakeClock())

        vm.onNameChange("Face Pull")
        vm.onPrimaryMuscleChange(MuscleGroup.SHOULDERS)
        vm.onMuscleHeadToggle(MuscleHead.POSTERIOR_DELTOID)
        vm.onPrimaryMuscleChange(MuscleGroup.SHOULDERS) // re-tapping the same, already-selected group

        assertEquals(setOf(MuscleHead.POSTERIOR_DELTOID), vm.uiState.value.muscleHeads)
    }

    @Test
    fun `edit mode loads an existing exercise's muscle heads`() = runTest {
        val seed = seedExercise().copy(primaryMuscleGroup = MuscleGroup.CALVES, muscleHeads = listOf(MuscleHead.GASTROCNEMIUS, MuscleHead.SOLEUS))
        val repo = FakeExerciseRepository(listOf(seed))
        val vm = CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf("exerciseId" to "existing-1")),
            repo,
            FakeExerciseMediaStore(),
            FakeClock(),
        )

        assertEquals(setOf(MuscleHead.GASTROCNEMIUS, MuscleHead.SOLEUS), vm.uiState.value.muscleHeads)
    }

    @Test
    fun `prefill name seeds the create form from a library search query`() = runTest {
        val vm = CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf("prefillName" to "Nordic Curl")),
            FakeExerciseRepository(),
            FakeExerciseMediaStore(),
            FakeClock(),
        )

        assertEquals("Nordic Curl", vm.uiState.value.name)
        assertFalse(vm.isEditMode)
    }
}
