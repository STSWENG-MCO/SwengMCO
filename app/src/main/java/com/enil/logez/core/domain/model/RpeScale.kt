package com.enil.logez.core.domain.model

/**
 * PHASE2_PLAN.md §5.1.7 — RPE (Rate of Perceived Exertion) is restricted to eight half-step
 * values, entered live-logging only via a picker, never as a free-text field and never as a
 * routine target (`routine_sets` carries no RPE column at all).
 *
 * [VALUES] is the sole source of what's enterable: a UI built from this list can never produce a
 * value outside it by construction, so there is no separate runtime validator to keep in sync.
 *
 * The reserve-reps mapping below is the standard RPE/RIR correlation used across strength
 * training generally (Renaissance Periodization and others publish the same table), phrased in
 * our own words rather than any app's exact wording.
 */
object RpeScale {
    val VALUES: List<Double> = listOf(6.0, 7.0, 7.5, 8.0, 8.5, 9.0, 9.5, 10.0)

    /** A short "reps in reserve" description for the picker's selected-value label. */
    fun reserveDescription(rpe: Double): String = when (rpe) {
        6.0 -> "4+ reps in reserve"
        7.0 -> "3 reps in reserve"
        7.5 -> "2–3 reps in reserve"
        8.0 -> "2 reps in reserve"
        8.5 -> "1–2 reps in reserve"
        9.0 -> "1 rep in reserve"
        9.5 -> "Maybe 1 rep in reserve"
        10.0 -> "Max effort — nothing left"
        else -> ""
    }

    /** "6" / "7.5" / "10" — no trailing ".0" on the whole-number values. */
    fun format(rpe: Double): String = if (rpe == rpe.toLong().toDouble()) rpe.toLong().toString() else rpe.toString()
}
