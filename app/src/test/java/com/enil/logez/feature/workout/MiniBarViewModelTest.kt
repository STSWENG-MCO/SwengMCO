package com.enil.logez.feature.workout

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.activity.ActivityTrackingController
import com.enil.logez.feature.workout.session.WorkoutSessionController
import kotlinx.coroutines.CoroutineScope
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
 * The mini bar carried the same defect as cold-start recovery: it sent every in-progress workout
 * to the strength Logger, which for a GPS run means a second foreground service. It now has to
 * surface enough state for the bar to route all three cases.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MiniBarViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun workout(kind: WorkoutKind) = WorkoutEntity(
        id = "w1", routineId = null, title = "Morning Run", notes = null,
        status = WorkoutStatus.IN_PROGRESS, startedAt = 5_000L, endedAt = null,
        durationSeconds = 0, createdAt = 5_000L, updatedAt = 5_000L, kind = kind,
    )

    private fun viewModel(repo: FakeWorkoutRepository, controller: ActivityTrackingController) =
        MiniBarViewModel(
            repo,
            controller,
            WorkoutSessionController(
                FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(),
                CoroutineScope(UnconfinedTestDispatcher()),
            ),
        )

    private fun controller(repo: FakeWorkoutRepository) = ActivityTrackingController(
        repo, FakeActivityTrackRepository(), FakeLocationSource(), FakeClock(),
        CoroutineScope(UnconfinedTestDispatcher()),
    )

    @Test
    fun `no in-progress workout hides the bar`() = runTest {
        val repo = FakeWorkoutRepository()
        assertFalse(viewModel(repo, controller(repo)).uiState.value.visible)
    }

    @Test
    fun `a strength workout reports its kind so the bar expands into the logger`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout(WorkoutKind.STRENGTH)))
        val state = viewModel(repo, controller(repo)).uiState.value
        assertTrue(state.visible)
        assertEquals(WorkoutKind.STRENGTH, state.kind)
    }

    @Test
    fun `a GPS run with tracking still live reports the session as alive`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout(WorkoutKind.GPS_TRACKED)))
        val controller = controller(repo)
        controller.startTracking("w1", "set1")
        val state = viewModel(repo, controller).uiState.value
        assertEquals(WorkoutKind.GPS_TRACKED, state.kind)
        assertTrue(state.gpsSessionAlive)
    }

    @Test
    fun `a GPS run whose process died reports the session as dead, with its start time`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout(WorkoutKind.GPS_TRACKED)))
        val state = viewModel(repo, controller(repo)).uiState.value
        assertEquals(WorkoutKind.GPS_TRACKED, state.kind)
        assertFalse(state.gpsSessionAlive)
        // The bar needs this to raise the interrupted-run dialog.
        assertEquals(5_000L, state.startedAt)
    }

    @Test
    fun `tracking state left over from a different workout does not count as this one's session`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout(WorkoutKind.GPS_TRACKED)))
        val controller = controller(repo)
        controller.startTracking("some-other-workout", "set9")
        assertFalse(viewModel(repo, controller).uiState.value.gpsSessionAlive)
    }
}
