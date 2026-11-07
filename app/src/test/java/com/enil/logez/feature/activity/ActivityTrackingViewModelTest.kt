package com.enil.logez.feature.activity

import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.session.WorkoutSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M21 redesign (2026-09-11): Finish now goes straight to the Save Workout screen instead of
 * through the strength Logger, which used to be the only thing that called
 * `sessionController.endSession()` (`WorkoutLoggerViewModel.prepareForFinish()`) once it was
 * reached. Without ending it here instead, the mini-bar's "in-progress, tap to resume" state would
 * keep pointing at a workoutId that's about to become COMPLETED, since nothing else on the new
 * direct path ever clears it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityTrackingViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun blankSet() = WorkoutSetEntity(
        id = "set-1", workoutExerciseId = "we-1", orderIndex = 0, setType = SetType.NORMAL,
        weightKg = null, reps = null, durationSeconds = null, distanceMeters = null,
        rpe = null, customMetric = null, isCompleted = false, completedAt = null,
    )

    private class Fixture(
        val viewModel: ActivityTrackingViewModel,
        val trackingController: ActivityTrackingController,
        val sessionController: WorkoutSessionController,
    )

    private fun fixture(clock: FakeClock = FakeClock()): Fixture {
        val workoutRepo = FakeWorkoutRepository(sets = listOf(blankSet()))
        val trackingController = ActivityTrackingController(
            workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), clock, CoroutineScope(UnconfinedTestDispatcher()),
        )
        val sessionController = WorkoutSessionController(
            FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()),
        )
        val workoutStarter = WorkoutStarter(workoutRepo, FakeRoutineRepository(), clock)
        val viewModel = ActivityTrackingViewModel(
            trackingController, SessionDiscarder(workoutStarter, sessionController, trackingController),
            sessionController, FakeSettingsRepository(), FakeHealthMetricsSource(), clock, com.enil.logez.core.common.AppLogger.NoOp,
        )
        return Fixture(viewModel, trackingController, sessionController)
    }

    @Test
    fun `finish ends the shared workout session, mirroring cancel`() = runTest {
        val f = fixture()
        f.trackingController.startTracking(workoutId = "w-1", workoutSetId = "set-1")
        f.sessionController.startSession("w-1")
        assertTrue(f.sessionController.state.value.hasActiveSession)

        val result = f.viewModel.finish()

        assertNotNull(result)
        assertFalse(f.sessionController.state.value.hasActiveSession)
    }

    @Test
    fun `finish returns null and still ends the session when no tracking was active`() = runTest {
        val f = fixture()
        f.sessionController.startSession("w-1") // e.g. a stale session from a prior workout

        val result = f.viewModel.finish()

        assertNull(result)
        assertFalse(f.sessionController.state.value.hasActiveSession)
    }
}
