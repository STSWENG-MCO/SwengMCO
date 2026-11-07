package com.enil.logez.core.domain.calc

import kotlin.math.abs

/**
 * PHASE2_PLAN.md §5.1.5 — the Plate Calculator's per-side loading algorithm (M17). Pure math, no
 * Android imports: the sheet feeds it a target and the user's equipment and renders the result.
 *
 * Float discipline: every internal step runs in integer QUARTER-KILOGRAMS (kg × 4, Long/Int), so
 * stacking 1.25 kg plates is exact and no Double error can ever accumulate across a loading. Any
 * weight that is not a quarter-kg multiple (a user-entered oddity like 1.1 kg) is rounded to the
 * NEAREST quarter-kg on entry — [roundToQuarterKg] is the single public statement of that choice,
 * and the equipment editor applies it at entry time so what the user sees stored is what the
 * solver uses.
 *
 * Optimality: greedy heaviest-first is optimal for the default denomination set, but unlimited-pair
 * pathological sets (e.g. plates of 4 kg and 3 kg for a 6 kg side: greedy loads 4 and stops, yet
 * 3+3 is exact) can beat it. So achievability runs as a bounded coin-style DP over quarter-kg
 * units — fewest plates per achievable side load, ties resolved toward the heavier plate, which
 * reproduces the greedy loading exactly wherever greedy is right. The DP is bounded by
 * ceil(per-side target) + the largest denomination (capped at [MAX_SIDE_QUARTERS]): any optimal
 * closest answer at or above the target needs at most one largest-plate overshoot past it.
 */
object PlateCalculator {

    /** DP bound cap: 5000 kg per side in quarter-units — far beyond any real target, keeps absurd input cheap. */
    private const val MAX_SIDE_QUARTERS = 20_000

    /**
     * @property perSideKg plates for ONE side, heaviest first; empty when the bar alone is the answer.
     * @property achievedKg bar + 2 × sum(perSideKg) — the closest achievable total (== target when [exact]).
     * @property exact whether [achievedKg] hits the target exactly.
     * @property belowBar the §5.1.5 "Bar alone weighs Y" case: target < bar, nothing can be loaded.
     */
    data class Result(
        val perSideKg: List<Double>,
        val achievedKg: Double,
        val exact: Boolean,
        val belowBar: Boolean,
    )

    /** The entry-rounding rule, public so the equipment editor stores exactly what the solver uses. */
    fun roundToQuarterKg(kg: Double): Double = fromQuarters(toQuarters(kg))

    fun solve(targetKg: Double, barKg: Double, plateDenominationsKg: List<Double>): Result {
        val barQ = toQuarters(barKg)
        val targetQ = toQuarters(targetKg)
        if (targetQ < barQ) {
            // Sub-bar signal: nothing to load, the empty bar already overshoots.
            return Result(perSideKg = emptyList(), achievedKg = fromQuarters(barQ), exact = false, belowBar = true)
        }
        val loadQ = targetQ - barQ // to be split across the two sides
        if (loadQ == 0L) return Result(emptyList(), fromQuarters(barQ), exact = true, belowBar = false)

        val denomsQ = plateDenominationsKg.asSequence()
            .map { toQuarters(it).toInt() }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
            .toList()
        if (denomsQ.isEmpty()) {
            // No plates owned: the bar alone IS the closest achievable weight.
            return Result(emptyList(), fromQuarters(barQ), exact = false, belowBar = false)
        }

        // dp[s] = fewest plates summing exactly to s quarter-units on one side (-1 = unreachable);
        // firstPlate[s] = the plate that achieves that count, ties kept on the heaviest (greedy-like).
        val sideCeilQ = ((loadQ + 1) / 2).coerceAtMost(MAX_SIDE_QUARTERS.toLong()).toInt()
        val bound = (sideCeilQ + denomsQ.first()).coerceAtMost(MAX_SIDE_QUARTERS)
        val dp = IntArray(bound + 1) { -1 }
        val firstPlate = IntArray(bound + 1)
        dp[0] = 0
        for (s in 1..bound) {
            for (d in denomsQ) {
                if (d <= s && dp[s - d] >= 0 && (dp[s] < 0 || dp[s - d] + 1 < dp[s])) {
                    dp[s] = dp[s - d] + 1
                    firstPlate[s] = d
                }
            }
        }

        // Closest achievable side load: minimise |2s − load|; on a tie keep the LOWER total
        // (ascending scan + strict '<' does both). s = 0 (bar alone) is always achievable.
        var bestS = 0
        var bestDist = loadQ // |2·0 − load|
        for (s in 1..bound) {
            if (dp[s] < 0) continue
            val dist = abs(2L * s - loadQ)
            if (dist < bestDist) {
                bestS = s
                bestDist = dist
            }
        }

        val perSide = buildList {
            var s = bestS
            while (s > 0) {
                val d = firstPlate[s]
                add(fromQuarters(d.toLong()))
                s -= d
            }
        }.sortedDescending()
        val achievedQ = barQ + 2L * bestS
        return Result(
            perSideKg = perSide,
            achievedKg = fromQuarters(achievedQ),
            exact = achievedQ == targetQ,
            belowBar = false,
        )
    }

    private fun toQuarters(kg: Double): Long {
        if (kg.isNaN() || kg <= 0.0) return 0L
        return Math.round(kg * 4.0)
    }

    private fun fromQuarters(quarters: Long): Double = quarters / 4.0
}
