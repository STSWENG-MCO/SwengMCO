package com.enil.logez.core.data.repository

import com.enil.logez.core.data.dao.AnalyticsDao
import com.enil.logez.core.data.dao.ExerciseStatRow
import com.enil.logez.core.data.dao.ExerciseStatRowWithExerciseId
import com.enil.logez.core.data.dao.StatSetRowFields
import com.enil.logez.core.data.dao.WorkoutDao
import com.enil.logez.core.data.dao.WorkoutSetWithExerciseIdRow
import com.enil.logez.core.data.dao.WorkoutSetWithExerciseRow
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
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class WorkoutRepositoryImpl @Inject constructor(
    private val dao: WorkoutDao,
    private val analyticsDao: AnalyticsDao,
) : WorkoutRepository {
    override suspend fun getInProgress(): WorkoutEntity? = dao.getInProgress()
    override fun observeInProgress(): Flow<WorkoutEntity?> = dao.observeInProgress()
    override fun observeCompleted(): Flow<List<WorkoutEntity>> = dao.observeCompleted()
    override suspend fun getCompletedWorkouts(): List<WorkoutEntity> = dao.getCompletedWorkouts()
    override suspend fun getById(id: String): WorkoutEntity? = dao.getById(id)
    override fun observeById(id: String): Flow<WorkoutEntity?> = dao.observeById(id)
    override suspend fun updateWorkout(workout: WorkoutEntity) = dao.updateWorkout(workout)
    override suspend fun deleteById(id: String) = dao.deleteWorkoutById(id)

    override suspend fun getExercisesForWorkout(workoutId: String): List<WorkoutExerciseEntity> =
        dao.getExercisesForWorkout(workoutId)

    override fun observeExercisesForWorkout(workoutId: String): Flow<List<WorkoutExerciseEntity>> =
        dao.observeExercisesForWorkout(workoutId)

    override suspend fun getSetsForWorkoutExercise(workoutExerciseId: String): List<WorkoutSetEntity> =
        dao.getSetsForWorkoutExercise(workoutExerciseId)

    override suspend fun insertFullWorkout(
        workout: WorkoutEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
    ) = dao.insertFullWorkout(workout, exercises, sets)

    override suspend fun insertWorkoutExercises(exercises: List<WorkoutExerciseEntity>) = dao.insertWorkoutExercises(exercises)
    override suspend fun insertWorkoutSets(sets: List<WorkoutSetEntity>) = dao.insertWorkoutSets(sets)
    override suspend fun insertWorkoutSet(set: WorkoutSetEntity) = dao.insertWorkoutSet(set)
    override suspend fun updateWorkoutSet(set: WorkoutSetEntity) = dao.updateWorkoutSet(set)
    override suspend fun updateWorkoutSetWeight(id: String, kg: Double?) = dao.updateWorkoutSetWeight(id, kg)
    override suspend fun updateWorkoutSetReps(id: String, reps: Int?) = dao.updateWorkoutSetReps(id, reps)
    override suspend fun updateWorkoutSetDuration(id: String, seconds: Int?) = dao.updateWorkoutSetDuration(id, seconds)
    override suspend fun updateWorkoutSetDistance(id: String, meters: Double?) = dao.updateWorkoutSetDistance(id, meters)
    override suspend fun updateWorkoutSetCustomMetric(id: String, value: Double?) = dao.updateWorkoutSetCustomMetric(id, value)
    override suspend fun updateWorkoutSetRpe(id: String, rpe: Double?) = dao.updateWorkoutSetRpe(id, rpe)
    override suspend fun updateWorkoutSetType(id: String, type: SetType) = dao.updateWorkoutSetType(id, type)
    override suspend fun updateWorkoutSetCompletion(id: String, completed: Boolean, completedAt: Long?) = dao.updateWorkoutSetCompletion(id, completed, completedAt)
    override suspend fun updateWorkoutSetOrderIndex(id: String, orderIndex: Int) = dao.updateWorkoutSetOrderIndex(id, orderIndex)
    override suspend fun deleteWorkoutSet(id: String) = dao.deleteWorkoutSetById(id)
    override suspend fun deleteWorkoutExercise(id: String) = dao.deleteWorkoutExerciseById(id)
    override suspend fun updateWorkoutExerciseOrderIndex(id: String, orderIndex: Int) = dao.updateWorkoutExerciseOrderIndex(id, orderIndex)
    override suspend fun reorderWorkoutExercises(orderedIds: List<String>) = dao.reorderWorkoutExercises(orderedIds)
    override suspend fun updateWorkoutExerciseSuperset(id: String, supersetGroup: Int?) = dao.updateWorkoutExerciseSuperset(id, supersetGroup)
    override suspend fun updateWorkoutExerciseNotes(id: String, notes: String?) = dao.updateWorkoutExerciseNotes(id, notes)
    override suspend fun updateWorkoutExerciseRestTimer(id: String, seconds: Int?) = dao.updateWorkoutExerciseRestTimer(id, seconds)

    override suspend fun replaceWorkoutExerciseExercise(workoutExerciseId: String, newExerciseId: String, carriedOverSets: List<WorkoutSetEntity>) =
        dao.replaceWorkoutExerciseExercise(workoutExerciseId, newExerciseId, carriedOverSets)

    override suspend fun getStatSetsForExercise(exerciseId: String): List<StatSet> =
        dao.getStatRowsForExercise(exerciseId).map { it.toStatSet() }

    override suspend fun getPreviousWorkoutSets(exerciseId: String, mode: PreviousValuesMode, currentRoutineId: String?, beforeStartedAt: Long?): List<StatSet> =
        resolvePreviousWorkoutSets(dao.getStatRowsForExercise(exerciseId).map { it.toStatSet() }, mode, currentRoutineId, beforeStartedAt)

    override suspend fun getPreviousWorkoutSetsBulk(exerciseIds: List<String>, mode: PreviousValuesMode, currentRoutineId: String?, beforeStartedAt: Long?): Map<String, List<StatSet>> {
        if (exerciseIds.isEmpty()) return emptyMap()
        // Fetch all stat rows for all exercises in one DAO call, group by exercise, then convert.
        val allRows = dao.getStatRowsForExercises(exerciseIds)
        val grouped = allRows.groupBy({ it.exerciseId }, { it.toStatSet() })
        return exerciseIds.associateWith { id ->
            resolvePreviousWorkoutSets(grouped[id].orEmpty(), mode, currentRoutineId, beforeStartedAt)
        }
    }

    /**
     * Returns all sets for a workout grouped by `workout_exercise_id` — replaces N per-instance
     * queries with one. Keyed by the workout-exercise instance id (NOT the exercise id): the same
     * exercise can appear twice in one workout (nothing dedups the picker or addExercises), and
     * grouping by exercise id would merge both instances' sets into every card sharing the
     * exercise, corrupting counts, PREVIOUS pairing, and persisted orderIndex on addSet.
     */
    suspend fun getAllSetsForWorkoutGroupedByWorkoutExercise(workoutId: String): Map<String, List<WorkoutSetEntity>> {
        val rows = dao.getAllSetsForWorkout(workoutId)
        return rows.groupBy({ it.workoutExerciseId }, { row ->
            WorkoutSetEntity(
                id = row.id,
                workoutExerciseId = row.workoutExerciseId,
                orderIndex = row.orderIndex,
                setType = row.setType,
                weightKg = row.weightKg,
                reps = row.reps,
                durationSeconds = row.durationSeconds,
                distanceMeters = row.distanceMeters,
                rpe = row.rpe,
                customMetric = row.customMetric,
                isCompleted = row.isCompleted,
                completedAt = row.completedAt,
            )
        })
    }

    override suspend fun getExerciseHistory(exerciseId: String): List<ExerciseHistoryEntry> =
        dao.getStatRowsForExercise(exerciseId).map { it.toHistoryEntry() }

    override suspend fun getRecentUsageTimestamps(): Map<String, Long> =
        dao.getMostRecentUsagePerExercise()
            .mapNotNull { row -> row.lastCompletedAt?.let { row.exerciseId to it } }
            .toMap()

    // --- M4c finish flow (§5.1.8) ---

    override suspend fun finishWorkout(workout: WorkoutEntity) = dao.finishWorkout(workout)

    override suspend fun getSetsWithExerciseForWorkout(workoutId: String): List<WorkoutSetWithExercise> =
        dao.getSetsWithExerciseForWorkout(workoutId).map(WorkoutSetWithExerciseRow::toDomain)

    override suspend fun getSetsWithExerciseForCompletedWorkouts(): List<WorkoutSetWithExercise> =
        dao.getSetsWithExerciseForCompletedWorkouts().map(WorkoutSetWithExerciseRow::toDomain)

    override suspend fun countCompletedWorkoutsUpTo(startedAt: Long, workoutId: String): Int =
        dao.countCompletedWorkoutsUpTo(startedAt, workoutId)

    override suspend fun getCompletedWorkoutTimestamps(): List<Long> = analyticsDao.getCompletedWorkoutTimestamps()

    override suspend fun getCompletedWorkoutsOn(fromMillis: Long, untilMillis: Long): List<WorkoutEntity> =
        dao.getCompletedWorkoutsBetween(fromMillis, untilMillis)

    // --- M5b edit flow (§5.1.10) ---

    override suspend fun replaceWorkoutStructure(
        workout: WorkoutEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
    ) = dao.replaceWorkoutStructure(workout, exercises, sets)
}

private fun WorkoutSetWithExerciseRow.toDomain() =
    WorkoutSetWithExercise(exerciseId, workoutExerciseId, exerciseOrderIndex, toStatSet())

/** The one mapping shared by every StatSet-projecting DAO row — see [StatSetRowFields]. */
private fun StatSetRowFields.toStatSet() = StatSet(
    setId = setId,
    workoutId = workoutId,
    workoutStartedAt = workoutStartedAt,
    orderIndex = orderIndex,
    setType = setType,
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    customMetric = customMetric,
    isCompleted = isCompleted,
    rpe = rpe,
    routineId = routineId,
)

private fun ExerciseStatRow.toHistoryEntry() = ExerciseHistoryEntry(
    workoutId = workoutId,
    workoutTitle = workoutTitle,
    workoutStartedAt = workoutStartedAt,
    setId = setId,
    setOrderIndex = orderIndex,
    setType = setType,
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    rpe = rpe,
    customMetric = customMetric,
)
