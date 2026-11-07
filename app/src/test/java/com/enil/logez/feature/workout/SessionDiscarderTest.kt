package com.enil.logez.feature.workout

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.activity.ActivityTrackingController
import com.enil.logez.feature.workout.session.WorkoutSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionDiscarderTest {
    private fun gpsWorkout() = WorkoutEntity(
        id = "w1", routineId = null, title = "Run", notes = null, status = WorkoutStatus.IN_PROGRESS,
        startedAt = 1_000L, endedAt = null, durationSeconds = 0, createdAt = 1_000L, updatedAt = 1_000L,
        kind = WorkoutKind.GPS_TRACKED,
    )

    @Test
    fun `discarding a live GPS run cancels tracking, deletes the row and ends the session`() = runTest {
        val repo = FakeWorkoutRepository(listOf(gpsWorkout()))
        val clock = FakeClock()
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val tracking = ActivityTrackingController(repo, FakeActivityTrackRepository(), FakeLocationSource(), clock, scope)
        val session = WorkoutSessionController(FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), scope)
        tracking.startTracking("w1", "set1")
        session.startSession("w1")

        SessionDiscarder(WorkoutStarter(repo, FakeRoutineRepository(), clock), session, tracking).discardInProgress()

        assertFalse(tracking.state.value.isTracking)
        assertNull(repo.getInProgress())
        assertNull(session.state.value.workoutId)
    }

    @Test
    fun `discarding with nothing in progress is a harmless no-op`() = runTest {
        val repo = FakeWorkoutRepository()
        val clock = FakeClock()
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val tracking = ActivityTrackingController(repo, FakeActivityTrackRepository(), FakeLocationSource(), clock, scope)
        val session = WorkoutSessionController(FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), scope)

        SessionDiscarder(WorkoutStarter(repo, FakeRoutineRepository(), clock), session, tracking).discardInProgress()

        assertFalse(tracking.state.value.isTracking)
        assertNull(repo.getInProgress())
    }
}
