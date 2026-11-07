package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeCalculatorTest {

    // --- WEIGHT_REPS ---

    @Test
    fun `WEIGHT_REPS volume is weight times reps`() {
        val v = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.WEIGHT_REPS, isBodyweightVolumeEligible = false,
            weightKg = 100.0, reps = 5, bodyweightKg = null,
        )
        assertEquals(500.0, v, 0.0)
    }

    @Test
    fun `WEIGHT_REPS with a null weight is zero, not a crash`() {
        val v = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.WEIGHT_REPS, isBodyweightVolumeEligible = false,
            weightKg = null, reps = 5, bodyweightKg = null,
        )
        assertEquals(0.0, v, 0.0)
    }

    @Test
    fun `a null reps count is zero volume regardless of exercise type`() {
        val v = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.WEIGHT_REPS, isBodyweightVolumeEligible = false,
            weightKg = 100.0, reps = null, bodyweightKg = null,
        )
        assertEquals(0.0, v, 0.0)
    }

    // --- REPS_ONLY (bodyweight movements with no added load, e.g. bodyweight squats) ---

    @Test
    fun `REPS_ONLY eligible for bodyweight volume uses the logged bodyweight`() {
        val v = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.REPS_ONLY, isBodyweightVolumeEligible = true,
            weightKg = null, reps = 10, bodyweightKg = 80.0,
        )
        assertEquals(800.0, v, 0.0)
    }

    @Test
    fun `REPS_ONLY eligible but with no bodyweight on file is zero, not a crash`() {
        val v = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.REPS_ONLY, isBodyweightVolumeEligible = true,
            weightKg = null, reps = 10, bodyweightKg = null,
        )
        assertEquals(0.0, v, 0.0)
    }

    @Test
    fun `REPS_ONLY not eligible for bodyweight volume is always zero`() {
        val v = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.REPS_ONLY, isBodyweightVolumeEligible = false,
            weightKg = null, reps = 10, bodyweightKg = 80.0,
        )
        assertEquals(0.0, v, 0.0)
    }

    // --- BODYWEIGHT_WEIGHTED (e.g. weighted pull-ups/dips) ---

    @Test
    fun `BODYWEIGHT_WEIGHTED eligible adds bodyweight plus the added weight`() {
        val v = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.BODYWEIGHT_WEIGHTED, isBodyweightVolumeEligible = true,
            weightKg = 20.0, reps = 8, bodyweightKg = 80.0,
        )
        assertEquals(800.0, v, 0.0) // (80 + 20) * 8
    }

    @Test
    fun `BODYWEIGHT_WEIGHTED eligible with no bodyweight on file falls back to added weight alone`() {
        val v = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.BODYWEIGHT_WEIGHTED, isBodyweightVolumeEligible = true,
            weightKg = 20.0, reps = 8, bodyweightKg = null,
        )
        assertEquals(160.0, v, 0.0) // 20 * 8, bodyweight treated as 0 when unknown
    }

    @Test
    fun `BODYWEIGHT_WEIGHTED not eligible uses only the added weight, even with a bodyweight on file`() {
        val v = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.BODYWEIGHT_WEIGHTED, isBodyweightVolumeEligible = false,
            weightKg = 20.0, reps = 8, bodyweightKg = 80.0,
        )
        assertEquals(160.0, v, 0.0) // 20 * 8 -- bodyweight ignored entirely when not eligible
    }

    @Test
    fun `BODYWEIGHT_WEIGHTED with a null added weight is zero, not a crash`() {
        val v = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.BODYWEIGHT_WEIGHTED, isBodyweightVolumeEligible = true,
            weightKg = null, reps = 8, bodyweightKg = 80.0,
        )
        assertEquals(0.0, v, 0.0)
    }

    // --- BODYWEIGHT_ASSISTED (e.g. an assisted-pull-up machine) ---

    @Test
    fun `BODYWEIGHT_ASSISTED subtracts assistance from bodyweight`() {
        val v = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.BODYWEIGHT_ASSISTED, isBodyweightVolumeEligible = true,
            weightKg = 30.0, reps = 8, bodyweightKg = 80.0,
        )
        assertEquals(400.0, v, 0.0) // (80 - 30) * 8
    }

    @Test
    fun `BODYWEIGHT_ASSISTED clamps to zero when assistance meets or exceeds bodyweight, never negative`() {
        val atBodyweight = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.BODYWEIGHT_ASSISTED, isBodyweightVolumeEligible = true,
            weightKg = 80.0, reps = 8, bodyweightKg = 80.0,
        )
        val overBodyweight = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.BODYWEIGHT_ASSISTED, isBodyweightVolumeEligible = true,
            weightKg = 120.0, reps = 8, bodyweightKg = 80.0,
        )
        assertEquals(0.0, atBodyweight, 0.0)
        assertEquals(0.0, overBodyweight, 0.0)
    }

    @Test
    fun `BODYWEIGHT_ASSISTED not eligible is always zero`() {
        val v = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.BODYWEIGHT_ASSISTED, isBodyweightVolumeEligible = false,
            weightKg = 30.0, reps = 8, bodyweightKg = 80.0,
        )
        assertEquals(0.0, v, 0.0)
    }

    @Test
    fun `BODYWEIGHT_ASSISTED with no bodyweight on file is zero, not a crash`() {
        val v = VolumeCalculator.setVolume(
            exerciseType = ExerciseType.BODYWEIGHT_ASSISTED, isBodyweightVolumeEligible = true,
            weightKg = 30.0, reps = 8, bodyweightKg = null,
        )
        assertEquals(0.0, v, 0.0)
    }

    // --- No-load exercise types always contribute zero volume ---

    @Test
    fun `duration, distance, floors and step exercise types always contribute zero volume`() {
        val zeroVolumeTypes = listOf(
            ExerciseType.DURATION, ExerciseType.WEIGHT_DURATION, ExerciseType.DISTANCE_DURATION,
            ExerciseType.WEIGHT_DISTANCE, ExerciseType.FLOORS_DURATION, ExerciseType.STEPS_DURATION,
        )
        zeroVolumeTypes.forEach { type ->
            val v = VolumeCalculator.setVolume(
                exerciseType = type, isBodyweightVolumeEligible = false,
                weightKg = 999.0, reps = 999, bodyweightKg = 999.0,
            )
            assertEquals("$type should always be zero volume", 0.0, v, 0.0)
        }
    }

    // --- sessionVolume ---

    @Test
    fun `sessionVolume sums the given set volumes`() {
        assertEquals(150.0, VolumeCalculator.sessionVolume(listOf(50.0, 40.0, 60.0)), 0.0)
    }

    @Test
    fun `sessionVolume of an empty list is zero`() {
        assertEquals(0.0, VolumeCalculator.sessionVolume(emptyList()), 0.0)
    }
}
