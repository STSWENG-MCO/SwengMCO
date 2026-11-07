package com.enil.logez.core.domain.repository

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.model.ExerciseHistoryEntry
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.SetType
import kotlinx.coroutines.flow.Flow

/** The live/in-progress workout and completed-workout history — the app's central logging surface. */
interface WorkoutRepository {
    suspend fun getInProgress(): WorkoutEntity?
    fun observeInProgress(): Flow<WorkoutEntity?>
    fun observeCompleted(): Flow<List<WorkoutEntity>>
    suspend fun getCompletedWorkouts(): List<WorkoutEntity>
    suspend fun getById(id: String): WorkoutEntity?
    fun observeById(id: String): Flow<WorkoutEntity?>
    suspend fun updateWorkout(workout: WorkoutEntity)
    suspend fun deleteById(id: String)

    suspend fun getExercisesForWorkout(workoutId: String): List<WorkoutExerciseEntity>
    fun observeExercisesForWorkout(workoutId: String): Flow<List<WorkoutExerciseEntity>>
    suspend fun getSetsForWorkoutExercise(workoutExerciseId: String): List<WorkoutSetEntity>

    suspend fun insertFullWorkout(
        workout: WorkoutEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
    )

    /** Live logger write-through (§5.1.3 spine): every edit persists immediately. */
    suspend fun insertWorkoutExercises(exercises: List<WorkoutExerciseEntity>)
    suspend fun insertWorkoutSets(sets: List<WorkoutSetEntity>)
    suspend fun insertWorkoutSet(set: WorkoutSetEntity)
    suspend fun updateWorkoutSet(set: WorkoutSetEntity)
    suspend fun updateWorkoutSetWeight(id: String, kg: Double?)
    suspend fun updateWorkoutSetReps(id: String, reps: Int?)
    suspend fun updateWorkoutSetDuration(id: String, seconds: Int?)
    suspend fun updateWorkoutSetDistance(id: String, meters: Double?)
    suspend fun updateWorkoutSetCustomMetric(id: String, value: Double?)
    suspend fun updateWorkoutSetRpe(id: String, rpe: Double?)
    suspend fun updateWorkoutSetType(id: String, type: SetType)
    suspend fun updateWorkoutSetCompletion(id: String, completed: Boolean, completedAt: Long?)
    /** M11 circuits: Remove Round re-indexes every exercise's later rounds down to keep orderIndex contiguous. */
    suspend fun updateWorkoutSetOrderIndex(id: String, orderIndex: Int)
    suspend fun deleteWorkoutSet(id: String)
    suspend fun deleteWorkoutExercise(id: String)
    suspend fun updateWorkoutExerciseOrderIndex(id: String, orderIndex: Int)

    /** M20a: stamps `orderIndex` 0..n-1 over [orderedIds] in one transaction (a drag-reorder drop). */
    suspend fun reorderWorkoutExercises(orderedIds: List<String>)
    suspend fun updateWorkoutExerciseSuperset(id: String, supersetGroup: Int?)
    suspend fun updateWorkoutExerciseNotes(id: String, notes: String?)
    suspend fun updateWorkoutExerciseRestTimer(id: String, seconds: Int?)

    /** §5.1.3 Replace Exercise: swaps the exercise id and rewrites the *same* set rows with field-carried-over values (never deletes/reinserts — a live logger keeps row identity mid-session). */
    suspend fun replaceWorkoutExerciseExercise(workoutExerciseId: String, newExerciseId: String, carriedOverSets: List<WorkoutSetEntity>)

    /** §8.1's join, already mapped to the calc-engine input type. */
    suspend fun getStatSetsForExercise(exerciseId: String): List<StatSet>

    /** §8.10 PREVIOUS column: the matching set from the most recent qualifying COMPLETED workout, by orderIndex. */
    suspend fun getPreviousWorkoutSets(exerciseId: String, mode: PreviousValuesMode, currentRoutineId: String?, beforeStartedAt: Long? = null): List<StatSet>

    /** Batch version: resolves PREVIOUS for multiple exercises at once, avoiding N+1 queries. */
    suspend fun getPreviousWorkoutSetsBulk(exerciseIds: List<String>, mode: PreviousValuesMode, currentRoutineId: String?, beforeStartedAt: Long? = null): Map<String, List<StatSet>>

    /** Exercise Detail's History tab (§5.2): every COMPLETED session containing the exercise, newest first. */
    suspend fun getExerciseHistory(exerciseId: String): List<ExerciseHistoryEntry>

    /** Exercise Library's "recently logged first" sort (§5.2): exerciseId -> most recent completedAt. */
    suspend fun getRecentUsageTimestamps(): Map<String, Long>

    // --- M4c finish flow (§5.1.8) ---

    /** §5.1.8's atomic save transaction: purge uncompleted sets (and exercises they emptied), then mark COMPLETED. */
    suspend fun finishWorkout(workout: WorkoutEntity)

    /** Every set of one workout paired with its exerciseId — the summary's stats and the PR-rebuild target list. */
    suspend fun getSetsWithExerciseForWorkout(workoutId: String): List<WorkoutSetWithExercise>

    /** The same, across every COMPLETED workout at once — the History feed's per-card stats in one query (§5.2). */
    suspend fun getSetsWithExerciseForCompletedWorkouts(): List<WorkoutSetWithExercise>

    /** Ordinal position of this workout among COMPLETED ones, for the summary's "Workout #N". */
    suspend fun countCompletedWorkoutsUpTo(startedAt: Long, workoutId: String): Int

    /** §8.7 streak input: `started_at` of every COMPLETED workout, newest first. */
    suspend fun getCompletedWorkoutTimestamps(): List<Long>

    /** §5.2 Calendar day sheet: COMPLETED workouts started within [fromMillis, untilMillis). */
    suspend fun getCompletedWorkoutsOn(fromMillis: Long, untilMillis: Long): List<WorkoutEntity>

    // --- M5b edit flow (§5.1.10) ---

    /** Swaps a workout's whole child structure for an edited one, and updates the row — one transaction. */
    suspend fun replaceWorkoutStructure(
        workout: WorkoutEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
    )
}

/**
 * Domain-layer view of [com.enil.logez.core.data.dao.WorkoutSetWithExerciseRow] — a StatSet that
 * also knows which exercise, and which *block* of it, produced the set. The block identity matters
 * because one workout can hold two blocks of the same exercise; see the DAO query's KDoc.
 */
data class WorkoutSetWithExercise(
    val exerciseId: String,
    val workoutExerciseId: String,
    val exerciseOrderIndex: Int,
    val set: com.enil.logez.core.domain.calc.StatSet,
)
