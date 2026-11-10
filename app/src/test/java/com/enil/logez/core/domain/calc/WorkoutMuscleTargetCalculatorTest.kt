package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMuscleTargetCalculatorTest {
    @Test
    fun `primary and secondary targets are normalized against the strongest muscle`() {
        val result =
            WorkoutMuscleTargetCalculator.intensities(
                listOf(
                    WorkoutMuscleTargetCalculator.TargetSet(MuscleGroup.CHEST, listOf(MuscleGroup.TRICEPS)),
                    WorkoutMuscleTargetCalculator.TargetSet(MuscleGroup.CHEST, listOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS)),
                ),
            )

        assertEquals(1f, result.getValue(MuscleGroup.CHEST), 0.001f)
        assertEquals(0.5f, result.getValue(MuscleGroup.TRICEPS), 0.001f)
        assertEquals(0.25f, result.getValue(MuscleGroup.SHOULDERS), 0.001f)
    }

    @Test
    fun `duplicate secondary groups and the primary repeated as secondary are counted once`() {
        val result =
            WorkoutMuscleTargetCalculator.intensities(
                listOf(
                    WorkoutMuscleTargetCalculator.TargetSet(
                        MuscleGroup.LATS,
                        listOf(MuscleGroup.BICEPS, MuscleGroup.BICEPS, MuscleGroup.LATS),
                    ),
                ),
            )

        assertEquals(1f, result.getValue(MuscleGroup.LATS), 0.001f)
        assertEquals(0.5f, result.getValue(MuscleGroup.BICEPS), 0.001f)
    }

    @Test fun `empty workout has no muscle intensity`() {
        assertTrue(WorkoutMuscleTargetCalculator.intensities(emptyList()).isEmpty())
    }

    // --- W2-01a: only drawable (MAPPABLE) groups reach the body diagram ---

    @Test
    fun `cardio, full body and other are never sent to the diagram`() {
        val result =
            WorkoutMuscleTargetCalculator.intensities(
                listOf(
                    WorkoutMuscleTargetCalculator.TargetSet(MuscleGroup.CARDIO, emptyList()),
                    WorkoutMuscleTargetCalculator.TargetSet(MuscleGroup.FULL_BODY, emptyList()),
                    WorkoutMuscleTargetCalculator.TargetSet(MuscleGroup.OTHER, emptyList()),
                    WorkoutMuscleTargetCalculator.TargetSet(MuscleGroup.CHEST, emptyList()),
                ),
            )

        assertEquals(setOf(MuscleGroup.CHEST), result.keys)
    }

    @Test
    fun `a non-drawable exercise does not make the drawable muscles look faint`() {
        // Two full-body sets and one chest set: if FULL_BODY were scored it would be the
        // "strongest" at 2.0 and chest would show at only half intensity.
        val result =
            WorkoutMuscleTargetCalculator.intensities(
                listOf(
                    WorkoutMuscleTargetCalculator.TargetSet(MuscleGroup.FULL_BODY, emptyList()),
                    WorkoutMuscleTargetCalculator.TargetSet(MuscleGroup.FULL_BODY, emptyList()),
                    WorkoutMuscleTargetCalculator.TargetSet(MuscleGroup.CHEST, emptyList()),
                ),
            )

        assertEquals(1f, result.getValue(MuscleGroup.CHEST), 0.001f)
    }

    @Test
    fun `drawable secondary muscles of a non-drawable exercise still count`() {
        // e.g. a run: primary CARDIO is dropped, its leg secondaries are kept.
        val result =
            WorkoutMuscleTargetCalculator.intensities(
                listOf(
                    WorkoutMuscleTargetCalculator.TargetSet(
                        MuscleGroup.CARDIO,
                        listOf(MuscleGroup.QUADRICEPS, MuscleGroup.CALVES),
                    ),
                ),
            )

        assertEquals(setOf(MuscleGroup.QUADRICEPS, MuscleGroup.CALVES), result.keys)
        assertEquals(1f, result.getValue(MuscleGroup.QUADRICEPS), 0.001f)
    }

    @Test
    fun `a non-drawable secondary muscle is dropped`() {
        val result =
            WorkoutMuscleTargetCalculator.intensities(
                listOf(WorkoutMuscleTargetCalculator.TargetSet(MuscleGroup.QUADRICEPS, listOf(MuscleGroup.CARDIO))),
            )

        assertEquals(setOf(MuscleGroup.QUADRICEPS), result.keys)
    }

    @Test
    fun `a workout of only non-drawable exercises gives an empty map, not a misleading highlight`() {
        val result =
            WorkoutMuscleTargetCalculator.intensities(
                listOf(
                    WorkoutMuscleTargetCalculator.TargetSet(MuscleGroup.CARDIO, emptyList()),
                    WorkoutMuscleTargetCalculator.TargetSet(MuscleGroup.FULL_BODY, listOf(MuscleGroup.OTHER)),
                ),
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `neck is drawable, so it is kept`() {
        val result =
            WorkoutMuscleTargetCalculator.intensities(
                listOf(WorkoutMuscleTargetCalculator.TargetSet(MuscleGroup.NECK, emptyList())),
            )

        assertEquals(mapOf(MuscleGroup.NECK to 1f), result)
    }

    @Test
    fun `mappable is every muscle group except cardio, full body and other`() {
        assertEquals(
            MuscleGroup.entries.toSet() - setOf(MuscleGroup.CARDIO, MuscleGroup.FULL_BODY, MuscleGroup.OTHER),
            WorkoutMuscleTargetCalculator.MAPPABLE,
        )
    }
}
