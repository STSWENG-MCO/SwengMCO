package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** PHASE2_PLAN.md §8.9 literal test vectors, landing with the engine's consuming milestone (M6a) per §10.6. */
class ChartAggregatorTest {
    private val today: LocalDate = LocalDate.of(2026, 8, 22)

    private fun set(
        id: String,
        weightKg: Double? = null,
        reps: Int? = null,
        durationSeconds: Int? = null,
        distanceMeters: Double? = null,
        setType: SetType = SetType.NORMAL,
        workoutId: String = "w1",
        startedAt: Long = 1_000L,
        isCompleted: Boolean = true,
    ) = StatSet(
        setId = id, workoutId = workoutId, workoutStartedAt = startedAt, orderIndex = 0,
        setType = setType, weightKg = weightKg, reps = reps, durationSeconds = durationSeconds,
        distanceMeters = distanceMeters, customMetric = null, isCompleted = isCompleted,
    )

    // --- window vectors (§8.9, today = 2026-08-22) ---

    @Test
    fun `LAST_30_DAYS window is 2026-07-24 through 2026-08-22`() {
        val w = ChartAggregator.window(ChartRange.LAST_30_DAYS, today)!!
        assertEquals(LocalDate.of(2026, 7, 24), w.start)
        assertEquals(today, w.endInclusive)
    }

    @Test
    fun `LAST_3_MONTHS window is 2026-05-23 through 2026-08-22`() {
        val w = ChartAggregator.window(ChartRange.LAST_3_MONTHS, today)!!
        assertEquals(LocalDate.of(2026, 5, 23), w.start)
        assertEquals(today, w.endInclusive)
    }

    @Test
    fun `LAST_YEAR window is 2025-08-23 through 2026-08-22`() {
        val w = ChartAggregator.window(ChartRange.LAST_YEAR, today)!!
        assertEquals(LocalDate.of(2025, 8, 23), w.start)
        assertEquals(today, w.endInclusive)
    }

    @Test
    fun `ALL_TIME window is unbounded (null)`() {
        assertNull(ChartAggregator.window(ChartRange.ALL_TIME, today))
    }

    // --- per-type metric sets (§8.9 table) ---

    @Test
    fun `metric sets follow the per-ExerciseType table exactly`() {
        assertEquals(
            listOf(ChartMetric.HEAVIEST_WEIGHT, ChartMetric.ONE_REP_MAX, ChartMetric.BEST_SET_VOLUME, ChartMetric.SESSION_VOLUME, ChartMetric.TOTAL_REPS),
            ChartAggregator.metricsFor(ExerciseType.WEIGHT_REPS),
        )
        assertEquals(listOf(ChartMetric.MOST_REPS_SET, ChartMetric.SESSION_REPS), ChartAggregator.metricsFor(ExerciseType.REPS_ONLY))
        assertEquals(listOf(ChartMetric.MOST_REPS_SET, ChartMetric.SESSION_REPS), ChartAggregator.metricsFor(ExerciseType.BODYWEIGHT_ASSISTED))
        assertEquals(
            listOf(ChartMetric.HEAVIEST_WEIGHT, ChartMetric.BEST_SET_VOLUME, ChartMetric.TOTAL_REPS),
            ChartAggregator.metricsFor(ExerciseType.BODYWEIGHT_WEIGHTED),
        )
        assertEquals(listOf(ChartMetric.BEST_TIME), ChartAggregator.metricsFor(ExerciseType.DURATION))
        assertEquals(listOf(ChartMetric.HEAVIEST_WEIGHT, ChartMetric.BEST_TIME), ChartAggregator.metricsFor(ExerciseType.WEIGHT_DURATION))
        assertEquals(
            listOf(ChartMetric.BEST_PACE, ChartMetric.LONGEST_DISTANCE, ChartMetric.LONGEST_TIME),
            ChartAggregator.metricsFor(ExerciseType.DISTANCE_DURATION),
        )
        assertEquals(listOf(ChartMetric.HEAVIEST_WEIGHT, ChartMetric.LONGEST_DISTANCE), ChartAggregator.metricsFor(ExerciseType.WEIGHT_DISTANCE))
        assertEquals(listOf(ChartMetric.BEST_TIME), ChartAggregator.metricsFor(ExerciseType.FLOORS_DURATION))
        assertEquals(listOf(ChartMetric.BEST_TIME), ChartAggregator.metricsFor(ExerciseType.STEPS_DURATION))
    }

    // --- point-value vectors (§8.9: WEIGHT_REPS workout with sets 100.0x1, 90.0x5, 80.0x10) ---

    private val weightRepsSets = listOf(
        set("s1", weightKg = 100.0, reps = 1),
        set("s2", weightKg = 90.0, reps = 5),
        set("s3", weightKg = 80.0, reps = 10),
    )

    private fun singleValue(metric: ChartMetric, sets: List<StatSet>, type: ExerciseType = ExerciseType.WEIGHT_REPS): Double {
        val points = ChartAggregator.points(metric, type, isBodyweightVolumeEligible = false, sets = sets, bodyweightByWorkout = emptyMap(), includeWarmupsInStats = false)
        assertEquals(1, points.size)
        return points.single().value
    }

    @Test
    fun `HEAVIEST_WEIGHT point is 100`() {
        assertEquals(100.0, singleValue(ChartMetric.HEAVIEST_WEIGHT, weightRepsSets), 1e-9)
    }

    @Test
    fun `ONE_REP_MAX point is 106_66666666666667`() {
        assertEquals(106.66666666666667, singleValue(ChartMetric.ONE_REP_MAX, weightRepsSets), 1e-9)
    }

    @Test
    fun `SESSION_VOLUME point is 1350`() {
        assertEquals(1350.0, singleValue(ChartMetric.SESSION_VOLUME, weightRepsSets), 1e-9)
    }

    @Test
    fun `TOTAL_REPS point is 16`() {
        assertEquals(16.0, singleValue(ChartMetric.TOTAL_REPS, weightRepsSets), 1e-9)
    }

    @Test
    fun `BEST_PACE picks the fastest set in seconds per km`() {
        val sets = listOf(
            set("p1", distanceMeters = 5000.0, durationSeconds = 1440),
            set("p2", distanceMeters = 3000.0, durationSeconds = 900),
        )
        assertEquals(288.0, singleValue(ChartMetric.BEST_PACE, sets, ExerciseType.DISTANCE_DURATION), 1e-9)
    }

    // --- aggregation contract ---

    @Test
    fun `one point per workout, sorted by startedAt, excluded sets contribute nothing`() {
        val sets = listOf(
            set("a1", weightKg = 100.0, reps = 5, workoutId = "w2", startedAt = 2_000L),
            set("a2", weightKg = 90.0, reps = 5, workoutId = "w1", startedAt = 1_000L),
            set("a3", weightKg = 200.0, reps = 5, workoutId = "w1", startedAt = 1_000L, isCompleted = false),
            set("a4", weightKg = 300.0, reps = 5, workoutId = "w3", startedAt = 3_000L, setType = SetType.WARMUP),
        )
        val points = ChartAggregator.points(
            ChartMetric.HEAVIEST_WEIGHT, ExerciseType.WEIGHT_REPS, isBodyweightVolumeEligible = false,
            sets = sets, bodyweightByWorkout = emptyMap(), includeWarmupsInStats = false,
        )
        assertEquals(listOf("w1", "w2"), points.map { it.workoutId })
        assertEquals(listOf(90.0, 100.0), points.map { it.value })
    }

    @Test
    fun `warm-up sets count once the stats setting includes them`() {
        val sets = listOf(
            set("a1", weightKg = 100.0, reps = 5),
            set("a2", weightKg = 120.0, reps = 3, setType = SetType.WARMUP),
        )
        val excluded = ChartAggregator.points(ChartMetric.HEAVIEST_WEIGHT, ExerciseType.WEIGHT_REPS, false, sets, emptyMap(), includeWarmupsInStats = false)
        val included = ChartAggregator.points(ChartMetric.HEAVIEST_WEIGHT, ExerciseType.WEIGHT_REPS, false, sets, emptyMap(), includeWarmupsInStats = true)
        assertEquals(100.0, excluded.single().value, 1e-9)
        assertEquals(120.0, included.single().value, 1e-9)
    }
}
