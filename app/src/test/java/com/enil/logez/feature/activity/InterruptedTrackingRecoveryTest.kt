package com.enil.logez.feature.activity

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeWorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M23a: what happens to a GPS run whose process died mid-track. */
@OptIn(ExperimentalCoroutinesApi::class)
class InterruptedTrackingRecoveryTest {
    private val startedAt = 1_000_000L
    private val now = startedAt + 4_320_000L // 72 minutes later

    private fun repo() = FakeWorkoutRepository(
        workouts = listOf(
            WorkoutEntity(
                id = "run1", routineId = null, title = "Morning Run", notes = null,
                status = WorkoutStatus.IN_PROGRESS, startedAt = startedAt, endedAt = null,
                durationSeconds = 0, createdAt = startedAt, updatedAt = startedAt,
                kind = WorkoutKind.GPS_TRACKED,
            ),
        ),
        exercises = listOf(
            WorkoutExerciseEntity(
                id = "we1", workoutId = "run1", exerciseId = "ex1", orderIndex = 0,
                supersetGroup = null, restTimerSeconds = null, notes = null,
            ),
        ),
        sets = listOf(
            WorkoutSetEntity(
                id = "set1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL,
                weightKg = null, reps = null, durationSeconds = null, distanceMeters = null,
                rpe = null, customMetric = null, isCompleted = false, completedAt = null,
            ),
        ),
    )

    private fun recovery(workoutRepo: FakeWorkoutRepository) = InterruptedTrackingRecovery(
        workoutRepo,
        ActivityTrackingController(
            workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), FakeClock(now),
            CoroutineScope(UnconfinedTestDispatcher()),
        ),
        FakeClock(now),
    )

    @Test
    fun `keepElapsedTime freezes the elapsed duration onto the workout`() = runTest {
        val repo = repo()
        assertTrue(recovery(repo).keepElapsedTime("run1"))
        assertEquals(4_320, repo.getById("run1")?.durationSeconds)
    }

    @Test
    fun `keepElapsedTime marks the set completed so saving does not purge the only row`() = runTest {
        val repo = repo()
        recovery(repo).keepElapsedTime("run1")
        // WorkoutDao.finishWorkout deletes uncompleted sets before rebuilding PRs, so a blank set
        // here would silently take the workout's only row with it.
        val set = repo.getSetsForWorkoutExercise("we1").single()
        assertTrue(set.isCompleted)
        assertEquals(now, set.completedAt)
    }

    @Test
    fun `keepElapsedTime leaves the workout IN_PROGRESS for the save screen to finish`() = runTest {
        val repo = repo()
        recovery(repo).keepElapsedTime("run1")
        assertEquals(WorkoutStatus.IN_PROGRESS, repo.getById("run1")?.status)
    }

    @Test
    fun `keepElapsedTime reports failure for a workout that is already gone`() = runTest {
        assertEquals(false, recovery(repo()).keepElapsedTime("nope"))
    }

    @Test
    fun `discard removes the workout entirely`() = runTest {
        val repo = repo()
        recovery(repo).discard("run1")
        assertNull(repo.getById("run1"))
    }
}
