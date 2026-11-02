package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** W1-02: core workout totals for the post-workout summary. */
class WorkoutTotalsCalculatorTest {
    private fun set(
        id: String,
        reps: Int? = 8,
        weightKg: Double? = 60.0,
        distanceMeters: Double? = null,
        setType: SetType = SetType.NORMAL,
        isCompleted: Boolean = true,
    ) = StatSet(
        setId = id, workoutId = "w1", workoutStartedAt = 1_000L, orderIndex = 0, setType = setType,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = distanceMeters,
        customMetric = null, isCompleted = isCompleted,
    )

    @Test
    fun `counts completed sets and sums their reps`() {
        val totals = WorkoutTotalsCalculator.totals(
            listOf(set("s1", reps = 8), set("s2", reps = 10), set("s3", reps = 12)),
            includeWarmupsInStats = false,
        )
        assertEquals(3, totals.setCount)
        assertEquals(30, totals.totalReps)
    }

    @Test
    fun `drop sets and failure sets count like normal sets`() {
        val totals = WorkoutTotalsCalculator.totals(
            listOf(
                set("s1", reps = 8),
                set("s2", reps = 6, setType = SetType.DROPSET),
                set("s3", reps = 4, setType = SetType.FAILURE),
            ),
            includeWarmupsInStats = false,
        )
        assertEquals(3, totals.setCount)
        assertEquals(18, totals.totalReps)
    }

    @Test
    fun `warm-ups are left out when the setting is off`() {
        val totals = WorkoutTotalsCalculator.totals(
            listOf(set("s1", reps = 8), set("s2", reps = 15, setType = SetType.WARMUP)),
            includeWarmupsInStats = false,
        )
        assertEquals(1, totals.setCount)
        assertEquals(8, totals.totalReps)
    }

    @Test
    fun `warm-ups count when the setting is on`() {
        val totals = WorkoutTotalsCalculator.totals(
            listOf(set("s1", reps = 8), set("s2", reps = 15, setType = SetType.WARMUP)),
            includeWarmupsInStats = true,
        )
        assertEquals(2, totals.setCount)
        assertEquals(23, totals.totalReps)
    }

    @Test
    fun `unfinished sets never count, however heavy`() {
        val totals = WorkoutTotalsCalculator.totals(
            listOf(set("s1", reps = 8), set("s2", reps = 20, weightKg = 200.0, isCompleted = false)),
            includeWarmupsInStats = true,
        )
        assertEquals(1, totals.setCount)
        assertEquals(8, totals.totalReps)
    }

    @Test
    fun `a set with no reps adds zero reps but still counts as a set`() {
        val totals = WorkoutTotalsCalculator.totals(
            listOf(set("s1", reps = null), set("s2", reps = 5)),
            includeWarmupsInStats = false,
        )
        assertEquals(2, totals.setCount)
        assertEquals(5, totals.totalReps)
        assertTrue(totals.hasReps)
    }

    @Test
    fun `a walk or run logs only distance, so reps and weight are flagged as not applicable`() {
        val totals = WorkoutTotalsCalculator.totals(
            listOf(set("s1", reps = null, weightKg = null, distanceMeters = 2_000.0)),
            includeWarmupsInStats = false,
        )
        assertFalse(totals.hasReps)
        assertFalse(totals.hasWeight)
        assertTrue(totals.hasDistance)
        assertEquals(2_000.0, totals.totalDistanceMeters, 1e-9)
    }

    @Test
    fun `a strength workout has reps and weight but no distance`() {
        val totals = WorkoutTotalsCalculator.totals(listOf(set("s1")), includeWarmupsInStats = false)
        assertTrue(totals.hasReps)
        assertTrue(totals.hasWeight)
        assertFalse(totals.hasDistance)
        assertEquals(0.0, totals.totalDistanceMeters, 1e-9)
    }

    @Test
    fun `distance from several sets adds up`() {
        val totals = WorkoutTotalsCalculator.totals(
            listOf(
                set("s1", reps = null, weightKg = null, distanceMeters = 1_000.0),
                set("s2", reps = null, weightKg = null, distanceMeters = 500.0),
            ),
            includeWarmupsInStats = false,
        )
        assertEquals(1_500.0, totals.totalDistanceMeters, 1e-9)
    }

    @Test
    fun `no sets means all zeros and nothing applicable`() {
        val totals = WorkoutTotalsCalculator.totals(emptyList(), includeWarmupsInStats = false)
        assertEquals(0, totals.setCount)
        assertEquals(0, totals.totalReps)
        assertEquals(0.0, totals.totalDistanceMeters, 1e-9)
        assertFalse(totals.hasWeight)
        assertFalse(totals.hasReps)
        assertFalse(totals.hasDistance)
    }
}
