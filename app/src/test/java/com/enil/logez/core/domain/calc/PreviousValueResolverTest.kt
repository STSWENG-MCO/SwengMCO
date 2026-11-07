package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviousValueResolverTest {
    private fun stat(setId: String, workoutId: String, routineId: String?, orderIndex: Int, reps: Int, startedAt: Long = 0) = StatSet(
        setId = setId, workoutId = workoutId, workoutStartedAt = startedAt, orderIndex = orderIndex, setType = SetType.NORMAL,
        weightKg = 50.0, reps = reps, durationSeconds = null, distanceMeters = null, customMetric = null,
        isCompleted = true, rpe = null, routineId = routineId,
    )

    @Test
    fun `ANY_WORKOUT returns the most recent workout's sets, ignoring routineId`() {
        // Rows already ordered most-recent-workout-first, as WorkoutDao.getStatRowsForExercise guarantees.
        val rows = listOf(
            stat("s1", "w2", "r-other", 0, 8),
            stat("s2", "w2", "r-other", 1, 7),
            stat("s3", "w1", "r-target", 0, 10),
        )

        val result = resolvePreviousWorkoutSets(rows, PreviousValuesMode.ANY_WORKOUT, currentRoutineId = "r-target")

        assertEquals(listOf("s1", "s2"), result.map { it.setId })
    }

    @Test
    fun `SAME_ROUTINE returns the most recent workout sharing the current routineId`() {
        val rows = listOf(
            stat("s1", "w3", "r-other", 0, 8), // most recent overall, but different routine
            stat("s2", "w2", "r-target", 0, 9),
            stat("s3", "w1", "r-target", 0, 10),
        )

        val result = resolvePreviousWorkoutSets(rows, PreviousValuesMode.SAME_ROUTINE, currentRoutineId = "r-target")

        assertEquals(listOf("s2"), result.map { it.setId })
    }

    @Test
    fun `SAME_ROUTINE with no matching routine returns empty, never falls back to ANY_WORKOUT`() {
        val rows = listOf(stat("s1", "w1", "r-other", 0, 8))

        val result = resolvePreviousWorkoutSets(rows, PreviousValuesMode.SAME_ROUTINE, currentRoutineId = "r-target")

        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty input returns empty regardless of mode`() {
        assertTrue(resolvePreviousWorkoutSets(emptyList(), PreviousValuesMode.ANY_WORKOUT, null).isEmpty())
    }

    @Test
    fun `beforeStartedAt excludes the workout being edited from its own PREVIOUS column`() {
        // §5.1.10: edit mode opens over a COMPLETED workout, so without the bound the resolver's
        // "most recent" is the very workout on screen — it would show its own values as last time's.
        val rows = listOf(
            stat("s-self", "w-editing", null, 0, 5, startedAt = 2_000L),
            stat("s-prior", "w-older", null, 0, 8, startedAt = 1_000L),
        )

        val unbounded = resolvePreviousWorkoutSets(rows, PreviousValuesMode.ANY_WORKOUT, null)
        val bounded = resolvePreviousWorkoutSets(rows, PreviousValuesMode.ANY_WORKOUT, null, beforeStartedAt = 2_000L)

        assertEquals(listOf("s-self"), unbounded.map { it.setId }) // what live logging wants
        assertEquals(listOf("s-prior"), bounded.map { it.setId }) // what edit mode wants
    }

    @Test
    fun `beforeStartedAt is strict, so a workout sharing the exact start instant is excluded`() {
        val rows = listOf(stat("s-tie", "w-tie", null, 0, 5, startedAt = 2_000L))

        val result = resolvePreviousWorkoutSets(rows, PreviousValuesMode.ANY_WORKOUT, null, beforeStartedAt = 2_000L)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `beforeStartedAt composes with SAME_ROUTINE rather than overriding it`() {
        val rows = listOf(
            stat("s-self", "w-editing", "r-target", 0, 5, startedAt = 3_000L),
            stat("s-other-routine", "w-b", "r-other", 0, 8, startedAt = 2_000L),
            stat("s-same-routine", "w-a", "r-target", 0, 10, startedAt = 1_000L),
        )

        val result = resolvePreviousWorkoutSets(rows, PreviousValuesMode.SAME_ROUTINE, "r-target", beforeStartedAt = 3_000L)

        assertEquals(listOf("s-same-routine"), result.map { it.setId })
    }
}
