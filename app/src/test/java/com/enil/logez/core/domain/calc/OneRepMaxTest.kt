package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * §10.6's M4c row: "OneRepMaxTest (full 30-entry table — BEST_1RM detection depends on it)".
 * Every factor is asserted as a literal, never recomputed with the production formula (§10.1).
 */
class OneRepMaxTest {
    /** reps -> percentage-of-1RM factor, transcribed independently from PHASE2_PLAN.md §8.2. */
    private val expectedFactors = mapOf(
        1 to 1.00, 2 to 0.97, 3 to 0.94, 4 to 0.92, 5 to 0.89,
        6 to 0.86, 7 to 0.83, 8 to 0.81, 9 to 0.78, 10 to 0.75,
        11 to 0.73, 12 to 0.71, 13 to 0.70, 14 to 0.68, 15 to 0.67,
        16 to 0.65, 17 to 0.64, 18 to 0.63, 19 to 0.61, 20 to 0.60,
        21 to 0.59, 22 to 0.58, 23 to 0.57, 24 to 0.56, 25 to 0.55,
        26 to 0.54, 27 to 0.53, 28 to 0.52, 29 to 0.51, 30 to 0.50,
    )

    @Test
    fun `all 30 table entries divide the weight by the exact published factor`() {
        expectedFactors.forEach { (reps, factor) ->
            assertEquals("reps=$reps", 100.0 / factor, OneRepMax.estimate(100.0, reps), 1e-9)
        }
    }

    @Test
    fun `1 rep is the lift itself`() {
        assertEquals(140.0, OneRepMax.estimate(140.0, 1), 1e-9)
    }

    @Test
    fun `reps beyond the table clamp to the 0_50 factor rather than growing`() {
        val at30 = OneRepMax.estimate(100.0, 30)
        assertEquals(200.0, at30, 1e-9)
        assertEquals(at30, OneRepMax.estimate(100.0, 31), 1e-9)
        assertEquals(at30, OneRepMax.estimate(100.0, 500), 1e-9)
    }

    @Test
    fun `zero and negative reps return zero instead of throwing`() {
        assertEquals(0.0, OneRepMax.estimate(100.0, 0), 0.0)
        // Regression: PCT[reps - 1] would index -2 and throw. StatSet.reps is a nullable Int with
        // no non-negative constraint in the schema, so a negative is type-legal input.
        assertEquals(0.0, OneRepMax.estimate(100.0, -1), 0.0)
    }

    @Test
    fun `roundForDisplay keeps one decimal`() {
        assertEquals(102.6, OneRepMax.roundForDisplay(102.5641), 1e-9)
        assertEquals(100.0, OneRepMax.roundForDisplay(100.0), 1e-9)
    }

    @Test
    fun `bestPerWorkout picks the highest 1RM per workout and excludes warmups by default`() {
        val sets = listOf(
            statSet("s1", "w1", orderIndex = 0, weightKg = 100.0, reps = 5), // 112.36
            statSet("s2", "w1", orderIndex = 1, weightKg = 120.0, reps = 3), // 127.66 <- best
            statSet("s3", "w1", orderIndex = 2, weightKg = 200.0, reps = 1, setType = SetType.WARMUP), // excluded
            statSet("s4", "w2", orderIndex = 0, weightKg = 90.0, reps = 8),
        )

        val result = OneRepMax.bestPerWorkout(sets, includeWarmupsInStats = false)

        assertEquals(2, result.size)
        assertEquals(120.0 / 0.94, result.getValue("w1"), 1e-9)
        assertEquals(90.0 / 0.81, result.getValue("w2"), 1e-9)
    }

    @Test
    fun `bestSetPerWorkout breaks ties toward the earlier set so the record names its first achiever`() {
        val sets = listOf(
            statSet("later", "w1", orderIndex = 5, weightKg = 100.0, reps = 5),
            statSet("earlier", "w1", orderIndex = 1, weightKg = 100.0, reps = 5), // identical 1RM
        )

        val (winner, _) = OneRepMax.bestSetPerWorkout(sets, includeWarmupsInStats = false).getValue("w1")

        assertEquals("earlier", winner.setId)
    }

    @Test
    fun `incomplete sets never contribute`() {
        val sets = listOf(statSet("s1", "w1", orderIndex = 0, weightKg = 300.0, reps = 1, isCompleted = false))
        assertEquals(0, OneRepMax.bestPerWorkout(sets, includeWarmupsInStats = false).size)
    }

    private fun statSet(
        setId: String,
        workoutId: String,
        orderIndex: Int,
        weightKg: Double?,
        reps: Int?,
        setType: SetType = SetType.NORMAL,
        isCompleted: Boolean = true,
    ) = StatSet(
        setId = setId, workoutId = workoutId, workoutStartedAt = 0L, orderIndex = orderIndex,
        setType = setType, weightKg = weightKg, reps = reps, durationSeconds = null,
        distanceMeters = null, customMetric = null, isCompleted = isCompleted,
    )
}
