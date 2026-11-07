package com.enil.logez.core.designsystem

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Constrained spacing/shape scale (Refactoring UI, ~25%+ step growth) — the vault's distilled
 * rule that ambiguous spacing is the classic amateur tell. Never invent an in-between value:
 * jump to the next step instead.
 */
object Spacing {
    val xxs: Dp = 4.dp
    val xs: Dp = 8.dp
    val sm: Dp = 12.dp
    val md: Dp = 16.dp
    val lg: Dp = 24.dp
    val xl: Dp = 32.dp
    val xxl: Dp = 48.dp
    val xxxl: Dp = 64.dp
}

object Radius {
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val pill: Dp = 999.dp

    /**
     * v7.0 redesign mockup's card radius (`--radius-card: 18px`). Deliberately separate from [md]:
     * [md] is also used for the exercise-photo thumbnail clip in `CustomExerciseEditorScreen`, an
     * image-well affordance rather than card chrome, and that shouldn't co-vary with how round
     * cards are.
     */
    val card: Dp = 18.dp
}

/** M9b (Neon Lab) — real elevation for [LogEzCard], replacing the fully-flat pre-rebrand cards. */
object Elevation {
    val card: Dp = 4.dp
    /** M20a: the lift a card gets while it is being dragged to reorder — the only other level. */
    val dragging: Dp = 8.dp
}

/**
 * Shared column geometry for every set-logging table (regular logger card, circuit round card,
 * routine builder) — one source so the three tables can't drift. Each fixed cell is sized so its
 * widest header word fits WHOLE on one line ("ROUND" for the set cell; "152.5 kg × 12" for the
 * PREVIOUS value column): a label that breaks mid-word ("ROU/ND") is a rendering bug, not wrapping.
 */
object SetTable {
    /** SET/ROUND number cell — "ROUND" is the widest label sharing this slot. */
    val setCell: Dp = 48.dp

    /** PREVIOUS column (Owner, 2026-09-03: shrunk back from 112dp to give REPS a bit more room, now
     that RPE renders on its own second line instead of an appended "@ 8.5" suffix — the value line
     alone ("50 kg × 10") no longer needs the extra width that suffix used to require). Still wider
     than the original 84dp so typical values render un-broken; only pathological cases (a long
     fractional weight paired with a long distance, e.g. "154.3 lb × 6.2 mi") still ellipsize. */
    val previousCell: Dp = 96.dp

    val rpeCell: Dp = 44.dp

    /** Trailing check/action column (also the header row's spacer over it). */
    val checkCell: Dp = 48.dp // the most-tapped control in the app; 48dp is the touch-target floor (was 40dp)

    /**
     * M17: the plate-calculator tap target that trails a barbell row's KG cell. The header rows
     * add a spacer of the same width whenever the affordance renders, so the KG header stays
     * centered over the (cell + button) pair and the M15 column alignment holds.
     */
    val plateCalcCell: Dp = 28.dp

    /**
     * M18 (Owner: uniform boxed cells): every set-row cell renders as a box of this
     * height. 56dp is Material3's OutlinedTextField min height — the KG/REPS/TIME fields already
     * render at it, so SET, PREVIOUS, and RPE boxes match the fields rather than the other way
     * round (widths stay the M15 SetTable values; only the fixed cells' chrome changed).
     */
    val cellHeight: Dp = 56.dp
}
