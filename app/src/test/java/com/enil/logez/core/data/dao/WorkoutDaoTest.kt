package com.enil.logez.core.data.dao

import com.enil.logez.core.data.RoomDatabaseTestBase
import com.enil.logez.core.data.entity.ExerciseEntity
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Robolectric 4.13's max supported SDK is 34; this app targets 36 (Fiterval precedent — same pin).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutDaoTest : RoomDatabaseTestBase() {
    private val dao get() = database.workoutDao()
    private val exerciseDao get() = database.exerciseDao()
    private val routineDao get() = database.routineDao()

    private suspend fun anExercise(id: String = "ex-1") {
        exerciseDao.insertIgnore(
            listOf(
                ExerciseEntity(
                    id = id, name = "Bench Press (Barbell)", exerciseType = ExerciseType.WEIGHT_REPS,
                    primaryMuscleGroup = MuscleGroup.CHEST, secondaryMuscleGroups = emptyList(),
                    equipment = Equipment.BARBELL, instructions = "x", mediaPath = null, isCustom = false,
                    isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
                ),
            ),
        )
    }

    private fun aWorkout(id: String, status: WorkoutStatus, routineId: String? = null) = WorkoutEntity(
        id = id, routineId = routineId, title = "Push Day", notes = null, status = status,
        startedAt = 1_000L, endedAt = if (status == WorkoutStatus.COMPLETED) 4_600L else null,
        durationSeconds = 3_600, createdAt = 1_000L, updatedAt = 1_000L,
    )

    @Test
    fun `getInProgress finds the single IN_PROGRESS workout`() = runTest {
        dao.upsertWorkout(aWorkout("w1", WorkoutStatus.COMPLETED))
        dao.upsertWorkout(aWorkout("w2", WorkoutStatus.IN_PROGRESS))

        assertEquals("w2", dao.getInProgress()?.id)
    }

    @Test
    fun `deleting a workout cascades to its exercises and sets`() = runTest {
        anExercise()
        dao.insertFullWorkout(
            workout = aWorkout("w1", WorkoutStatus.COMPLETED),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we1", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = listOf(
                WorkoutSetEntity(id = "ws1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 2_000L),
            ),
        )

        dao.deleteWorkoutById("w1")

        assertTrue(dao.getExercisesForWorkout("w1").isEmpty())
        assertTrue(dao.getSetsForWorkoutExercise("we1").isEmpty())
    }

    @Test
    fun `deleting a routine SETs NULL on the workouts started from it`() = runTest {
        routineDao.upsertRoutine(RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0))
        dao.upsertWorkout(aWorkout("w1", WorkoutStatus.COMPLETED, routineId = "r1"))

        routineDao.deleteRoutineById("r1")

        assertNull(dao.getById("w1")?.routineId)
    }

    @Test
    fun `getStatRowsForExercise joins completed workouts only and includes rpe`() = runTest {
        anExercise()
        dao.insertFullWorkout(
            workout = aWorkout("w-completed", WorkoutStatus.COMPLETED),
            exercises = listOf(WorkoutExerciseEntity(id = "we1", workoutId = "w-completed", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "ws1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = 8.5, customMetric = null, isCompleted = true, completedAt = 2_000L)),
        )
        dao.insertFullWorkout(
            workout = aWorkout("w-in-progress", WorkoutStatus.IN_PROGRESS),
            exercises = listOf(WorkoutExerciseEntity(id = "we2", workoutId = "w-in-progress", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(WorkoutSetEntity(id = "ws2", workoutExerciseId = "we2", orderIndex = 0, setType = SetType.NORMAL, weightKg = 999.0, reps = 1, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 2_000L)),
        )

        val rows = dao.getStatRowsForExercise("ex-1")

        assertEquals(1, rows.size) // the IN_PROGRESS workout's set never appears
        assertEquals("ws1", rows.first().setId)
        assertEquals(8.5, rows.first().rpe)
    }

    @Test
    fun `getSetsWithExerciseForWorkout distinguishes two blocks of the same exercise`() = runTest {
        anExercise()
        dao.insertFullWorkout(
            workout = aWorkout("w1", WorkoutStatus.COMPLETED),
            exercises = listOf(
                WorkoutExerciseEntity(id = "we-main", workoutId = "w1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
                WorkoutExerciseEntity(id = "we-burnout", workoutId = "w1", exerciseId = "ex-1", orderIndex = 1, supersetGroup = null, restTimerSeconds = null, notes = null),
            ),
            sets = listOf(
                aSet("s-main", "we-main", 0, 100.0, 5),
                aSet("s-burn", "we-burnout", 0, 40.0, 20),
            ),
        )

        val rows = dao.getSetsWithExerciseForWorkout("w1")

        // Both sets share exerciseId AND orderIndex — only the block identity tells them apart.
        assertEquals(listOf("we-main", "we-burnout"), rows.map { it.workoutExerciseId })
        assertEquals(listOf(0, 1), rows.map { it.exerciseOrderIndex })
    }

    @Test
    fun `countCompletedWorkoutsUpTo gives tied timestamps distinct ordinals`() = runTest {
        // A plain `started_at <= x` counted both sides of a tie, so two workouts saved at the same
        // instant both reported N and the ordinal N-1 was never assigned to anything.
        dao.upsertWorkout(aWorkout("w-a", WorkoutStatus.COMPLETED))
        dao.upsertWorkout(aWorkout("w-b", WorkoutStatus.COMPLETED))

        val a = dao.countCompletedWorkoutsUpTo(1_000L, "w-a")
        val b = dao.countCompletedWorkoutsUpTo(1_000L, "w-b")

        assertEquals(1, a)
        assertEquals(2, b)
    }

    @Test
    fun `countCompletedWorkoutsUpTo ignores later and non-completed workouts`() = runTest {
        dao.upsertWorkout(aWorkout("w-old", WorkoutStatus.COMPLETED).copy(startedAt = 500L))
        dao.upsertWorkout(aWorkout("w-this", WorkoutStatus.COMPLETED))
        dao.upsertWorkout(aWorkout("w-later", WorkoutStatus.COMPLETED).copy(startedAt = 9_000L))
        dao.upsertWorkout(aWorkout("w-live", WorkoutStatus.IN_PROGRESS))

        assertEquals(2, dao.countCompletedWorkoutsUpTo(1_000L, "w-this"))
    }

    private fun aSet(id: String, workoutExerciseId: String, orderIndex: Int, weightKg: Double, reps: Int) =
        WorkoutSetEntity(
            id = id, workoutExerciseId = workoutExerciseId, orderIndex = orderIndex, setType = SetType.NORMAL,
            weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null, rpe = null,
            customMetric = null, isCompleted = true, completedAt = 2_000L,
        )
}
