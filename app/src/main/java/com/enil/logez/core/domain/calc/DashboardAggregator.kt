package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.ExerciseType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.Instant

/**
 * PHASE2_PLAN.md §5.2 "Analytics dashboard" — workout-level aggregation for the Training charts
 * (volume/reps/duration/frequency per week), the Muscle Distribution card's period tiles, Main
 * Exercises, and the Monthly Report's 6-month comparison. Pure computation; the ViewModel feeds
 * it bulk-read rows and resolves zone/today fresh on every refresh (frozen-field convention).
 *
 * Volume convention: [VolumeCalculator.setVolume] with `bodyweightKg = null`, exactly as the
 * History feed and the post-save Summary compute a workout's volume — a workout must show one
 * volume number on every surface, so the dashboard inherits their choice rather than resolving
 * real bodyweights the way the *per-exercise* Summary chart (§8.9) does.
 *
 * Warm-up semantics follow §8.6's table: volume/reps/set counts respect the included-set
 * predicate; duration and frequency are workout-level and never affected by it.
 */
object DashboardAggregator {
    enum class TrainingMetric { VOLUME, REPS, DURATION, FREQUENCY }

    /** One completed workout, projected to what the dashboard needs. */
    data class WorkoutInfo(val id: String, val startedAt: Long, val durationSeconds: Int)

    /** One set joined with the exercise facts volume math needs. */
    data class SetWithExercise(
        val exerciseId: String,
        val exerciseName: String,
        val exerciseType: ExerciseType,
        val isBodyweightVolumeEligible: Boolean,
        val set: StatSet,
    )

    data class WeeklyBar(val weekStart: LocalDate, val value: Double)
    data class PeriodTotals(val workouts: Int, val durationSeconds: Long, val volumeKg: Double, val sets: Int)
    data class ExerciseFrequency(val exerciseId: String, val exerciseName: String, val workoutCount: Int)
    data class MonthTotals(val month: YearMonth, val totals: PeriodTotals)

    fun localDate(startedAt: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(startedAt).atZone(zone).toLocalDate()

    /**
     * One bar per week from the window's first workout-holding week through today's week —
     * zero weeks inside that span render as zero-height bars (an honest gap), never invented
     * values. Empty result when the window holds no workouts at all (§5.2's empty-card state).
     */
    fun weeklyTrainingSeries(
        metric: TrainingMetric,
        workouts: List<WorkoutInfo>,
        sets: List<SetWithExercise>,
        range: ChartRange,
        today: LocalDate,
        zone: ZoneId,
        firstDayOfWeek: DayOfWeek,
        includeWarmupsInStats: Boolean,
    ): List<WeeklyBar> {
        val window = ChartAggregator.window(range, today)
        val inWindow = workouts.filter { window == null || localDate(it.startedAt, zone) in window }
        if (inWindow.isEmpty()) return emptyList()

        val valueByWeek: Map<LocalDate, Double> = when (metric) {
            TrainingMetric.FREQUENCY -> inWindow
                .groupingBy { StreakCalculator.weekStart(localDate(it.startedAt, zone), firstDayOfWeek) }
                .eachCount().mapValues { it.value.toDouble() }
            TrainingMetric.DURATION -> inWindow
                .groupBy { StreakCalculator.weekStart(localDate(it.startedAt, zone), firstDayOfWeek) }
                .mapValues { (_, ws) -> ws.sumOf { it.durationSeconds.toDouble() } }
            TrainingMetric.VOLUME, TrainingMetric.REPS -> {
                val workoutIds = inWindow.map { it.id }.toHashSet()
                sets
                    .filter { it.set.workoutId in workoutIds && isIncluded(it.set, includeWarmupsInStats) }
                    .groupBy { StreakCalculator.weekStart(localDate(it.set.workoutStartedAt, zone), firstDayOfWeek) }
                    .mapValues { (_, weekSets) ->
                        when (metric) {
                            TrainingMetric.VOLUME -> weekSets.sumOf {
                                VolumeCalculator.setVolume(it.exerciseType, it.isBodyweightVolumeEligible, it.set.weightKg, it.set.reps, bodyweightKg = null)
                            }
                            else -> weekSets.sumOf { (it.set.reps ?: 0).toDouble() }
                        }
                    }
            }
        }

        val firstWeek = inWindow.minOf { StreakCalculator.weekStart(localDate(it.startedAt, zone), firstDayOfWeek) }
        val lastWeek = StreakCalculator.weekStart(today, firstDayOfWeek)
        val bars = mutableListOf<WeeklyBar>()
        var week = firstWeek
        while (!week.isAfter(lastWeek)) {
            bars.add(WeeklyBar(week, valueByWeek[week] ?: 0.0))
            week = week.plusWeeks(1)
        }
        return bars
    }

    /** The Muscle Distribution card's four tiles (§5.2 card 2), over an arbitrary window (null = all time). */
    fun periodTotals(
        workouts: List<WorkoutInfo>,
        sets: List<SetWithExercise>,
        window: ClosedRange<LocalDate>?,
        zone: ZoneId,
        includeWarmupsInStats: Boolean,
    ): PeriodTotals {
        val inWindow = workouts.filter { window == null || localDate(it.startedAt, zone) in window }
        val workoutIds = inWindow.map { it.id }.toHashSet()
        val includedSets = sets.filter { it.set.workoutId in workoutIds && isIncluded(it.set, includeWarmupsInStats) }
        return PeriodTotals(
            workouts = inWindow.size,
            durationSeconds = inWindow.sumOf { it.durationSeconds.toLong() },
            volumeKg = includedSets.sumOf {
                VolumeCalculator.setVolume(it.exerciseType, it.isBodyweightVolumeEligible, it.set.weightKg, it.set.reps, bodyweightKg = null)
            },
            sets = includedSets.size,
        )
    }

    /**
     * §5.2 card 5 — most frequently logged exercises. "Frequency" = the number of distinct
     * workouts in the window holding >=1 included set of the exercise, sorted descending then
     * alphabetically for a stable order between equally-frequent exercises.
     */
    fun mainExercises(
        sets: List<SetWithExercise>,
        window: ClosedRange<LocalDate>?,
        zone: ZoneId,
        includeWarmupsInStats: Boolean,
    ): List<ExerciseFrequency> =
        sets
            .filter { isIncluded(it.set, includeWarmupsInStats) }
            .filter { window == null || localDate(it.set.workoutStartedAt, zone) in window }
            .groupBy { it.exerciseId }
            .map { (exerciseId, exSets) ->
                ExerciseFrequency(exerciseId, exSets.first().exerciseName, exSets.distinctBy { it.set.workoutId }.size)
            }
            .sortedWith(compareByDescending<ExerciseFrequency> { it.workoutCount }.thenBy { it.exerciseName })

    /** Whole-month window for [month] — the Monthly Report's building block. */
    fun monthWindow(month: YearMonth): ClosedRange<LocalDate> = month.atDay(1)..month.atEndOfMonth()

    /** §5.2 card 6 — one totals row per month, oldest first, for the 6-month comparison chart. */
    fun monthlyTotals(
        workouts: List<WorkoutInfo>,
        sets: List<SetWithExercise>,
        months: List<YearMonth>,
        zone: ZoneId,
        includeWarmupsInStats: Boolean,
    ): List<MonthTotals> =
        months.sorted().map { month ->
            MonthTotals(month, periodTotals(workouts, sets, monthWindow(month), zone, includeWarmupsInStats))
        }
}
