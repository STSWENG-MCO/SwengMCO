package com.enil.logez.feature.workout

import com.enil.logez.core.domain.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M11: the circuit logger's round-first grouping and check-off scroll targeting — pure functions. */
class CircuitGroupingTest {
    private fun set(id: String, completed: Boolean = false) = WorkoutSetUiModel(id = id, isCompleted = completed)

    private fun exercise(id: String, sets: List<WorkoutSetUiModel>) = WorkoutExerciseUiModel(
        id = id, exerciseId = "x-$id", exerciseName = id, exerciseType = ExerciseType.WEIGHT_REPS, sets = sets,
    )

    @Test
    fun `equal set counts group into rectangular rounds in sequence order`() {
        val exercises = listOf(
            exercise("a", listOf(set("a0"), set("a1"))),
            exercise("b", listOf(set("b0"), set("b1"))),
        )

        val rounds = buildCircuitRounds(exercises)

        assertEquals(2, rounds.size)
        assertEquals(1, rounds[0].roundNumber)
        assertEquals(listOf("a", "b"), rounds[0].entries.map { it.exercise.id })
        assertEquals(listOf("a0", "b0"), rounds[0].entries.map { it.set!!.id })
        assertEquals(listOf("a1", "b1"), rounds[1].entries.map { it.set!!.id })
    }

    @Test
    fun `unequal set counts render defensively - short exercises get null slots, nothing is hidden or reflowed`() {
        val exercises = listOf(
            exercise("a", listOf(set("a0"), set("a1"), set("a2"))),
            exercise("b", listOf(set("b0"))),
        )

        val rounds = buildCircuitRounds(exercises)

        // MAX drives the round count, so every logged row stays visible.
        assertEquals(3, rounds.size)
        // Round 2/3: b contributes an inert slot, and a's rows keep their true indices.
        assertEquals("a1", rounds[1].entries[0].set!!.id)
        assertNull(rounds[1].entries[1].set)
        assertEquals("a2", rounds[2].entries[0].set!!.id)
        assertNull(rounds[2].entries[1].set)
        // Every round still lists every exercise, in sequence order.
        assertTrue(rounds.all { it.entries.map { e -> e.exercise.id } == listOf("a", "b") })
    }

    @Test
    fun `no exercises means no rounds`() {
        assertEquals(0, circuitRoundCount(emptyList()))
        assertEquals(emptyList<CircuitRound>(), buildCircuitRounds(emptyList()))
    }

    @Test
    fun `next incomplete position stays inside the same round first`() {
        val exercises = listOf(
            exercise("a", listOf(set("a0", completed = true), set("a1"))),
            exercise("b", listOf(set("b0"), set("b1"))),
        )

        // Just checked a0 (round 0): b0 in the same round is still open.
        assertEquals(0, nextIncompleteCircuitPosition(exercises, fromRoundIndex = 0, fromExerciseId = "a"))
    }

    @Test
    fun `next incomplete position wraps into the next round's first incomplete row`() {
        val exercises = listOf(
            exercise("a", listOf(set("a0", completed = true), set("a1"))),
            exercise("b", listOf(set("b0", completed = true), set("b1"))),
        )

        // Just checked b0 (last of round 0): round 1 is next.
        assertEquals(1, nextIncompleteCircuitPosition(exercises, fromRoundIndex = 0, fromExerciseId = "b"))
    }

    @Test
    fun `next incomplete position wraps around to an earlier round when later ones are done`() {
        val exercises = listOf(
            exercise("a", listOf(set("a0"), set("a1", completed = true))),
            exercise("b", listOf(set("b0", completed = true), set("b1", completed = true))),
        )

        // Just checked b1 (end of the circuit): the only open row is a0, back in round 0.
        assertEquals(0, nextIncompleteCircuitPosition(exercises, fromRoundIndex = 1, fromExerciseId = "b"))
    }

    @Test
    fun `next incomplete position is null once everything is checked`() {
        val exercises = listOf(
            exercise("a", listOf(set("a0", completed = true))),
            exercise("b", listOf(set("b0", completed = true))),
        )

        assertNull(nextIncompleteCircuitPosition(exercises, fromRoundIndex = 0, fromExerciseId = "b"))
    }

    @Test
    fun `next incomplete position skips defensive null slots without crashing`() {
        val exercises = listOf(
            exercise("a", listOf(set("a0", completed = true), set("a1"))),
            exercise("b", listOf(set("b0", completed = true))), // no row in round 2
        )

        assertEquals(1, nextIncompleteCircuitPosition(exercises, fromRoundIndex = 0, fromExerciseId = "b"))
    }
}
