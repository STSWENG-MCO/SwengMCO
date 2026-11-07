package com.enil.logez.fakes

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.calc.resolvePreviousWorkoutSets
import com.enil.logez.core.domain.model.ExerciseHistoryEntry
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.core.domain.repository.WorkoutSetWithExercise
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2) — full mutable CRUD for the live logger's write-through edits. */
class FakeWorkoutRepository(
    workouts: List<WorkoutEntity> = emptyList(),
    exercises: List<WorkoutExerciseEntity> = emptyList(),
    sets: List<WorkoutSetEntity> = emptyList(),
    private val statSetsByExercise: Map<String, List<StatSet>> = emptyMap(),
    private val historyByExercise: Map<String, List<ExerciseHistoryEntry>> = emptyMap(),
    private val recentUsage: Map<String, Long> = emptyMap(),
) : WorkoutRepository {
    private val workoutsState = MutableStateFlow(workouts.associateBy { it.id })
    private val exercisesState = MutableStateFlow(exercises)
    private val setsState = MutableStateFlow(sets)

    override suspend fun getInProgress(): WorkoutEntity? = workoutsState.value.values.find { it.status.name == "IN_PROGRESS" }
    override fun observeInProgress(): Flow<WorkoutEntity?> = workoutsState.map { m -> m.values.find { it.status.name == "IN_PROGRESS" } }
    // Mirrors the DAO's `ORDER BY started_at DESC` — a fake that preserved insertion order instead
    // would let a History feed that forgot to sort pass every test against it.
    override fun observeCompleted(): Flow<List<WorkoutEntity>> =
        workoutsState.map { m -> m.values.filter { it.status.name == "COMPLETED" }.sortedByDescending { it.startedAt } }
    // Mirrors the DAO's `ORDER BY started_at ASC` on the one-shot read.
    override suspend fun getCompletedWorkouts(): List<WorkoutEntity> =
        workoutsState.value.values.filter { it.status.name == "COMPLETED" }.sortedBy { it.startedAt }
    override suspend fun getById(id: String): WorkoutEntity? = workoutsState.value[id]
    override fun observeById(id: String): Flow<WorkoutEntity?> = workoutsState.map { it[id] }
    override suspend fun updateWorkout(workout: WorkoutEntity) { workoutsState.update { it + (workout.id to workout) } }
    override suspend fun deleteById(id: String) {
        workoutsState.update { it - id }
        val orphaned = exercisesState.value.filter { it.workoutId == id }.map { it.id }.toSet()
        exercisesState.update { list -> list.filterNot { it.workoutId == id } }
        setsState.update { list -> list.filterNot { it.workoutExerciseId in orphaned } }
    }

    override suspend fun getExercisesForWorkout(workoutId: String): List<WorkoutExerciseEntity> =
        exercisesState.value.filter { it.workoutId == workoutId }.sortedBy { it.orderIndex }

    override fun observeExercisesForWorkout(workoutId: String): Flow<List<WorkoutExerciseEntity>> =
        exercisesState.map { list -> list.filter { it.workoutId == workoutId }.sortedBy { it.orderIndex } }

    override suspend fun getSetsForWorkoutExercise(workoutExerciseId: String): List<WorkoutSetEntity> =
        setsState.value.filter { it.workoutExerciseId == workoutExerciseId }.sortedBy { it.orderIndex }

    override suspend fun insertFullWorkout(workout: WorkoutEntity, exercises: List<WorkoutExerciseEntity>, sets: List<WorkoutSetEntity>) {
        workoutsState.update { it + (workout.id to workout) }
        exercisesState.update { it + exercises }
        setsState.update { it + sets }
    }

    override suspend fun insertWorkoutExercises(exercises: List<WorkoutExerciseEntity>) { exercisesState.update { it + exercises } }
    override suspend fun insertWorkoutSets(sets: List<WorkoutSetEntity>) { setsState.update { it + sets } }
    override suspend fun insertWorkoutSet(set: WorkoutSetEntity) { setsState.update { it + set } }

    override suspend fun updateWorkoutSet(set: WorkoutSetEntity) {
        setsState.update { list -> list.map { if (it.id == set.id) set else it } }
    }

    override suspend fun updateWorkoutSetWeight(id: String, kg: Double?) = mutateSet(id) { it.copy(weightKg = kg) }
    override suspend fun updateWorkoutSetReps(id: String, reps: Int?) = mutateSet(id) { it.copy(reps = reps) }
    override suspend fun updateWorkoutSetDuration(id: String, seconds: Int?) = mutateSet(id) { it.copy(durationSeconds = seconds) }
    override suspend fun updateWorkoutSetDistance(id: String, meters: Double?) = mutateSet(id) { it.copy(distanceMeters = meters) }
    override suspend fun updateWorkoutSetCustomMetric(id: String, value: Double?) = mutateSet(id) { it.copy(customMetric = value) }
    override suspend fun updateWorkoutSetRpe(id: String, rpe: Double?) = mutateSet(id) { it.copy(rpe = rpe) }
    override suspend fun updateWorkoutSetType(id: String, type: SetType) = mutateSet(id) { it.copy(setType = type) }
    override suspend fun updateWorkoutSetCompletion(id: String, completed: Boolean, completedAt: Long?) =
        mutateSet(id) { it.copy(isCompleted = completed, completedAt = completedAt) }

    override suspend fun updateWorkoutSetOrderIndex(id: String, orderIndex: Int) =
        mutateSet(id) { it.copy(orderIndex = orderIndex) }

    private fun mutateSet(id: String, transform: (WorkoutSetEntity) -> WorkoutSetEntity) {
        setsState.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    override suspend fun deleteWorkoutSet(id: String) { setsState.update { list -> list.filterNot { it.id == id } } }

    override suspend fun deleteWorkoutExercise(id: String) {
        exercisesState.update { list -> list.filterNot { it.id == id } }
        setsState.update { list -> list.filterNot { it.workoutExerciseId == id } }
    }

    override suspend fun updateWorkoutExerciseOrderIndex(id: String, orderIndex: Int) =
        mutateExercise(id) { it.copy(orderIndex = orderIndex) }

    override suspend fun reorderWorkoutExercises(orderedIds: List<String>) =
        orderedIds.forEachIndexed { index, id -> mutateExercise(id) { it.copy(orderIndex = index) } }

    override suspend fun updateWorkoutExerciseSuperset(id: String, supersetGroup: Int?) =
        mutateExercise(id) { it.copy(supersetGroup = supersetGroup) }

    override suspend fun updateWorkoutExerciseNotes(id: String, notes: String?) =
        mutateExercise(id) { it.copy(notes = notes) }

    override suspend fun updateWorkoutExerciseRestTimer(id: String, seconds: Int?) =
        mutateExercise(id) { it.copy(restTimerSeconds = seconds) }

    private fun mutateExercise(id: String, transform: (WorkoutExerciseEntity) -> WorkoutExerciseEntity) {
        exercisesState.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    override suspend fun replaceWorkoutExerciseExercise(workoutExerciseId: String, newExerciseId: String, carriedOverSets: List<WorkoutSetEntity>) {
        mutateExercise(workoutExerciseId) { it.copy(exerciseId = newExerciseId) }
        carriedOverSets.forEach { s -> setsState.update { list -> list.map { if (it.id == s.id) s else it } } }
    }

    /**
     * Mirrors the real query, which joins `workout_sets -> workout_exercises -> workouts` filtered
     * on `status = 'COMPLETED'`: sets living in this fake's own tables become stat sets as soon as
     * their workout completes. [statSetsByExercise] stays supported as a way for a test to seed
     * *prior* history it never inserted as rows.
     */
    override suspend fun getStatSetsForExercise(exerciseId: String): List<StatSet> {
        val seeded = statSetsByExercise[exerciseId].orEmpty()
        val completedWorkoutIds = workoutsState.value.values.filter { it.status.name == "COMPLETED" }.map { it.id }.toSet()
        val live = exercisesState.value
            .filter { it.exerciseId == exerciseId && it.workoutId in completedWorkoutIds }
            .flatMap { we -> getSetsWithExerciseForWorkoutSync(we.workoutId).filter { it.exerciseId == exerciseId } }
            .map { it.set }
        return seeded + live
    }

    override suspend fun getPreviousWorkoutSets(exerciseId: String, mode: PreviousValuesMode, currentRoutineId: String?, beforeStartedAt: Long?): List<StatSet> =
        resolvePreviousWorkoutSets(statSetsByExercise[exerciseId].orEmpty(), mode, currentRoutineId, beforeStartedAt)

    override suspend fun getPreviousWorkoutSetsBulk(exerciseIds: List<String>, mode: PreviousValuesMode, currentRoutineId: String?, beforeStartedAt: Long?): Map<String, List<StatSet>> =
        exerciseIds.associateWith { id ->
            resolvePreviousWorkoutSets(statSetsByExercise[id].orEmpty(), mode, currentRoutineId, beforeStartedAt)
        }

    override suspend fun getExerciseHistory(exerciseId: String): List<ExerciseHistoryEntry> = historyByExercise[exerciseId].orEmpty()

    override suspend fun getRecentUsageTimestamps(): Map<String, Long> = recentUsage

    // --- M4c finish flow ---

    /** Mirrors the real DAO transaction: purge uncompleted sets, prune exercises they emptied, then update. */
    override suspend fun finishWorkout(workout: WorkoutEntity) {
        val exerciseIds = exercisesState.value.filter { it.workoutId == workout.id }.map { it.id }.toSet()
        setsState.update { list -> list.filterNot { it.workoutExerciseId in exerciseIds && !it.isCompleted } }
        val stillPopulated = setsState.value.map { it.workoutExerciseId }.toSet()
        exercisesState.update { list -> list.filterNot { it.workoutId == workout.id && it.id !in stillPopulated } }
        workoutsState.update { it + (workout.id to workout) }
    }

    override suspend fun getSetsWithExerciseForWorkout(workoutId: String): List<WorkoutSetWithExercise> =
        getSetsWithExerciseForWorkoutSync(workoutId)

    // Mirrors the DAO's `WHERE status = 'COMPLETED' ORDER BY started_at DESC` — an unfiltered fake
    // would hide a feed that accidentally surfaced IN_PROGRESS workouts as cards.
    override suspend fun getSetsWithExerciseForCompletedWorkouts(): List<WorkoutSetWithExercise> =
        workoutsState.value.values
            .filter { it.status.name == "COMPLETED" }
            .sortedByDescending { it.startedAt }
            .flatMap { getSetsWithExerciseForWorkoutSync(it.id) }

    private fun getSetsWithExerciseForWorkoutSync(workoutId: String): List<WorkoutSetWithExercise> {
        val exercises = exercisesState.value.filter { it.workoutId == workoutId }.sortedBy { it.orderIndex }
        val workout = workoutsState.value[workoutId]
        return exercises.flatMap { we ->
            setsState.value
                .filter { it.workoutExerciseId == we.id }
                .sortedBy { it.orderIndex }
                .map { s ->
                    WorkoutSetWithExercise(
                        exerciseId = we.exerciseId,
                        workoutExerciseId = we.id,
                        exerciseOrderIndex = we.orderIndex,
                        set = StatSet(
                            setId = s.id,
                            workoutId = workoutId,
                            workoutStartedAt = workout?.startedAt ?: 0L,
                            orderIndex = s.orderIndex,
                            setType = s.setType,
                            weightKg = s.weightKg,
                            reps = s.reps,
                            durationSeconds = s.durationSeconds,
                            distanceMeters = s.distanceMeters,
                            customMetric = s.customMetric,
                            isCompleted = s.isCompleted,
                            rpe = s.rpe,
                            routineId = workout?.routineId,
                        ),
                    )
                }
        }
    }

    // Mirrors the DAO's total order exactly: strictly-earlier workouts, plus same-instant ones
    // whose id does not sort after this workout's.
    override suspend fun countCompletedWorkoutsUpTo(startedAt: Long, workoutId: String): Int =
        workoutsState.value.values.count {
            it.status.name == "COMPLETED" &&
                (it.startedAt < startedAt || (it.startedAt == startedAt && it.id <= workoutId))
        }

    // Mirrors the DAO transaction: dropping the exercises cascades their sets, then re-insert.
    override suspend fun replaceWorkoutStructure(
        workout: WorkoutEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
    ) {
        val orphaned = exercisesState.value.filter { it.workoutId == workout.id }.map { it.id }.toSet()
        exercisesState.update { list -> list.filterNot { it.workoutId == workout.id } }
        setsState.update { list -> list.filterNot { it.workoutExerciseId in orphaned } }
        exercisesState.update { it + exercises }
        setsState.update { it + sets }
        workoutsState.update { it + (workout.id to workout) }
    }

    override suspend fun getCompletedWorkoutsOn(fromMillis: Long, untilMillis: Long): List<WorkoutEntity> =
        workoutsState.value.values
            .filter { it.status.name == "COMPLETED" && it.startedAt >= fromMillis && it.startedAt < untilMillis }
            .sortedBy { it.startedAt }

    override suspend fun getCompletedWorkoutTimestamps(): List<Long> =
        workoutsState.value.values.filter { it.status.name == "COMPLETED" }.map { it.startedAt }.sortedDescending()
}
