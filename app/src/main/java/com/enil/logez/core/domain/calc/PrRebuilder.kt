package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.PrType

/** One winning record, still framework-free — the caller mints ids and maps this to `PersonalRecordEntity`. */
data class PrRecord(
    val prType: PrType,
    val value: Double,
    val workoutId: String,
    /** Null exactly for the two session-scoped PrTypes (§3.2: "workoutSetId is null for the two session-scoped PrTypes"). */
    val workoutSetId: String?,
    val achievedAt: Long,
)

/**
 * PHASE2_PLAN.md §8.4's missing half: [PrCalculator] computes *candidate values* for one set or
 * one session, but nothing turned an exercise's whole COMPLETED history into the single winner
 * per `PrType` that the `personal_records` derived cache stores (its unique index is
 * `(exercise_id, pr_type)`). That rebuild is what a workout save triggers, and it lives here
 * rather than in [PrCalculator] so both stay pure and separately testable.
 *
 * Tie-break: the **earliest** achiever wins — a later session that merely *equals* your best
 * does not steal the record, so `achievedAt` keeps pointing at when you first hit it. Strict `>`
 * on the value, with `workoutStartedAt` (then `orderIndex`, then `setId`) as the ordering key.
 * The `setId` tail matters: `orderIndex` is only unique within one workout-exercise block, so two
 * blocks of the same exercise in one session tie on both leading keys and would otherwise let
 * SQLite's unspecified row order decide which set the record is credited to.
 */
object PrRebuilder {
    /**
     * [sets] must be an exercise's full history across COMPLETED workouts (an IN_PROGRESS
     * workout never contributes a StatSet — §8.1). Warm-up filtering happens here via the shared
     * [isIncluded] predicate, since [PrCalculator] deliberately trusts its caller to pre-filter.
     *
     * [bodyweightByWorkoutId] carries the bodyweight in effect *on the day of each workout*, per
     * §8.3's "resolved once per workout, not per set". Passing one current value for the whole
     * history would recompute every past bodyweight-volume record against today's weight, so a
     * record would silently move whenever the user's weight changed — and the cache would stop
     * being a pure function of the logged data. Workouts with no measurement on or before them
     * map to null, which the volume engine already treats as "bodyweight unknown".
     */
    fun rebuild(
        type: ExerciseType,
        sets: List<StatSet>,
        eligible: Boolean,
        bodyweightByWorkoutId: Map<String, Double?>,
        includeWarmupsInStats: Boolean,
    ): List<PrRecord> {
        val included = sets.filter { isIncluded(it, includeWarmupsInStats) }
        if (included.isEmpty()) return emptyList()

        val winners = mutableMapOf<PrType, PrRecord>()

        // Set-scoped: every set is its own candidate. Ordering the scan by (session time, position)
        // means the first set to reach a value keeps the record under the strict-> comparison.
        included
            .sortedWith(compareBy({ it.workoutStartedAt }, { it.orderIndex }, { it.setId }))
            .forEach { set ->
                val bodyweightKg = bodyweightByWorkoutId[set.workoutId]
                PrCalculator.setCandidates(type, set, eligible, bodyweightKg).forEach { (prType, value) ->
                    val current = winners[prType]
                    if (current == null || value > current.value) {
                        winners[prType] = PrRecord(
                            prType = prType,
                            value = value,
                            workoutId = set.workoutId,
                            workoutSetId = set.setId,
                            achievedAt = set.workoutStartedAt,
                        )
                    }
                }
            }

        // Session-scoped: candidates are per-workout totals, so the unit of comparison is a
        // whole session and there is no single owning set (hence workoutSetId = null).
        included
            .groupBy { it.workoutId }
            .entries
            // `workoutId` breaks ties on identical start times so the scan order — and therefore
            // which session keeps an equalled record — does not depend on grouping order.
            .sortedWith(compareBy({ (_, s) -> s.minOf { it.workoutStartedAt } }, { (id, _) -> id }))
            .forEach { (workoutId, sessionSets) ->
                val startedAt = sessionSets.minOf { it.workoutStartedAt }
                val bodyweightKg = bodyweightByWorkoutId[workoutId]
                PrCalculator.sessionCandidates(type, sessionSets, eligible, bodyweightKg).forEach { (prType, value) ->
                    val current = winners[prType]
                    if (current == null || value > current.value) {
                        winners[prType] = PrRecord(
                            prType = prType,
                            value = value,
                            workoutId = workoutId,
                            workoutSetId = null,
                            achievedAt = startedAt,
                        )
                    }
                }
            }

        return winners.values.toList()
    }
}
