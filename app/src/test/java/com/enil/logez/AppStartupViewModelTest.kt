package com.enil.logez

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.activity.ActivityTrackingController
import com.enil.logez.feature.workout.InProgressWorkoutResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * §9.5 cold-start recovery. A GPS run and a typed session need opposite handling, and routing a
 * recovered run into the strength Logger starts a second foreground service alongside the location
 * one that may still be running — so every branch is pinned here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStartupViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun workout(id: String, kind: WorkoutKind, status: WorkoutStatus = WorkoutStatus.IN_PROGRESS) =
        WorkoutEntity(
            id = id, routineId = null, title = "Session", notes = null, status = status,
            startedAt = 1_000L, endedAt = null, durationSeconds = 0, createdAt = 1_000L, updatedAt = 1_000L,
            kind = kind,
        )

    private fun controller(workoutRepo: FakeWorkoutRepository) = ActivityTrackingController(
        workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), FakeClock(),
        CoroutineScope(UnconfinedTestDispatcher()),
    )

    @Test
    fun `no in-progress workout recovers nothing`() = runTest {
        val repo = FakeWorkoutRepository()
        val vm = AppStartupViewModel(InProgressWorkoutResolver(repo, controller(repo)))
        assertEquals(StartupRecovery.None, vm.recovery.value)
    }

    @Test
    fun `an in-progress strength workout resumes into the logger`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout("w1", WorkoutKind.STRENGTH)))
        val vm = AppStartupViewModel(InProgressWorkoutResolver(repo, controller(repo)))
        assertEquals(StartupRecovery.ResumeStrength("w1"), vm.recovery.value)
    }

    @Test
    fun `a GPS run whose tracking session is still live re-enters the tracking screen`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout("w1", WorkoutKind.GPS_TRACKED)))
        val controller = controller(repo)
        controller.startTracking("w1", "set1")
        val vm = AppStartupViewModel(InProgressWorkoutResolver(repo, controller))
        assertEquals(StartupRecovery.ResumeLiveTracking, vm.recovery.value)
    }

    @Test
    fun `a GPS run whose process died is reported as interrupted, not resumed`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout("w1", WorkoutKind.GPS_TRACKED)))
        val vm = AppStartupViewModel(InProgressWorkoutResolver(repo, controller(repo)))
        assertEquals(StartupRecovery.InterruptedRun("w1", 1_000L), vm.recovery.value)
    }

    @Test
    fun `a controller left pointing at a different workout does not count as this run's live session`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout("w1", WorkoutKind.GPS_TRACKED)))
        val controller = controller(repo)
        // Tracking state from an earlier, already-finished run that was never cleared.
        controller.startTracking("some-other-workout", "set9")
        val vm = AppStartupViewModel(InProgressWorkoutResolver(repo, controller))
        assertEquals(StartupRecovery.InterruptedRun("w1", 1_000L), vm.recovery.value)
    }

    @Test
    fun `consumeRecovery clears the decision so it does not repeat on recomposition`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout("w1", WorkoutKind.STRENGTH)))
        val vm = AppStartupViewModel(InProgressWorkoutResolver(repo, controller(repo)))
        vm.consumeRecovery()
        assertEquals(StartupRecovery.None, vm.recovery.value)
    }
}
