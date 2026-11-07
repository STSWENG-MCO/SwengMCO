package com.enil.logez.feature.workout.finish

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.wellness.WorkoutHeartRateBackfill
import com.enil.logez.fakes.FakeActivityTrackRepository
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

/**
 * W1-01 regression: Finish → Save → Summary, end to end over one shared in-memory repository.
 * The Save screen's ViewModel saves the workout, its result names the workout the summary opens,
 * the summary shows the saved session (not the unsaved draft), and coming back to the finish
 * flow for the same workout does not offer to save it again.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FinishToSummaryRegressionTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private val exercises = FakeExerciseRepository(listOf(benchPress()))
    private val runner = FakeTransactionRunner()

    private val workoutRepo =
        FakeWorkoutRepository(
            workouts = listOf(inProgressWorkout()),
            exercises =
                listOf(
                    WorkoutExerciseEntity(
                        id = "we1",
                        workoutId = "w1",
                        exerciseId = "ex-1",
                        orderIndex = 0,
                        supersetGroup = null,
                        restTimerSeconds = null,
                        notes = null,
                    ),
                ),
            sets = listOf(set("s1", completed = true), set("s2", completed = false, orderIndex = 1)),
        )

    @Test
    fun `saving opens the summary for that same saved workout`() =
        runTest {
            val finish = finishViewModel()
            finish.updateTitle("Push Day")

            finish.save(structureChoice = null)

            // The Save screen hands this id to the navigation, which opens the summary for it.
            val saved = finish.saveState.value
            assertTrue("save should succeed, was $saved", saved is SaveState.Saved)
            val savedId = (saved as SaveState.Saved).result.workoutId
            assertEquals("w1", savedId)
            assertEquals(WorkoutStatus.COMPLETED, workoutRepo.getById(savedId)!!.status)

            val summary = summaryViewModel(savedId).uiState.value
            assertFalse(summary.isLoading)
            assertFalse("a strength workout must not take the GPS summary path", summary.isGpsTracked)
            // The saved title, not the draft's original one.
            assertEquals("Push Day", summary.title)
            // Only the completed set: the unfinished one was discarded by the save, so the summary
            // reflects the saved session rather than the in-progress draft.
            assertEquals(1, summary.completedSetCount)
        }

    @Test
    fun `re-entering the finish flow after saving does not offer to save again`() =
        runTest {
            finishViewModel().save(structureChoice = null)
            assertEquals(1, runner.transactionCount)

            val reopened = finishViewModel()

            assertTrue(reopened.uiState.value.isAlreadySaved)
            reopened.save(structureChoice = null)
            assertEquals("a second save must not run the save transaction again", 1, runner.transactionCount)
            assertEquals(SaveState.Idle, reopened.saveState.value)
        }

    // --- fixture ---

    private fun finishViewModel(): FinishWorkoutViewModel {
        val routineRepo = FakeRoutineRepository()
        val updater =
            PersonalRecordsUpdater(
                workoutRepo,
                exercises,
                FakePersonalRecordsRepository(),
                FakeMeasurementRepository(),
                FakeSettingsRepository(),
            )
        return FinishWorkoutViewModel(
            SavedStateHandle(mapOf(FinishWorkoutViewModel.WORKOUT_ID_ARG to "w1")),
            workoutRepo,
            routineRepo,
            WorkoutFinisher(
                workoutRepo,
                routineRepo,
                updater,
                runner,
                FakeHealthMetricsSource(),
                FakeWorkoutHeartRateSampleRepository(),
                FakeClock(currentMillis = 100_000L),
            ),
        )
    }

    private fun summaryViewModel(workoutId: String): WorkoutSummaryViewModel {
        val heartRateRepo = FakeWorkoutHeartRateSampleRepository()
        return WorkoutSummaryViewModel(
            savedStateHandle = SavedStateHandle(mapOf(WorkoutSummaryViewModel.WORKOUT_ID_ARG to workoutId)),
            workoutRepository = workoutRepo,
            exerciseRepository = exercises,
            personalRecordsRepository = FakePersonalRecordsRepository(),
            settingsRepository = FakeSettingsRepository(),
            activityTrackRepository = FakeActivityTrackRepository(),
            heartRateSampleRepository = heartRateRepo,
            heartRateBackfill = WorkoutHeartRateBackfill(FakeHealthMetricsSource(), heartRateRepo, AppLogger.NoOp),
        )
    }

    private fun inProgressWorkout() =
        WorkoutEntity(
            id = "w1",
            routineId = null,
            title = "Original Title",
            notes = null,
            status = WorkoutStatus.IN_PROGRESS,
            startedAt = 10_000L,
            endedAt = null,
            durationSeconds = 1800,
            createdAt = 10_000L,
            updatedAt = 10_000L,
        )

    private fun set(
        id: String,
        completed: Boolean,
        orderIndex: Int = 0,
    ) = WorkoutSetEntity(
        id = id,
        workoutExerciseId = "we1",
        orderIndex = orderIndex,
        setType = SetType.NORMAL,
        weightKg = 60.0,
        reps = 8,
        durationSeconds = null,
        distanceMeters = null,
        rpe = null,
        customMetric = null,
        isCompleted = completed,
        completedAt = if (completed) 1L else null,
    )

    private fun benchPress() =
        Exercise(
            id = "ex-1",
            name = "Bench Press (Barbell)",
            exerciseType = ExerciseType.WEIGHT_REPS,
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
}
