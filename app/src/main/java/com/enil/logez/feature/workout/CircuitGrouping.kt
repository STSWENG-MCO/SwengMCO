package com.enil.logez.feature.workout

/**
 * M11 circuits: the logger's round-first view of the same `workout_exercises`/`workout_sets`
 * rows a regular workout groups exercise-first. Pure functions — the ViewModel/Screen call these
 * on every recomposition input, and the tests drive them directly.
 *
 * Round k (1-based) is the slice of row index k-1 across every exercise, in exercise sequence
 * order. Nothing here mutates or repairs data: a circuit whose exercises carry UNEQUAL set counts
 * (edited data, partial adds) still renders — an exercise simply contributes no entry to rounds
 * beyond its own row count, and [CircuitRoundEntry.set] is null for that gap so the UI can show an
 * inert "—" slot instead of crashing or silently re-flowing rows.
 */
data class CircuitRound(
    /** 1-based round number — what the "ROUND k" header prints. */
    val roundNumber: Int,
    /** One entry per exercise, in sequence order — [CircuitRoundEntry.set] null where the exercise has no row for this round. */
    val entries: List<CircuitRoundEntry>,
)

data class CircuitRoundEntry(
    val exercise: WorkoutExerciseUiModel,
    /** The exercise's row for this round, or null when its set count falls short (defensive slot). */
    val set: WorkoutSetUiModel?,
)

/** The circuit's round count: the LARGEST per-exercise row count, so no logged row is ever hidden. */
fun circuitRoundCount(exercises: List<WorkoutExerciseUiModel>): Int =
    exercises.maxOfOrNull { it.sets.size } ?: 0

/** Flips exercise-major rows into round-major cards. Empty when there are no exercises (or none has a row). */
fun buildCircuitRounds(exercises: List<WorkoutExerciseUiModel>): List<CircuitRound> {
    val rounds = circuitRoundCount(exercises)
    return (0 until rounds).map { roundIndex ->
        CircuitRound(
            roundNumber = roundIndex + 1,
            entries = exercises.map { ex -> CircuitRoundEntry(exercise = ex, set = ex.sets.getOrNull(roundIndex)) },
        )
    }
}

/**
 * The check-off auto-scroll target (§5.1.3 step 7's circuit analog): the next incomplete row in
 * the SAME round after [fromExerciseId], wrapping forward into later rounds' first incomplete row
 * and then around to the top. Returns the 0-based round index to scroll to, or null when nothing
 * incomplete remains anywhere.
 */
fun nextIncompleteCircuitPosition(
    exercises: List<WorkoutExerciseUiModel>,
    fromRoundIndex: Int,
    fromExerciseId: String,
): Int? {
    val rounds = buildCircuitRounds(exercises)
    if (rounds.isEmpty()) return null
    val fromExercisePos = exercises.indexOfFirst { it.id == fromExerciseId }
    // Walk every (round, exercise) cell in circuit order starting just after the checked cell.
    val total = rounds.size * exercises.size
    val startFlat = fromRoundIndex * exercises.size + (fromExercisePos + 1)
    for (offset in 0 until total) {
        val flat = (startFlat + offset).mod(total)
        val roundIndex = flat / exercises.size
        val entry = rounds.getOrNull(roundIndex)?.entries?.getOrNull(flat % exercises.size) ?: continue
        if (entry.set != null && !entry.set.isCompleted) return roundIndex
    }
    return null
}
