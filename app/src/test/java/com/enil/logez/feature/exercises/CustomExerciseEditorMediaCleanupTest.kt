package com.enil.logez.feature.exercises

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseMediaStore
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeMediaFileCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric only for a real [Uri]: [CustomExerciseEditorViewModel.onImagePicked] takes one, and
 * the plain-JVM editor tests deliberately never construct it (see MeasurementsViewModelTest's doc).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomExerciseEditorMediaCleanupTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val existing = Exercise(
        id = "existing-1",
        name = "Bench Press (Barbell)",
        exerciseType = ExerciseType.WEIGHT_REPS,
        primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = listOf(MuscleGroup.TRICEPS),
        equipment = Equipment.BARBELL,
        instructions = "",
        mediaPath = "exercise_media/old.jpg",
        isCustom = true,
        isBodyweightVolumeEligible = false,
        isDeleted = false,
        createdAt = 500L,
        updatedAt = 500L,
    )

    private fun editor(repo: FakeExerciseRepository, cleaner: FakeMediaFileCleaner, picked: String = "exercise_media/new.jpg") =
        CustomExerciseEditorViewModel(
            SavedStateHandle(mapOf(CustomExerciseEditorViewModel.EXERCISE_ID_ARG to existing.id)),
            repo, FakeExerciseMediaStore(result = picked), FakeClock(currentMillis = 9_000L), cleaner,
        )

    @Test
    fun `replacing an exercise's image deletes the old file once the new one is saved`() = runTest {
        val repo = FakeExerciseRepository(listOf(existing))
        val cleaner = FakeMediaFileCleaner()
        val vm = editor(repo, cleaner)

        vm.onImagePicked(Uri.parse("content://test/picked.jpg"))
        vm.save {}

        assertEquals("exercise_media/new.jpg", repo.getById(existing.id)?.mediaPath)
        assertEquals(listOf("exercise_media/old.jpg"), cleaner.requested)
    }

    @Test
    fun `saving without changing the image deletes nothing`() = runTest {
        val repo = FakeExerciseRepository(listOf(existing))
        val cleaner = FakeMediaFileCleaner()
        val vm = editor(repo, cleaner)

        vm.onNameChange("Bench Press (Paused)")
        vm.save {}

        assertTrue(cleaner.requested.isEmpty())
    }
}
