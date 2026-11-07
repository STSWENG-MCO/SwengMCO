package com.enil.logez.feature.history

import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeExerciseRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** PHASE2_PLAN.md §5.2 History tab feed — card stats, ordering, and the records chip gate. */
@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `an empty history produces an empty, non-loading card list`() = runTest {
        val vm = viewModel(FakeWorkoutRepository())
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.cards.isEmpty())
    }

    @Test
    fun `cards sort newest-first and only ever include COMPLETED workouts`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(
                workout("w-old", startedAt = 1_000L),
                workout("w-new", startedAt = 3_000L),
                workout("w-mid", startedAt = 2_000L),
                workout("w-live", startedAt = 5_000L, status = WorkoutStatus.IN_PROGRESS),
            ),
        )
        val vm = viewModel(workoutRepo)

        assertEquals(listOf("w-new", "w-mid", "w-old"), vm.uiState.value.cards.map { it.workoutId })
    }

    @Test
    fun `card volume matches the same isIncluded plus VolumeCalculator path the Summary screen uses`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout("w1", startedAt = 1_000L)),
            exercises = listOf(workoutExercise("we1", "w1")),
            sets = listOf(
                aSet("s1", "we1", 0, weightKg = 100.0, reps = 5, setType = SetType.NORMAL, isCompleted = true),
                aSet("s2", "we1", 1, weightKg = 40.0, reps = 12, setType = SetType.WARMUP, isCompleted = true), // excluded by default
                aSet("s3", "we1", 2, weightKg = 100.0, reps = 5, setType = SetType.NORMAL, isCompleted = false), // excluded — not completed
            ),
        )
        val vm = viewModel(workoutRepo)

        val card = vm.uiState.value.cards.single()
        assertEquals(500.0, card.volumeKg, 1e-9) // only s1: 100 * 5
        assertEquals(1, card.setCount)
    }

    @Test
    fun `the exercise lines sum to the card's own Sets stat when the workout contains warm-ups`() = runTest {
        // Regression: the summary lines counted raw isCompleted while the Sets stat filtered
        // through isIncluded, so one card asserted two different totals for itself — a workout
        // with warm-ups read "3 Sets" beside lines adding up to 5.
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout("w1", startedAt = 1_000L)),
            exercises = listOf(workoutExercise("we-squat", "w1", exerciseId = "ex-squat")),
            sets = listOf(
                aSet("s1", "we-squat", 0, weightKg = 40.0, reps = 10, setType = SetType.WARMUP, isCompleted = true),
                aSet("s2", "we-squat", 1, weightKg = 60.0, reps = 8, setType = SetType.WARMUP, isCompleted = true),
                aSet("s3", "we-squat", 2, weightKg = 100.0, reps = 5, isCompleted = true),
                aSet("s4", "we-squat", 3, weightKg = 100.0, reps = 5, isCompleted = true),
                aSet("s5", "we-squat", 4, weightKg = 100.0, reps = 5, isCompleted = true),
            ),
        )
        val vm = viewModel(workoutRepo, exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-squat", "Back Squat"))))

        val card = vm.uiState.value.cards.single()
        assertEquals(3, card.setCount) // 2 warm-ups excluded by default
        assertEquals(
            "the lines a user reads must add up to the stat printed directly above them",
            card.setCount,
            card.exerciseSummaries.sumOf { it.setCount },
        )
    }

    @Test
    fun `turning the warm-up setting on moves both the stat and the lines together`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout("w1", startedAt = 1_000L)),
            exercises = listOf(workoutExercise("we-squat", "w1", exerciseId = "ex-squat")),
            sets = listOf(
                aSet("s1", "we-squat", 0, weightKg = 40.0, reps = 10, setType = SetType.WARMUP, isCompleted = true),
                aSet("s2", "we-squat", 1, weightKg = 100.0, reps = 5, isCompleted = true),
            ),
        )
        val vm = viewModel(
            workoutRepo,
            exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-squat", "Back Squat"))),
            settingsRepo = FakeSettingsRepository(UserSettings(includeWarmupsInStats = true)),
        )

        val card = vm.uiState.value.cards.single()
        assertEquals(2, card.setCount)
        assertEquals(2, card.exerciseSummaries.single().setCount)
    }

    @Test
    fun `a GPS-tracked walk with no weight logged hides Volume and reports its distance`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout("w1", startedAt = 1_000L)),
            exercises = listOf(workoutExercise("we1", "w1")),
            sets = listOf(
                aSet("s1", "we1", 0, weightKg = null, reps = null, distanceMeters = 2_000.0, isCompleted = true),
            ),
        )
        val vm = viewModel(workoutRepo)

        val card = vm.uiState.value.cards.single()
        assertFalse(card.hasVolume)
        assertTrue(card.hasDistance)
        assertEquals(2_000.0, card.distanceMeters, 1e-9)
    }

    @Test
    fun `a strength workout with logged weight shows Volume and has no distance`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout("w1", startedAt = 1_000L)),
            exercises = listOf(workoutExercise("we1", "w1")),
            sets = listOf(aSet("s1", "we1", 0, weightKg = 100.0, reps = 5, isCompleted = true)),
        )
        val vm = viewModel(workoutRepo)

        val card = vm.uiState.value.cards.single()
        assertTrue(card.hasVolume)
        assertFalse(card.hasDistance)
    }

    @Test
    fun `the records chip only appears when a PR names this workout`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout("w-pr", startedAt = 1_000L), workout("w-no-pr", startedAt = 2_000L)),
        )
        val recordsRepo = FakePersonalRecordsRepository(
            listOf(
                PersonalRecordEntity(id = "pr1", exerciseId = "ex-1", workoutId = "w-pr", workoutSetId = "s1", prType = PrType.HEAVIEST_WEIGHT, value = 100.0, achievedAt = 1_000L),
            ),
        )
        val vm = viewModel(workoutRepo, recordsRepo)

        val prCard = vm.uiState.value.cards.first { it.workoutId == "w-pr" }
        val noPrCard = vm.uiState.value.cards.first { it.workoutId == "w-no-pr" }
        assertTrue(prCard.hasRecords)
        assertFalse(noPrCard.hasRecords)
    }

    @Test
    fun `exercise summary lines are in block order with their completed-set count, not raw set count`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout("w1", startedAt = 1_000L)),
            exercises = listOf(
                workoutExercise("we-squat", "w1", exerciseId = "ex-squat", orderIndex = 0),
                workoutExercise("we-curl", "w1", exerciseId = "ex-curl", orderIndex = 1),
            ),
            sets = listOf(
                aSet("s1", "we-squat", 0, weightKg = 80.0, reps = 5, isCompleted = true),
                aSet("s2", "we-squat", 1, weightKg = 80.0, reps = 5, isCompleted = true),
                aSet("s3", "we-squat", 2, weightKg = 80.0, reps = 5, isCompleted = false),
                aSet("s4", "we-curl", 0, weightKg = 15.0, reps = 10, isCompleted = true),
            ),
        )
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-squat", "Back Squat"), exercise("ex-curl", "Bicep Curl")))
        val vm = viewModel(workoutRepo, exerciseRepo = exerciseRepo)

        val lines = vm.uiState.value.cards.single().exerciseSummaries
        assertEquals(listOf("Back Squat" to 2, "Bicep Curl" to 1), lines.map { it.name to it.setCount })
    }

    // --- fixture ---

    private fun viewModel(
        workoutRepo: FakeWorkoutRepository,
        recordsRepo: FakePersonalRecordsRepository = FakePersonalRecordsRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(listOf(exercise("ex-1"))),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
    ) = HistoryViewModel(workoutRepo, exerciseRepo, recordsRepo, settingsRepo)

    private fun workout(id: String, startedAt: Long, status: WorkoutStatus = WorkoutStatus.COMPLETED) = WorkoutEntity(
        id = id, routineId = null, title = "Session $id", notes = null, status = status,
        startedAt = startedAt, endedAt = startedAt + 1000L, durationSeconds = 1000, createdAt = startedAt, updatedAt = startedAt,
    )

    private fun workoutExercise(id: String, workoutId: String, exerciseId: String = "ex-1", orderIndex: Int = 0) = WorkoutExerciseEntity(
        id = id, workoutId = workoutId, exerciseId = exerciseId, orderIndex = orderIndex, supersetGroup = null, restTimerSeconds = null, notes = null,
    )

    private fun aSet(
        id: String,
        workoutExerciseId: String,
        orderIndex: Int,
        weightKg: Double?,
        reps: Int?,
        setType: SetType = SetType.NORMAL,
        isCompleted: Boolean,
        distanceMeters: Double? = null,
    ) = WorkoutSetEntity(
        id = id, workoutExerciseId = workoutExerciseId, orderIndex = orderIndex, setType = setType,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = distanceMeters, rpe = null,
        customMetric = null, isCompleted = isCompleted, completedAt = if (isCompleted) 1L else null,
    )

    private fun exercise(id: String, name: String = "Ex $id") = Exercise(
        id = id, name = name, exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )
}
