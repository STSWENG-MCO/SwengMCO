package com.enil.logez.feature.history

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** PHASE2_PLAN.md §5.2 "Save as Routine": build a routine from a past workout's structure. */
class WorkoutToRoutineConverterTest {
    @Test
    fun `the routine takes the workout's title, order, and logged values as targets`() = runTest {
        val f = fixture(
            exercises = listOf(we("we1", "ex-1", 0), we("we2", "ex-2", 1)),
            sets = listOf(
                set("s1", "we1", 0, 100.0, 5),
                set("s2", "we1", 1, 100.0, 4),
                set("s3", "we2", 0, 60.0, 10),
            ),
        )

        val routineId = f.converter.convert("w1")!!

        val routine = f.routineRepo.getRoutineById(routineId)!!
        assertEquals("Push Day", routine.name)
        val exercises = f.routineRepo.getExercisesForRoutine(routineId).sortedBy { it.orderIndex }
        assertEquals(listOf("ex-1", "ex-2"), exercises.map { it.exerciseId })

        val benchSets = f.routineRepo.getSetsForRoutineExercise(exercises[0].id).sortedBy { it.orderIndex }
        assertEquals(2, benchSets.size)
        assertEquals(100.0, benchSets[0].targetWeightKg!!, 1e-9)
        assertEquals(5, benchSets[0].targetReps)
        assertEquals(4, benchSets[1].targetReps)
    }

    @Test
    fun `a session cannot express a rep range, so the routine gets none rather than an invented one`() = runTest {
        val f = fixture(
            exercises = listOf(we("we1", "ex-1", 0)),
            sets = listOf(set("s1", "we1", 0, 100.0, 8)),
        )

        val routineId = f.converter.convert("w1")!!

        val exercise = f.routineRepo.getExercisesForRoutine(routineId).single()
        val routineSet = f.routineRepo.getSetsForRoutineExercise(exercise.id).single()
        assertEquals(8, routineSet.targetReps)
        assertNull(routineSet.targetRepRangeMin)
        assertNull(routineSet.targetRepRangeMax)
    }

    @Test
    fun `the session note does not become the routine's standing coaching note`() = runTest {
        val f = fixture(
            exercises = listOf(we("we1", "ex-1", 0, notes = "shoulder tight today")),
            sets = listOf(set("s1", "we1", 0, 100.0, 5)),
        )

        val routineId = f.converter.convert("w1")!!

        assertNull(f.routineRepo.getExercisesForRoutine(routineId).single().notes)
    }

    @Test
    fun `supersets carry over, but a group left with one member is cleaned up`() = runTest {
        val f = fixture(
            exercises = listOf(
                we("we1", "ex-1", 0, supersetGroup = 1),
                we("we2", "ex-2", 1, supersetGroup = 1),
                we("we3", "ex-3", 2, supersetGroup = 2), // partner never made it into the saved workout
            ),
            sets = listOf(
                set("s1", "we1", 0, 100.0, 5),
                set("s2", "we2", 0, 60.0, 10),
                set("s3", "we3", 0, 40.0, 12),
            ),
        )

        val routineId = f.converter.convert("w1")!!

        val exercises = f.routineRepo.getExercisesForRoutine(routineId).sortedBy { it.orderIndex }
        assertEquals(1, exercises[0].supersetGroup)
        assertEquals(1, exercises[1].supersetGroup)
        assertNull("a one-member superset group is not a valid routine state", exercises[2].supersetGroup)
    }

    @Test
    fun `rest timers carry over`() = runTest {
        val f = fixture(
            exercises = listOf(we("we1", "ex-1", 0, restTimerSeconds = 120)),
            sets = listOf(set("s1", "we1", 0, 100.0, 5)),
        )

        val routineId = f.converter.convert("w1")!!

        assertEquals(120, f.routineRepo.getExercisesForRoutine(routineId).single().restTimerSeconds)
    }

    @Test
    fun `a workout with no exercises produces no routine`() = runTest {
        val f = fixture(exercises = emptyList(), sets = emptyList())

        assertNull(f.converter.convert("w1"))
    }

    @Test
    fun `a missing workout produces no routine`() = runTest {
        val f = fixture(exercises = emptyList(), sets = emptyList())

        assertNull(f.converter.convert("does-not-exist"))
    }

    @Test
    fun `the new routine lands unfiled at the top rather than inside some folder`() = runTest {
        val f = fixture(
            exercises = listOf(we("we1", "ex-1", 0)),
            sets = listOf(set("s1", "we1", 0, 100.0, 5)),
        )

        val routineId = f.converter.convert("w1")!!

        val routine = f.routineRepo.getRoutineById(routineId)!!
        assertNull(routine.folderId)
        assertEquals(0, routine.orderIndex)
        assertNotNull(f.routineRepo.getRoutineById(routineId))
    }

    // --- M11 circuits ---

    @Test
    fun `a circuit workout saves as a circuit routine with rounds equal to the workout's row counts`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(
                WorkoutEntity(
                    id = "w1", routineId = null, title = "Conditioning", notes = null,
                    status = WorkoutStatus.COMPLETED, startedAt = 1_000L, endedAt = 5_000L,
                    durationSeconds = 4000, createdAt = 1_000L, updatedAt = 1_000L,
                    structure = WorkoutStructure.CIRCUIT,
                ),
            ),
            exercises = listOf(we("we1", "ex-1", 0), we("we2", "ex-2", 1)),
            sets = listOf(
                set("s1", "we1", 0, 24.0, 15),
                set("s2", "we1", 1, 24.0, 12),
                set("s3", "we2", 0, 16.0, 10),
                set("s4", "we2", 1, 16.0, 10),
            ),
        )
        val routineRepo = FakeRoutineRepository()
        val converter = WorkoutToRoutineConverter(workoutRepo, routineRepo, FakeClock(currentMillis = 9_000L))

        val routineId = converter.convert("w1")!!

        val routine = routineRepo.getRoutineById(routineId)!!
        assertEquals(WorkoutStructure.CIRCUIT, routine.structure)
        val exercises = routineRepo.getExercisesForRoutine(routineId).sortedBy { it.orderIndex }
        // Rounds = the workout's per-exercise row counts, carried as ordinary per-round set rows.
        assertEquals(2, routineRepo.getSetsForRoutineExercise(exercises[0].id).size)
        assertEquals(2, routineRepo.getSetsForRoutineExercise(exercises[1].id).size)
    }

    // --- fixture ---

    private class Fixture(val converter: WorkoutToRoutineConverter, val routineRepo: FakeRoutineRepository)

    private fun fixture(exercises: List<WorkoutExerciseEntity>, sets: List<WorkoutSetEntity>): Fixture {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(
                WorkoutEntity(
                    id = "w1", routineId = null, title = "Push Day", notes = "felt strong",
                    status = WorkoutStatus.COMPLETED, startedAt = 1_000L, endedAt = 5_000L,
                    durationSeconds = 4000, createdAt = 1_000L, updatedAt = 1_000L,
                ),
            ),
            exercises = exercises,
            sets = sets,
        )
        val routineRepo = FakeRoutineRepository()
        return Fixture(WorkoutToRoutineConverter(workoutRepo, routineRepo, FakeClock(currentMillis = 9_000L)), routineRepo)
    }

    private fun we(
        id: String,
        exerciseId: String,
        orderIndex: Int,
        supersetGroup: Int? = null,
        restTimerSeconds: Int? = null,
        notes: String? = null,
    ) = WorkoutExerciseEntity(
        id = id, workoutId = "w1", exerciseId = exerciseId, orderIndex = orderIndex,
        supersetGroup = supersetGroup, restTimerSeconds = restTimerSeconds, notes = notes,
    )

    private fun set(id: String, workoutExerciseId: String, orderIndex: Int, weightKg: Double, reps: Int) =
        WorkoutSetEntity(
            id = id, workoutExerciseId = workoutExerciseId, orderIndex = orderIndex, setType = SetType.NORMAL,
            weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null, rpe = null,
            customMetric = null, isCompleted = true, completedAt = 1L,
        )
}
