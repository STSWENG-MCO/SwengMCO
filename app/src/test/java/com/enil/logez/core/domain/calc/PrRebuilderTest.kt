package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** §8.4's derived-cache rebuild: one winner per PrType across an exercise's whole COMPLETED history. */
class PrRebuilderTest {
    @Test
    fun `picks the single best set across sessions for each set-scoped PrType`() {
        val sets = listOf(
            set("s1", "w1", startedAt = 1_000L, weightKg = 100.0, reps = 5),
            set("s2", "w2", startedAt = 2_000L, weightKg = 120.0, reps = 3),
            set("s3", "w3", startedAt = 3_000L, weightKg = 110.0, reps = 8),
        )

        val records = PrRebuilder.rebuild(ExerciseType.WEIGHT_REPS, sets, eligible = false, bodyweightByWorkoutId = emptyMap(), includeWarmupsInStats = false)
            .associateBy { it.prType }

        assertEquals(120.0, records.getValue(PrType.HEAVIEST_WEIGHT).value, 1e-9)
        assertEquals("s2", records.getValue(PrType.HEAVIEST_WEIGHT).workoutSetId)
        // Best set volume is 110*8=880, beating 100*5=500 and 120*3=360.
        assertEquals(880.0, records.getValue(PrType.BEST_SET_VOLUME).value, 1e-9)
        assertEquals("s3", records.getValue(PrType.BEST_SET_VOLUME).workoutSetId)
    }

    @Test
    fun `the earliest achiever keeps the record when a later session merely equals it`() {
        val sets = listOf(
            set("first", "w1", startedAt = 1_000L, weightKg = 100.0, reps = 5),
            set("second", "w2", startedAt = 2_000L, weightKg = 100.0, reps = 5),
        )

        val heaviest = PrRebuilder.rebuild(ExerciseType.WEIGHT_REPS, sets, eligible = false, bodyweightByWorkoutId = emptyMap(), includeWarmupsInStats = false)
            .first { it.prType == PrType.HEAVIEST_WEIGHT }

        assertEquals("first", heaviest.workoutSetId)
        assertEquals("w1", heaviest.workoutId)
        assertEquals(1_000L, heaviest.achievedAt)
    }

    @Test
    fun `session-scoped records name the workout but no single set`() {
        val sets = listOf(
            set("s1", "w1", startedAt = 1_000L, weightKg = 100.0, reps = 5), // session volume 500
            set("s2", "w2", startedAt = 2_000L, weightKg = 100.0, reps = 5),
            set("s3", "w2", startedAt = 2_000L, weightKg = 100.0, reps = 5), // session volume 1000 <- best
        )

        val sessionVolume = PrRebuilder.rebuild(ExerciseType.WEIGHT_REPS, sets, eligible = false, bodyweightByWorkoutId = emptyMap(), includeWarmupsInStats = false)
            .first { it.prType == PrType.BEST_SESSION_VOLUME }

        assertEquals(1000.0, sessionVolume.value, 1e-9)
        assertEquals("w2", sessionVolume.workoutId)
        // §3.2: "workoutSetId is null for the two session-scoped PrTypes".
        assertNull(sessionVolume.workoutSetId)
    }

    @Test
    fun `warmups are excluded unless the stats setting includes them`() {
        val sets = listOf(
            set("normal", "w1", startedAt = 1_000L, weightKg = 100.0, reps = 5),
            set("warmup", "w1", startedAt = 1_000L, weightKg = 500.0, reps = 1, setType = SetType.WARMUP),
        )

        val excluded = PrRebuilder.rebuild(ExerciseType.WEIGHT_REPS, sets, eligible = false, bodyweightByWorkoutId = emptyMap(), includeWarmupsInStats = false)
            .first { it.prType == PrType.HEAVIEST_WEIGHT }
        assertEquals(100.0, excluded.value, 1e-9)

        val included = PrRebuilder.rebuild(ExerciseType.WEIGHT_REPS, sets, eligible = false, bodyweightByWorkoutId = emptyMap(), includeWarmupsInStats = true)
            .first { it.prType == PrType.HEAVIEST_WEIGHT }
        assertEquals(500.0, included.value, 1e-9)
    }

    @Test
    fun `incomplete sets never become records`() {
        val sets = listOf(
            set("done", "w1", startedAt = 1_000L, weightKg = 100.0, reps = 5),
            set("abandoned", "w1", startedAt = 1_000L, weightKg = 300.0, reps = 5, isCompleted = false),
        )

        val heaviest = PrRebuilder.rebuild(ExerciseType.WEIGHT_REPS, sets, eligible = false, bodyweightByWorkoutId = emptyMap(), includeWarmupsInStats = false)
            .first { it.prType == PrType.HEAVIEST_WEIGHT }

        assertEquals(100.0, heaviest.value, 1e-9)
    }

    @Test
    fun `an exercise with no included sets produces no records`() {
        val sets = listOf(set("s1", "w1", startedAt = 1_000L, weightKg = 100.0, reps = 5, isCompleted = false))
        assertTrue(PrRebuilder.rebuild(ExerciseType.WEIGHT_REPS, sets, eligible = false, bodyweightByWorkoutId = emptyMap(), includeWarmupsInStats = false).isEmpty())
    }

    @Test
    fun `only PrTypes applicable to the exercise type are produced`() {
        val sets = listOf(set("s1", "w1", startedAt = 1_000L, weightKg = null, reps = 12))
        val types = PrRebuilder.rebuild(ExerciseType.REPS_ONLY, sets, eligible = false, bodyweightByWorkoutId = emptyMap(), includeWarmupsInStats = false)
            .map { it.prType }
            .toSet()
        assertEquals(setOf(PrType.MOST_REPS_SET, PrType.MOST_SESSION_REPS), types)
    }

    @Test
    fun `two identical sets in one session credit the record deterministically, whatever order they arrive in`() {
        // Regression: orderIndex is per workout-exercise block, so two blocks of the same exercise
        // in one session tie on (startedAt, orderIndex). The scan then fell back to arrival order,
        // which comes from a SQL ORDER BY that is fully tied for those rows — so which set owned
        // the record was left to SQLite.
        val a = set("block-a-set", "w1", startedAt = 1_000L, weightKg = 100.0, reps = 5)
        val b = set("block-b-set", "w1", startedAt = 1_000L, weightKg = 100.0, reps = 5)

        val forward = PrRebuilder.rebuild(ExerciseType.WEIGHT_REPS, listOf(a, b), eligible = false, bodyweightByWorkoutId = emptyMap(), includeWarmupsInStats = false)
        val reversed = PrRebuilder.rebuild(ExerciseType.WEIGHT_REPS, listOf(b, a), eligible = false, bodyweightByWorkoutId = emptyMap(), includeWarmupsInStats = false)

        assertEquals(
            forward.associate { it.prType to it.workoutSetId },
            reversed.associate { it.prType to it.workoutSetId },
        )
    }

    @Test
    fun `sessions starting at the same instant resolve to the same winner regardless of grouping order`() {
        val w1 = listOf(set("s1", "wa", startedAt = 1_000L, weightKg = 100.0, reps = 5))
        val w2 = listOf(set("s2", "wb", startedAt = 1_000L, weightKg = 100.0, reps = 5))

        val forward = PrRebuilder.rebuild(ExerciseType.WEIGHT_REPS, w1 + w2, eligible = false, bodyweightByWorkoutId = emptyMap(), includeWarmupsInStats = false)
        val reversed = PrRebuilder.rebuild(ExerciseType.WEIGHT_REPS, w2 + w1, eligible = false, bodyweightByWorkoutId = emptyMap(), includeWarmupsInStats = false)

        val sessionVolume = { rs: List<PrRecord> -> rs.first { it.prType == PrType.BEST_SESSION_VOLUME }.workoutId }
        assertEquals(sessionVolume(forward), sessionVolume(reversed))
    }

    @Test
    fun `bodyweight is taken from each workout's own entry, not one value for all history`() {
        // §8.3: "resolved once per workout, not per set". The January set at 70kg bodyweight is
        // worth (70+10)x8 = 640; the August set at 80kg is (80+20)x6 = 600. Applying August's
        // weight to both would score January at 720 — a number never achieved.
        val sets = listOf(
            set("s-jan", "w-jan", startedAt = 1_000L, weightKg = 10.0, reps = 8),
            set("s-aug", "w-aug", startedAt = 2_000L, weightKg = 20.0, reps = 6),
        )

        val records = PrRebuilder.rebuild(
            ExerciseType.BODYWEIGHT_WEIGHTED,
            sets,
            eligible = true,
            bodyweightByWorkoutId = mapOf("w-jan" to 70.0, "w-aug" to 80.0),
            includeWarmupsInStats = false,
        ).associateBy { it.prType }

        assertEquals(640.0, records.getValue(PrType.BEST_SET_VOLUME).value, 1e-9)
        assertEquals("w-jan", records.getValue(PrType.BEST_SET_VOLUME).workoutId)
    }

    private fun set(
        setId: String,
        workoutId: String,
        startedAt: Long,
        weightKg: Double?,
        reps: Int?,
        setType: SetType = SetType.NORMAL,
        isCompleted: Boolean = true,
    ) = StatSet(
        setId = setId, workoutId = workoutId, workoutStartedAt = startedAt, orderIndex = 0,
        setType = setType, weightKg = weightKg, reps = reps, durationSeconds = null,
        distanceMeters = null, customMetric = null, isCompleted = isCompleted,
    )
}
