package com.enil.logez.feature.workout

import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.workout.session.WorkoutSessionController
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Circuit-mode round operations (Add Round / Remove Round / the "has this round been logged into"
 * check) for the live workout logger — pulled out of [WorkoutLoggerViewModel] (2026-09-19 debt
 * audit). Operates directly on the ViewModel's own shared [exercises] state and routes every write
 * through the same [persist] the ViewModel itself uses for every other edit on this screen, so
 * live/edit-mode semantics stay identical. The circuit-mode guard (only meaningful when the
 * workout's structure is actually CIRCUIT) stays in the ViewModel's own thin public methods, since
 * that check depends on state ([WorkoutLoggerViewModel.isCircuit]) this class has no need to know
 * about otherwise.
 */
class CircuitRoundEditor(
    private val exercises: MutableStateFlow<List<WorkoutExerciseUiModel>>,
    private val workoutRepository: WorkoutRepository,
    private val sessionController: WorkoutSessionController,
    private val persist: (suspend () -> Unit) -> Unit,
) {
    /**
     * "+ Add Round": appends one set row to EVERY exercise, pre-seeded from that exercise's
     * previous (last) round — the circuit-mode replacement for per-exercise + Add Set. All the new
     * rows land at the same orderIndex (= old round count), preserving the rectangle invariant.
     */
    fun addRound() {
        val current = exercises.value
        if (current.isEmpty()) return
        val newSetsByExercise = current.associate { ex ->
            val last = ex.sets.lastOrNull()
            ex.id to WorkoutSetUiModel(
                id = UUID.randomUUID().toString(),
                setType = SetType.NORMAL,
                weightKg = last?.weightKg,
                reps = last?.reps,
                durationSeconds = last?.durationSeconds,
                distanceMeters = last?.distanceMeters,
                customMetric = last?.customMetric,
            )
        }
        exercises.update { list -> list.map { ex -> newSetsByExercise[ex.id]?.let { ex.copy(sets = ex.sets + it) } ?: ex } }
        persist {
            val entities = current.mapNotNull { ex ->
                newSetsByExercise[ex.id]?.toEntity(ex.id)?.copy(orderIndex = ex.sets.size)
            }
            workoutRepository.insertWorkoutSets(entities)
        }
    }

    /**
     * Round header's "Remove Round" ([roundIndex] 0-based): drops that round's row from every
     * exercise that has one and re-indexes later rounds down, keeping every exercise's set count
     * identical and its orderIndex contiguous — the invariant everything else keys off.
     */
    fun removeRound(roundIndex: Int) {
        if (roundIndex < 0) return
        // The last remaining round is not removable: a zero-round circuit renders no entries at
        // all (exercises become unreachable), and a later Add Exercise would seed the newcomer
        // one row ahead of everyone else, permanently breaking the equal-row-count invariant.
        if (circuitRoundCount(exercises.value) <= 1) return
        val current = exercises.value
        val removedSetIds = mutableListOf<String>()
        current.forEach { ex ->
            ex.sets.getOrNull(roundIndex)?.let { doomed ->
                removedSetIds += doomed.id
                // A running inline stopwatch on a row that's about to vanish just stops (nothing to commit).
                sessionController.stopInlineTimer(ex.id, doomed.id)
            }
        }
        if (removedSetIds.isEmpty()) return
        exercises.update { list ->
            list.map { ex ->
                if (roundIndex >= ex.sets.size) ex else ex.copy(sets = ex.sets.filterIndexed { i, _ -> i != roundIndex })
            }
        }
        persist {
            removedSetIds.forEach { workoutRepository.deleteWorkoutSet(it) }
            // Re-index survivors past the removed round so orderIndex stays contiguous per exercise.
            exercises.value.forEach { ex ->
                ex.sets.forEachIndexed { index, s ->
                    if (index >= roundIndex) workoutRepository.updateWorkoutSetOrderIndex(s.id, index)
                }
            }
        }
    }

    /** How many rows a given round holds values in — the Remove Round confirm's "anything logged" check. */
    fun roundHasLoggedValues(roundIndex: Int): Boolean = exercises.value.any { ex ->
        ex.sets.getOrNull(roundIndex)?.let { s ->
            s.isCompleted || s.weightKg != null || s.reps != null || s.durationSeconds != null ||
                s.distanceMeters != null || s.customMetric != null
        } == true
    }
}
