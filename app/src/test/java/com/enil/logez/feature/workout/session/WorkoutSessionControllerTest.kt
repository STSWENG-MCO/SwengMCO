package com.enil.logez.feature.workout.session

import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSessionControllerTest {
    @Test
    fun `startSession sets workoutId and persists the start timestamp`() = runTest {
        val repo = FakeActiveSessionRepository()
        val clock = FakeClock(currentMillis = 10_000L)
        val controller = WorkoutSessionController(repo, clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))

        controller.startSession("w1")

        assertEquals("w1", controller.state.value.workoutId)
        assertEquals("w1", repo.snapshot.workoutId)
        assertEquals(10_000L, repo.snapshot.lastResumedAtMillis)
    }

    @Test
    fun `startSession persists before returning, so a process death can't strand an IN_PROGRESS workout with no session`() = runTest {
        val repo = FakeActiveSessionRepository()
        // A scope that never runs anything, standing in for "the fire-and-forget coroutine was
        // killed by process death". The persist must already have happened via the suspend call.
        val deadScope = CoroutineScope(Job())
        val controller = WorkoutSessionController(repo, FakeClock(currentMillis = 10_000L), FakeElapsedRealtimeClock(), deadScope)

        controller.startSession("w1")

        assertEquals("w1", repo.snapshot.workoutId)
    }

    @Test
    fun `empty workout session waits at zero for its first exercise and persists that mode`() = runTest {
        val repo = FakeActiveSessionRepository()
        val clock = FakeClock(currentMillis = 10_000L)
        val controller = WorkoutSessionController(repo, clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))

        controller.startSession("w1", waitForFirstExercise = true)
        clock.currentMillis = 70_000L

        assertTrue(controller.state.value.isPaused)
        assertTrue(controller.state.value.isEmptyWorkoutTimerMode)
        assertEquals(0L, controller.elapsedSeconds())
        assertTrue(repo.snapshot.isEmptyWorkoutTimerMode)
        assertNull(repo.snapshot.lastResumedAtMillis)
    }

    @Test
    fun `resetElapsedTime can restart or hold an empty workout at zero`() = runTest {
        val repo = FakeActiveSessionRepository()
        val clock = FakeClock(currentMillis = 0L)
        val controller = WorkoutSessionController(repo, clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        controller.startSession("w1", waitForFirstExercise = true)

        controller.resume()
        clock.currentMillis = 45_000L
        controller.resetElapsedTime(paused = false)
        assertEquals(0L, controller.elapsedSeconds())
        assertTrue(!controller.state.value.isPaused)

        clock.currentMillis = 60_000L
        controller.resetElapsedTime(paused = true)
        clock.currentMillis = 90_000L
        assertEquals(0L, controller.elapsedSeconds())
        assertTrue(repo.snapshot.isPaused)
        assertTrue(repo.snapshot.isEmptyWorkoutTimerMode)
    }

    @Test
    fun `pause persists before returning, so a lost write can't rehydrate a stale un-paused snapshot`() = runTest {
        val repo = FakeActiveSessionRepository()
        val clock = FakeClock(currentMillis = 0L)
        val deadScope = CoroutineScope(Job())
        val controller = WorkoutSessionController(repo, clock, FakeElapsedRealtimeClock(), deadScope)
        controller.startSession("w1")

        clock.currentMillis = 30_000L
        controller.pause()

        assertTrue(repo.snapshot.isPaused)
        assertEquals(30L, repo.snapshot.accumulatedActiveSeconds)
        assertNull(repo.snapshot.lastResumedAtMillis)
    }

    @Test
    fun `pause accumulates elapsed seconds and clears lastResumedAt`() = runTest {
        val repo = FakeActiveSessionRepository()
        val clock = FakeClock(currentMillis = 0L)
        val controller = WorkoutSessionController(repo, clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        controller.startSession("w1")

        clock.currentMillis = 30_000L
        controller.pause()

        assertTrue(controller.state.value.isPaused)
        assertEquals(30L, controller.state.value.accumulatedActiveSeconds)
        assertNull(controller.state.value.lastResumedAtMillis)
        assertEquals(30L, repo.snapshot.accumulatedActiveSeconds)
    }

    @Test
    fun `resume after pause continues accumulating from the new resume point`() = runTest {
        val repo = FakeActiveSessionRepository()
        val clock = FakeClock(currentMillis = 0L)
        val controller = WorkoutSessionController(repo, clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        controller.startSession("w1")
        clock.currentMillis = 30_000L
        controller.pause()

        clock.currentMillis = 45_000L
        controller.resume()
        clock.currentMillis = 65_000L // 20s further active

        assertEquals(50L, controller.elapsedSeconds()) // 30 accumulated + 20 more active
    }

    @Test
    fun `pause is a no-op when no session is active`() = runTest {
        val controller = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        controller.pause()
        assertTrue(!controller.state.value.isPaused)
    }

    @Test
    fun `startRestTimer sets a deadline and persists it, skipRestTimer clears it`() = runTest {
        val repo = FakeActiveSessionRepository()
        val elapsedClock = FakeElapsedRealtimeClock(currentMillis = 1_000L)
        val controller = WorkoutSessionController(repo, FakeClock(), elapsedClock, CoroutineScope(UnconfinedTestDispatcher()))
        controller.startSession("w1")

        controller.startRestTimer("we1", 90)
        assertEquals(91_000L, controller.state.value.restDeadlineElapsedRealtimeMillis)
        assertEquals("we1", controller.state.value.restExerciseId)
        assertEquals(91_000L, repo.snapshot.restDeadlineElapsedRealtimeMillis)

        controller.skipRestTimer()
        assertNull(controller.state.value.restDeadlineElapsedRealtimeMillis)
        assertNull(controller.state.value.restExerciseId)
        assertNull(repo.snapshot.restDeadlineElapsedRealtimeMillis)
    }

    @Test
    fun `startRestTimer with zero seconds (off) skips immediately without setting a deadline`() = runTest {
        val controller = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        controller.startSession("w1")

        controller.startRestTimer("we1", 0)

        assertNull(controller.state.value.restDeadlineElapsedRealtimeMillis)
    }

    @Test
    fun `adjustRestTimer minus15 below the floor clears the timer via the controller too`() = runTest {
        val elapsedClock = FakeElapsedRealtimeClock(currentMillis = 0L)
        val controller = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), elapsedClock, CoroutineScope(UnconfinedTestDispatcher()))
        controller.startSession("w1")
        controller.startRestTimer("we1", 10) // deadline at 10_000ms, "now" still 0

        controller.adjustRestTimer(-15)

        assertNull(controller.state.value.restDeadlineElapsedRealtimeMillis)
    }

    @Test
    fun `markRestTimerFired clears the deadline and emits the owning exerciseId once`() = runTest {
        val controller = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        controller.startSession("w1")
        controller.startRestTimer("we1", 90)

        // restTimerFired has no replay -- a collector must be actively subscribed before the
        // event fires, so start collecting (and let it actually subscribe via runCurrent()) first.
        var fired: String? = null
        val collectorJob = launch { fired = controller.restTimerFired.first() }
        runCurrent()

        controller.markRestTimerFired()
        collectorJob.join()

        assertNull(controller.state.value.restDeadlineElapsedRealtimeMillis)
        assertEquals("we1", fired)
    }

    @Test
    fun `inline timer start then stop for the same set returns elapsed seconds`() = runTest {
        val elapsedClock = FakeElapsedRealtimeClock(currentMillis = 5_000L)
        val controller = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), elapsedClock, CoroutineScope(UnconfinedTestDispatcher()))

        controller.startInlineTimer("we1", "s1")
        elapsedClock.currentMillis = 17_000L
        val seconds = controller.stopInlineTimer("we1", "s1")

        assertEquals(12, seconds)
        assertNull(controller.state.value.inlineTimer)
    }

    @Test
    fun `stopInlineTimer for a different set than the one running returns null and leaves it running`() = runTest {
        val controller = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        controller.startInlineTimer("we1", "s1")

        val result = controller.stopInlineTimer("we1", "sOther")

        assertNull(result)
        assertEquals("s1", controller.state.value.inlineTimer?.setId)
    }

    @Test
    fun `rehydrate restores a persisted snapshot -- process-death recovery`() = runTest {
        val repo = FakeActiveSessionRepository()
        repo.startSession("w1", 5_000L, isEmptyWorkoutTimerMode = false)
        repo.updateRestTimer(20_000L, "we1")
        val controller = WorkoutSessionController(repo, FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))

        controller.rehydrate()

        assertEquals("w1", controller.state.value.workoutId)
        assertEquals(5_000L, controller.state.value.lastResumedAtMillis)
        assertEquals(20_000L, controller.state.value.restDeadlineElapsedRealtimeMillis)
        assertEquals("we1", controller.state.value.restExerciseId)
    }

    @Test
    fun `rehydrate is idempotent and does not clobber live state on a second call`() = runTest {
        val repo = FakeActiveSessionRepository()
        val controller = WorkoutSessionController(repo, FakeClock(currentMillis = 1_000L), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        controller.startSession("live-session")

        // A stray second rehydrate() call (e.g. from a second collector) must not overwrite the
        // already-live in-memory state with whatever the repository happened to have.
        controller.rehydrate()

        assertEquals("live-session", controller.state.value.workoutId)
    }

    @Test
    fun `endSession clears both in-memory and persisted state`() = runTest {
        val repo = FakeActiveSessionRepository()
        val controller = WorkoutSessionController(repo, FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        controller.startSession("w1")
        controller.startRestTimer("we1", 90)

        controller.endSession()

        assertNull(controller.state.value.workoutId)
        assertNull(controller.state.value.restDeadlineElapsedRealtimeMillis)
        assertNull(repo.snapshot.workoutId)
    }

    @Test
    fun `elapsedSecondsFlow's first emission reflects the current computed elapsed time`() = runTest {
        val clock = FakeClock(currentMillis = 0L)
        val controller = WorkoutSessionController(FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        controller.startSession("w1")
        clock.currentMillis = 42_000L

        assertEquals(42L, controller.elapsedSecondsFlow.first())
    }

    @Test
    fun `restRemainingMillisFlow's first emission reflects the current remaining time`() = runTest {
        val elapsedClock = FakeElapsedRealtimeClock(currentMillis = 0L)
        val controller = WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), elapsedClock, CoroutineScope(UnconfinedTestDispatcher()))
        controller.startSession("w1")
        controller.startRestTimer("we1", 90)
        elapsedClock.currentMillis = 30_000L

        assertEquals(60_000L, controller.restRemainingMillisFlow.first())
    }
}
