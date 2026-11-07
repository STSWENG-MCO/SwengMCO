package com.enil.logez.feature.workout

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.activity.ActivityTrackingController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Every resume surface asks this one question. Five screens used to answer it themselves and each
 * got a different subset right, which is how GPS runs ended up opening the strength logger.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InProgressWorkoutResolverTest {
    private fun workout(kind: WorkoutKind, status: WorkoutStatus = WorkoutStatus.IN_PROGRESS) =
        WorkoutEntity(
            id = "w1", routineId = null, title = "Session", notes = null, status = status,
            startedAt = 7_000L, endedAt = null, durationSeconds = 0,
            createdAt = 7_000L, updatedAt = 7_000L, kind = kind,
        )

    private fun controller(repo: FakeWorkoutRepository) = ActivityTrackingController(
        repo, FakeActivityTrackRepository(), FakeLocationSource(), FakeClock(),
        CoroutineScope(UnconfinedTestDispatcher()),
    )

    private fun resolver(repo: FakeWorkoutRepository, controller: ActivityTrackingController) =
        InProgressWorkoutResolver(repo, controller)

    @Test
    fun `nothing in progress resolves to null`() = runTest {
        val repo = FakeWorkoutRepository()
        assertNull(resolver(repo, controller(repo)).resolve())
    }

    @Test
    fun `a typed session resolves to Strength`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout(WorkoutKind.STRENGTH)))
        assertEquals(InProgressWorkout.Strength("w1"), resolver(repo, controller(repo)).resolve())
    }

    @Test
    fun `a GPS run still collecting resolves to LiveGpsRun`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout(WorkoutKind.GPS_TRACKED)))
        val controller = controller(repo)
        controller.startTracking("w1", "set1")
        assertEquals(InProgressWorkout.LiveGpsRun("w1"), resolver(repo, controller).resolve())
    }

    @Test
    fun `a GPS run whose process died resolves to InterruptedGpsRun, carrying its start time`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout(WorkoutKind.GPS_TRACKED)))
        assertEquals(
            InProgressWorkout.InterruptedGpsRun("w1", 7_000L),
            resolver(repo, controller(repo)).resolve(),
        )
    }

    @Test
    fun `tracking state left over from a different workout does not count as this one's session`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout(WorkoutKind.GPS_TRACKED)))
        val controller = controller(repo)
        controller.startTracking("some-other-workout", "set9")
        assertEquals(
            InProgressWorkout.InterruptedGpsRun("w1", 7_000L),
            resolver(repo, controller).resolve(),
        )
    }

    @Test
    fun `a completed workout is not in progress`() = runTest {
        val repo = FakeWorkoutRepository(listOf(workout(WorkoutKind.GPS_TRACKED, WorkoutStatus.COMPLETED)))
        assertNull(resolver(repo, controller(repo)).resolve())
    }
}
