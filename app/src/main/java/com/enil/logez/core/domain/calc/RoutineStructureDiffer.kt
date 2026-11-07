package com.enil.logez.core.domain.calc

/** One exercise's structural shape — identity plus how many sets it carries. Values are deliberately absent. */
data class ExerciseShape(val exerciseId: String, val setCount: Int)

/**
 * PHASE2_PLAN.md §5.1.8(b) — decides whether the finish flow shows the "Update Routine vs Keep
 * Original" prompt. Structural means *shape*: exercises or sets added / removed / reordered.
 * Value-only differences (heavier weight, more reps) are never structural — those follow the
 * separate "Update Routine Values" toggle in §5.1.8(a), so comparing values here would fire the
 * prompt on virtually every session and train the user to dismiss it.
 *
 * Order matters: the same exercises performed in a different order *is* a structural change.
 */
object RoutineStructureDiffer {
    fun isStructurallyChanged(routineShape: List<ExerciseShape>, loggedShape: List<ExerciseShape>): Boolean =
        routineShape != loggedShape
}
