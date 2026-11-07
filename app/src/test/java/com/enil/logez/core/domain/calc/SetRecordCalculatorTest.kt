package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.calc.SetRecordCalculator.SetRecord
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** PHASE2_PLAN.md §8.5 literal test vectors, landing with the engine's consuming milestone (M6a) per §10.6. */
class SetRecordCalculatorTest {
    private fun set(id: String, weightKg: Double, reps: Int, setType: SetType = SetType.NORMAL, isCompleted: Boolean = true) = StatSet(
        setId = id, workoutId = "w1", workoutStartedAt = 1_000L, orderIndex = 0, setType = setType,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null,
        customMetric = null, isCompleted = isCompleted,
    )

    /** §8.5's shared fixture: [NORMAL 100.0x5, NORMAL 90.0x5, NORMAL 105.0x3, WARMUP 100.0x8, NORMAL 105.0x5]. */
    private val vectorSets = listOf(
        set("s1", 100.0, 5),
        set("s2", 90.0, 5),
        set("s3", 105.0, 3),
        set("s4", 100.0, 8, setType = SetType.WARMUP),
        set("s5", 105.0, 5),
    )

    @Test
    fun `warm-ups excluded - records are (3, 105) and (5, 105)`() {
        assertEquals(
            listOf(SetRecord(3, 105.0), SetRecord(5, 105.0)),
            SetRecordCalculator.setRecords(ExerciseType.WEIGHT_REPS, vectorSets, includeWarmupsInStats = false),
        )
    }

    @Test
    fun `warm-ups included - the 8-rep warm-up row appears`() {
        assertEquals(
            listOf(SetRecord(3, 105.0), SetRecord(5, 105.0), SetRecord(8, 100.0)),
            SetRecordCalculator.setRecords(ExerciseType.WEIGHT_REPS, vectorSets, includeWarmupsInStats = true),
        )
    }

    @Test
    fun `an incomplete set is ignored no matter how heavy`() {
        val withIncomplete = vectorSets + set("s6", 200.0, 5, isCompleted = false)
        assertEquals(
            listOf(SetRecord(3, 105.0), SetRecord(5, 105.0)),
            SetRecordCalculator.setRecords(ExerciseType.WEIGHT_REPS, withIncomplete, includeWarmupsInStats = false),
        )
    }

    @Test
    fun `empty input yields no records`() {
        assertTrue(SetRecordCalculator.setRecords(ExerciseType.WEIGHT_REPS, emptyList(), includeWarmupsInStats = false).isEmpty())
    }

    @Test
    fun `only the two weight-and-reps types get set records - assisted bodyweight is excluded`() {
        assertTrue(SetRecordCalculator.setRecords(ExerciseType.BODYWEIGHT_ASSISTED, vectorSets, includeWarmupsInStats = false).isEmpty())
        assertTrue(SetRecordCalculator.setRecords(ExerciseType.REPS_ONLY, vectorSets, includeWarmupsInStats = false).isEmpty())
        assertTrue(SetRecordCalculator.setRecords(ExerciseType.DURATION, vectorSets, includeWarmupsInStats = false).isEmpty())
        assertEquals(2, SetRecordCalculator.setRecords(ExerciseType.BODYWEIGHT_WEIGHTED, vectorSets, includeWarmupsInStats = false).size)
    }
}
