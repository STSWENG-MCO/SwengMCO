package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.SetType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §10.6's M4c row: "PrDetectorTest". The §8.4 PrType-per-ExerciseType matrix is asserted
 * type-by-type as literals — it is pure transcription from the plan, so a silent mismatch here
 * would mis-award records for a whole exercise category with nothing else to catch it.
 */
class PrCalculatorTest {
    @Test
    fun `the PrType-per-ExerciseType matrix matches section 8_4 exactly`() {
        assertEquals(
            setOf(PrType.HEAVIEST_WEIGHT, PrType.BEST_1RM, PrType.BEST_SET_VOLUME, PrType.BEST_SESSION_VOLUME),
            PrCalculator.applicablePrTypes(ExerciseType.WEIGHT_REPS),
        )
        assertEquals(
            setOf(PrType.MOST_REPS_SET, PrType.MOST_SESSION_REPS),
            PrCalculator.applicablePrTypes(ExerciseType.REPS_ONLY),
        )
        assertEquals(
            setOf(PrType.MOST_REPS_SET, PrType.MOST_SESSION_REPS),
            PrCalculator.applicablePrTypes(ExerciseType.BODYWEIGHT_ASSISTED),
        )
        assertEquals(
            setOf(PrType.HEAVIEST_WEIGHT, PrType.BEST_SET_VOLUME),
            PrCalculator.applicablePrTypes(ExerciseType.BODYWEIGHT_WEIGHTED),
        )
        assertEquals(setOf(PrType.BEST_TIME), PrCalculator.applicablePrTypes(ExerciseType.DURATION))
        assertEquals(
            setOf(PrType.HEAVIEST_WEIGHT, PrType.BEST_TIME),
            PrCalculator.applicablePrTypes(ExerciseType.WEIGHT_DURATION),
        )
        assertEquals(
            setOf(PrType.LONGEST_DISTANCE, PrType.LONGEST_TIME),
            PrCalculator.applicablePrTypes(ExerciseType.DISTANCE_DURATION),
        )
        assertEquals(
            setOf(PrType.HEAVIEST_WEIGHT, PrType.LONGEST_DISTANCE),
            PrCalculator.applicablePrTypes(ExerciseType.WEIGHT_DISTANCE),
        )
        assertEquals(setOf(PrType.BEST_TIME), PrCalculator.applicablePrTypes(ExerciseType.FLOORS_DURATION))
        assertEquals(setOf(PrType.BEST_TIME), PrCalculator.applicablePrTypes(ExerciseType.STEPS_DURATION))
    }

    @Test
    fun `every ExerciseType maps to at least one PrType`() {
        ExerciseType.entries.forEach { type ->
            assertTrue("$type has no applicable PrTypes", PrCalculator.applicablePrTypes(type).isNotEmpty())
        }
    }

    @Test
    fun `WEIGHT_REPS set candidates carry weight, 1RM and set volume`() {
        val candidates = PrCalculator.setCandidates(
            ExerciseType.WEIGHT_REPS, weightRepsSet(weightKg = 100.0, reps = 5), eligible = false, bodyweightKg = null,
        )
        assertEquals(100.0, candidates.getValue(PrType.HEAVIEST_WEIGHT), 1e-9)
        assertEquals(100.0 / 0.89, candidates.getValue(PrType.BEST_1RM), 1e-9)
        assertEquals(500.0, candidates.getValue(PrType.BEST_SET_VOLUME), 1e-9)
        // Session-scoped types are never per-set candidates.
        assertTrue(PrType.BEST_SESSION_VOLUME !in candidates)
    }

    @Test
    fun `candidates whose required field is null are omitted rather than counted as zero`() {
        val candidates = PrCalculator.setCandidates(
            ExerciseType.WEIGHT_REPS, weightRepsSet(weightKg = null, reps = 5), eligible = false, bodyweightKg = null,
        )
        assertTrue(PrType.HEAVIEST_WEIGHT !in candidates)
        assertTrue(PrType.BEST_1RM !in candidates)
        assertTrue(PrType.BEST_SET_VOLUME !in candidates)
    }

    @Test
    fun `session candidates sum the whole session`() {
        val sets = listOf(
            weightRepsSet(weightKg = 100.0, reps = 5),
            weightRepsSet(weightKg = 100.0, reps = 3, setId = "s2", orderIndex = 1),
        )
        val candidates = PrCalculator.sessionCandidates(ExerciseType.WEIGHT_REPS, sets, eligible = false, bodyweightKg = null)
        assertEquals(800.0, candidates.getValue(PrType.BEST_SESSION_VOLUME), 1e-9)
    }

    @Test
    fun `MOST_SESSION_REPS sums reps for rep-based types`() {
        val sets = listOf(
            repsOnlySet(reps = 12),
            repsOnlySet(reps = 10, setId = "s2", orderIndex = 1),
        )
        val candidates = PrCalculator.sessionCandidates(ExerciseType.REPS_ONLY, sets, eligible = false, bodyweightKg = null)
        assertEquals(22.0, candidates.getValue(PrType.MOST_SESSION_REPS), 1e-9)
    }

    @Test
    fun `a live banner fires only when the new set strictly beats both cached and earlier-session bests`() {
        val newSet = weightRepsSet(weightKg = 110.0, reps = 5)
        val banners = PrCalculator.liveBanners(
            type = ExerciseType.WEIGHT_REPS,
            newSet = newSet,
            eligible = false,
            bodyweightKg = null,
            cachedBests = mapOf(PrType.HEAVIEST_WEIGHT to 100.0),
            earlierSessionBests = emptyMap(),
            isFirstEverLog = false,
            includeWarmupsInStats = false,
        )
        assertTrue(PrType.HEAVIEST_WEIGHT in banners)
    }

    @Test
    fun `merely equalling a previous best is not a PR`() {
        val banners = PrCalculator.liveBanners(
            type = ExerciseType.WEIGHT_REPS,
            newSet = weightRepsSet(weightKg = 100.0, reps = 5),
            eligible = false,
            bodyweightKg = null,
            cachedBests = mapOf(PrType.HEAVIEST_WEIGHT to 100.0),
            earlierSessionBests = emptyMap(),
            isFirstEverLog = false,
            includeWarmupsInStats = false,
        )
        assertTrue(PrType.HEAVIEST_WEIGHT !in banners)
    }

    @Test
    fun `an earlier set in the same session suppresses a repeat banner`() {
        // Third set matches the second set's already-announced best — no second banner.
        val banners = PrCalculator.liveBanners(
            type = ExerciseType.WEIGHT_REPS,
            newSet = weightRepsSet(weightKg = 110.0, reps = 5),
            eligible = false,
            bodyweightKg = null,
            cachedBests = mapOf(PrType.HEAVIEST_WEIGHT to 100.0),
            earlierSessionBests = mapOf(PrType.HEAVIEST_WEIGHT to 110.0),
            isFirstEverLog = false,
            includeWarmupsInStats = false,
        )
        assertTrue(PrType.HEAVIEST_WEIGHT !in banners)
    }

    @Test
    fun `an exercise's first-ever log never banners`() {
        val banners = PrCalculator.liveBanners(
            type = ExerciseType.WEIGHT_REPS,
            newSet = weightRepsSet(weightKg = 100.0, reps = 5),
            eligible = false,
            bodyweightKg = null,
            cachedBests = emptyMap(),
            earlierSessionBests = emptyMap(),
            isFirstEverLog = true,
            includeWarmupsInStats = false,
        )
        assertTrue(banners.isEmpty())
    }

    @Test
    fun `a warmup never banners unless the stats setting includes warmups`() {
        val warmup = weightRepsSet(weightKg = 500.0, reps = 5, setType = SetType.WARMUP)
        val args = { include: Boolean ->
            PrCalculator.liveBanners(
                type = ExerciseType.WEIGHT_REPS, newSet = warmup, eligible = false, bodyweightKg = null,
                cachedBests = mapOf(PrType.HEAVIEST_WEIGHT to 100.0), earlierSessionBests = emptyMap(),
                isFirstEverLog = false, includeWarmupsInStats = include,
            )
        }
        assertTrue(args(false).isEmpty())
        assertTrue(PrType.HEAVIEST_WEIGHT in args(true))
    }

    private fun weightRepsSet(
        weightKg: Double?,
        reps: Int?,
        setId: String = "s1",
        orderIndex: Int = 0,
        setType: SetType = SetType.NORMAL,
    ) = StatSet(
        setId = setId, workoutId = "w1", workoutStartedAt = 0L, orderIndex = orderIndex, setType = setType,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null,
        customMetric = null, isCompleted = true,
    )

    private fun repsOnlySet(reps: Int, setId: String = "s1", orderIndex: Int = 0) =
        weightRepsSet(weightKg = null, reps = reps, setId = setId, orderIndex = orderIndex)
}
