package com.enil.logez.feature.history

import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.domain.repository.RoutineRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE2_PLAN.md §5.2 Workout Detail "Save as Routine": "creates a routine from the exercise/set
 * structure, opening the routine editor pre-filled."
 *
 * The session's logged values become the routine's targets, which is the whole point — a routine
 * built from a workout should start you where you actually finished. Rep ranges are the one thing
 * a session cannot express, so they are simply absent rather than invented from the reps performed
 * (the same rule §8.10 applies when a finished workout updates its source routine's values).
 */
@Singleton
class WorkoutToRoutineConverter @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val clock: Clock,
) {
    /** Returns the new routine's id, or null if the workout has vanished. */
    suspend fun convert(workoutId: String): String? {
        val workout = workoutRepository.getById(workoutId) ?: return null
        val workoutExercises = workoutRepository.getExercisesForWorkout(workoutId).sortedBy { it.orderIndex }
        if (workoutExercises.isEmpty()) return null

        val now = clock.now().toEpochMilliseconds()
        val routineId = UUID.randomUUID().toString()

        val routineExercises = workoutExercises.mapIndexed { index, we ->
            RoutineExerciseEntity(
                id = UUID.randomUUID().toString(),
                routineId = routineId,
                exerciseId = we.exerciseId,
                orderIndex = index,
                supersetGroup = we.supersetGroup,
                restTimerSeconds = we.restTimerSeconds,
                // The session note ("shoulder tight today") is commentary on one workout, not a
                // standing coaching cue — WorkoutExerciseEntity documents the two as distinct, and
                // the finish flow's structural rewrite makes the same call.
                notes = null,
            )
        }

        val setsByExercise = workoutExercises.map { we ->
            workoutRepository.getSetsForWorkoutExercise(we.id).sortedBy { it.orderIndex }
        }
        val isCircuit = workout.structure == WorkoutStructure.CIRCUIT
        // In a circuit, a set's orderIndex IS its round, and the finish-flow purge leaves gaps
        // where rounds were skipped — compacting by list position here would shift a surviving
        // later round's values onto an earlier round in the new routine (the same misattribution
        // WorkoutFinisher's circuit rewrite guards against). Map by round instead, padding a
        // skipped round from the nearest earlier surviving one so the routine stays rectangular.
        val circuitRounds = if (isCircuit) setsByExercise.maxOf { rows -> rows.maxOfOrNull { it.orderIndex + 1 } ?: 0 } else 0
        val routineSets = setsByExercise.flatMapIndexed { index: Int, rows ->
            if (isCircuit) {
                // A finished workout shouldn't hold a zero-set exercise, but if one slips through,
                // emitting no rows (builder pads on open) beats crashing on rows.first() below.
                if (rows.isEmpty()) return@flatMapIndexed emptyList()
                val byRound = rows.associateBy { it.orderIndex }
                (0 until circuitRounds).map { round ->
                    val ws = byRound[round] ?: rows.lastOrNull { it.orderIndex < round } ?: rows.first()
                    RoutineSetEntity(
                        id = UUID.randomUUID().toString(),
                        routineExerciseId = routineExercises[index].id,
                        orderIndex = round,
                        setType = ws.setType,
                        targetWeightKg = ws.weightKg,
                        targetReps = ws.reps,
                        targetRepRangeMin = null,
                        targetRepRangeMax = null,
                        targetDurationSeconds = ws.durationSeconds,
                        targetDistanceMeters = ws.distanceMeters,
                    )
                }
            } else {
                rows.mapIndexed { setIndex, ws ->
                    RoutineSetEntity(
                        id = UUID.randomUUID().toString(),
                        routineExerciseId = routineExercises[index].id,
                        orderIndex = setIndex,
                        setType = ws.setType,
                        targetWeightKg = ws.weightKg,
                        targetReps = ws.reps,
                        targetRepRangeMin = null,
                        targetRepRangeMax = null,
                        targetDurationSeconds = ws.durationSeconds,
                        targetDistanceMeters = ws.distanceMeters,
                    )
                }
            }
        }

        routineRepository.createRoutineAtTop(
            RoutineEntity(
                id = routineId,
                folderId = null,
                name = workout.title,
                notes = null,
                orderIndex = 0,
                createdAt = now,
                updatedAt = now,
                // M11: a circuit workout saves as a circuit routine — targets mapped round-by-round
                // (orderIndex), with purged rounds padded, in the flatMapIndexed block above.
                structure = workout.structure,
            ),
            dropOrphanSupersets(routineExercises),
            routineSets,
        )
        return routineId
    }

    /**
     * A superset group with one member is not a valid state anywhere in the app. It can arise here
     * because the workout's own save purged a partner whose sets were all left uncompleted.
     */
    private fun dropOrphanSupersets(exercises: List<RoutineExerciseEntity>): List<RoutineExerciseEntity> {
        val memberCount = exercises.mapNotNull { it.supersetGroup }.groupingBy { it }.eachCount()
        return exercises.map {
            if (it.supersetGroup != null && (memberCount[it.supersetGroup] ?: 0) < 2) it.copy(supersetGroup = null) else it
        }
    }
}
