package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutMuscleTargetCalculatorTest {
    @Test
    fun `primary and secondary targets are normalized against the strongest muscle`() {
        val result = WorkoutMuscleTargetCalculator.intensities(
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
        val result = WorkoutMuscleTargetCalculator.intensities(
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
}
