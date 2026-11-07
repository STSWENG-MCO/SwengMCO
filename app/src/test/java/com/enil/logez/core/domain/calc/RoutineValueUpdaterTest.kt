package com.enil.logez.core.domain.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * §8.10's own published test-vector table for "Update Routine Values", one test per row, plus
 * the rep-range invariant that §6's M4c checklist calls out separately ("rep-range targets never
 * auto-update").
 */
class RoutineValueUpdaterTest {
    @Test
    fun `exact-reps targets take the logged weight and reps`() {
        val result = RoutineValueUpdater.updatedTargets(
            routineSets = listOf(target(orderIndex = 0, weightKg = 100.0, reps = 5)),
            loggedSets = listOf(logged(orderIndex = 0, weightKg = 105.0, reps = 6)),
        )
        assertEquals(105.0, result.single().targetWeightKg!!, 1e-9)
        assertEquals(6, result.single().targetReps)
    }

    @Test
    fun `a rep-range target keeps its range and never gains an exact targetReps`() {
        val result = RoutineValueUpdater.updatedTargets(
            routineSets = listOf(target(orderIndex = 0, weightKg = 100.0, reps = null, rangeMin = 6, rangeMax = 8)),
            loggedSets = listOf(logged(orderIndex = 0, weightKg = 105.0, reps = 7)),
        ).single()

        // Weight still updates — only the reps half of the target is frozen.
        assertEquals(105.0, result.targetWeightKg!!, 1e-9)
        assertNull(result.targetReps)
        assertEquals(6, result.targetRepRangeMin)
        assertEquals(8, result.targetRepRangeMax)
    }

    @Test
    fun `an uncompleted logged set updates nothing`() {
        val result = RoutineValueUpdater.updatedTargets(
            routineSets = listOf(target(orderIndex = 0, weightKg = 100.0, reps = 5)),
            loggedSets = listOf(logged(orderIndex = 0, weightKg = 105.0, reps = 6, isCompleted = false)),
        ).single()

        assertEquals(100.0, result.targetWeightKg!!, 1e-9)
        assertEquals(5, result.targetReps)
    }

    @Test
    fun `extra logged sets beyond the routine's own are ignored`() {
        // §8.10: structural differences are the separate Update-Routine prompt's territory.
        val result = RoutineValueUpdater.updatedTargets(
            routineSets = listOf(
                target(orderIndex = 0, weightKg = 100.0, reps = 5),
                target(orderIndex = 1, weightKg = 100.0, reps = 5),
            ),
            loggedSets = listOf(
                logged(orderIndex = 0, weightKg = 105.0, reps = 6),
                logged(orderIndex = 1, weightKg = 106.0, reps = 6),
                logged(orderIndex = 2, weightKg = 107.0, reps = 6),
            ),
        )

        assertEquals(2, result.size)
        assertEquals(105.0, result[0].targetWeightKg!!, 1e-9)
        assertEquals(106.0, result[1].targetWeightKg!!, 1e-9)
    }

    @Test
    fun `a routine set with no matching logged set is left untouched`() {
        val result = RoutineValueUpdater.updatedTargets(
            routineSets = listOf(
                target(orderIndex = 0, weightKg = 100.0, reps = 5),
                target(orderIndex = 1, weightKg = 100.0, reps = 5),
            ),
            loggedSets = listOf(logged(orderIndex = 0, weightKg = 105.0, reps = 6)),
        )

        assertEquals(105.0, result[0].targetWeightKg!!, 1e-9)
        assertEquals(100.0, result[1].targetWeightKg!!, 1e-9)
    }

    @Test
    fun `duration and distance targets update alongside weight`() {
        val result = RoutineValueUpdater.updatedTargets(
            routineSets = listOf(target(orderIndex = 0, durationSeconds = 60, distanceMeters = 400.0)),
            loggedSets = listOf(logged(orderIndex = 0, durationSeconds = 75, distanceMeters = 500.0)),
        ).single()

        assertEquals(75, result.targetDurationSeconds)
        assertEquals(500.0, result.targetDistanceMeters!!, 1e-9)
    }

    private fun target(
        orderIndex: Int,
        weightKg: Double? = null,
        reps: Int? = null,
        rangeMin: Int? = null,
        rangeMax: Int? = null,
        durationSeconds: Int? = null,
        distanceMeters: Double? = null,
    ) = RoutineSetTargets(orderIndex, weightKg, reps, rangeMin, rangeMax, durationSeconds, distanceMeters)

    private fun logged(
        orderIndex: Int,
        weightKg: Double? = null,
        reps: Int? = null,
        durationSeconds: Int? = null,
        distanceMeters: Double? = null,
        isCompleted: Boolean = true,
    ) = LoggedSetValues(orderIndex, weightKg, reps, durationSeconds, distanceMeters, isCompleted)
}
