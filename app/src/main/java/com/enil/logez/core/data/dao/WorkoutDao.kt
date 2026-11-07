package com.enil.logez.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.data.export.WorkoutCsvRow
import com.enil.logez.core.domain.model.SetType
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWorkout(workout: WorkoutEntity)

    @Update
    suspend fun updateWorkout(workout: WorkoutEntity)

    @Query("DELETE FROM workouts WHERE id = :id")
    suspend fun deleteWorkoutById(id: String)

    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<WorkoutEntity?>

    /** Spine anchor: at most one IN_PROGRESS row at a time — process-death recovery reads this. */
    @Query("SELECT * FROM workouts WHERE status = 'IN_PROGRESS' LIMIT 1")
    suspend fun getInProgress(): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE status = 'IN_PROGRESS' LIMIT 1")
    fun observeInProgress(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE status = 'COMPLETED' ORDER BY started_at DESC")
    fun observeCompleted(): Flow<List<WorkoutEntity>>

    /** One-shot completed list for the dashboard/Profile aggregates (§5.2), refreshed on RESUME. */
    @Query("SELECT * FROM workouts WHERE status = 'COMPLETED' ORDER BY started_at ASC")
    suspend fun getCompletedWorkouts(): List<WorkoutEntity>

    /**
     * §5.2 Calendar: the workouts on one local day, as a half-open [fromMillis, untilMillis) range.
     * The caller computes both bounds from `LocalDate.atStartOfDay(zone)`, so a day that is 23 or
     * 25 hours long across a DST change is still exactly one day — which a fixed +86_400_000 would
     * get wrong twice a year.
     */
    @Query("SELECT * FROM workouts WHERE status = 'COMPLETED' AND started_at >= :fromMillis AND started_at < :untilMillis ORDER BY started_at ASC")
    suspend fun getCompletedWorkoutsBetween(fromMillis: Long, untilMillis: Long): List<WorkoutEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutExercises(exercises: List<WorkoutExerciseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSets(sets: List<WorkoutSetEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSet(set: WorkoutSetEntity)

    @Update
    suspend fun updateWorkoutSet(set: WorkoutSetEntity)

    // Targeted single-field updates for the live logger's write-through edits (§5.1.3) — a
    // whole-row @Update reconstructed from the UI model would need every field (orderIndex,
    // completedAt, ...) the UI doesn't track, silently clobbering them on every keystroke.
    @Query("UPDATE workout_sets SET weight_kg = :kg WHERE id = :id")
    suspend fun updateWorkoutSetWeight(id: String, kg: Double?)

    @Query("UPDATE workout_sets SET reps = :reps WHERE id = :id")
    suspend fun updateWorkoutSetReps(id: String, reps: Int?)

    @Query("UPDATE workout_sets SET duration_seconds = :seconds WHERE id = :id")
    suspend fun updateWorkoutSetDuration(id: String, seconds: Int?)

    @Query("UPDATE workout_sets SET distance_meters = :meters WHERE id = :id")
    suspend fun updateWorkoutSetDistance(id: String, meters: Double?)

    @Query("UPDATE workout_sets SET custom_metric = :value WHERE id = :id")
    suspend fun updateWorkoutSetCustomMetric(id: String, value: Double?)

    @Query("UPDATE workout_sets SET rpe = :rpe WHERE id = :id")
    suspend fun updateWorkoutSetRpe(id: String, rpe: Double?)

    @Query("UPDATE workout_sets SET set_type = :type WHERE id = :id")
    suspend fun updateWorkoutSetType(id: String, type: SetType)

    @Query("UPDATE workout_sets SET is_completed = :completed, completed_at = :completedAt WHERE id = :id")
    suspend fun updateWorkoutSetCompletion(id: String, completed: Boolean, completedAt: Long?)

    /** M11 circuits: removing round k re-indexes every exercise's later rounds down by one. */
    @Query("UPDATE workout_sets SET order_index = :orderIndex WHERE id = :id")
    suspend fun updateWorkoutSetOrderIndex(id: String, orderIndex: Int)

    @Query("DELETE FROM workout_sets WHERE id = :id")
    suspend fun deleteWorkoutSetById(id: String)

    @Query("DELETE FROM workout_exercises WHERE id = :id")
    suspend fun deleteWorkoutExerciseById(id: String)

    @Query("UPDATE workout_exercises SET order_index = :orderIndex WHERE id = :id")
    suspend fun updateWorkoutExerciseOrderIndex(id: String, orderIndex: Int)

    /**
     * M20a: one transaction for a whole permutation, mirroring [RoutineDao.reorderRoutines] — the
     * live logger writes every drop through, and N separate UPDATEs could leave duplicate
     * `order_index` values behind a process death mid-loop or two overlapping drops.
     */
    @Transaction
    suspend fun reorderWorkoutExercises(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> updateWorkoutExerciseOrderIndex(id, index) }
    }

    @Query("UPDATE workout_exercises SET superset_group = :supersetGroup WHERE id = :id")
    suspend fun updateWorkoutExerciseSuperset(id: String, supersetGroup: Int?)

    @Query("UPDATE workout_exercises SET notes = :notes WHERE id = :id")
    suspend fun updateWorkoutExerciseNotes(id: String, notes: String?)

    @Query("UPDATE workout_exercises SET rest_timer_seconds = :seconds WHERE id = :id")
    suspend fun updateWorkoutExerciseRestTimer(id: String, seconds: Int?)

    @Query("UPDATE workout_exercises SET exercise_id = :newExerciseId WHERE id = :id")
    suspend fun updateWorkoutExerciseExerciseId(id: String, newExerciseId: String)

    /** §5.1.3 Replace Exercise: swaps the exercise id, rewrites the *same* set rows with carried-over values. */
    @Transaction
    suspend fun replaceWorkoutExerciseExercise(workoutExerciseId: String, newExerciseId: String, carriedOverSets: List<WorkoutSetEntity>) {
        updateWorkoutExerciseExerciseId(workoutExerciseId, newExerciseId)
        carriedOverSets.forEach { updateWorkoutSet(it) }
    }

    @Query("SELECT * FROM workout_exercises WHERE workout_id = :workoutId ORDER BY order_index ASC")
    suspend fun getExercisesForWorkout(workoutId: String): List<WorkoutExerciseEntity>

    @Query("SELECT * FROM workout_exercises WHERE workout_id = :workoutId ORDER BY order_index ASC")
    fun observeExercisesForWorkout(workoutId: String): Flow<List<WorkoutExerciseEntity>>

    @Query("SELECT * FROM workout_sets WHERE workout_exercise_id = :workoutExerciseId ORDER BY order_index ASC")
    suspend fun getSetsForWorkoutExercise(workoutExerciseId: String): List<WorkoutSetEntity>

    @Query("SELECT * FROM workout_sets WHERE workout_exercise_id = :workoutExerciseId ORDER BY order_index ASC")
    fun observeSetsForWorkoutExercise(workoutExerciseId: String): Flow<List<WorkoutSetEntity>>

    /**
     * Raw joined rows for one exercise across COMPLETED workouts — the source data for §8's
     * `StatSet` DTO (volume/1RM/PR/chart engines), §8.10's PREVIOUS-values resolution, AND the
     * Exercise Detail History tab (§5.2, which additionally needs the workout title — carried
     * here rather than duplicating an almost-identical query). Repository maps rows to `StatSet`
     * or `ExerciseHistoryEntry` depending on the caller; kept as one flat projection.
     */
    @Query(
        """
        SELECT ws.id AS setId, w.id AS workoutId, w.title AS workoutTitle, w.started_at AS workoutStartedAt,
               w.routine_id AS routineId, ws.order_index AS orderIndex, ws.set_type AS setType,
               ws.weight_kg AS weightKg, ws.reps AS reps, ws.duration_seconds AS durationSeconds,
               ws.distance_meters AS distanceMeters, ws.custom_metric AS customMetric,
               ws.is_completed AS isCompleted, ws.rpe AS rpe
        FROM workout_sets ws
        JOIN workout_exercises we ON we.id = ws.workout_exercise_id
        JOIN workouts w ON w.id = we.workout_id
        WHERE we.exercise_id = :exerciseId AND w.status = 'COMPLETED'
        ORDER BY w.started_at DESC, ws.order_index ASC
        """,
    )
    suspend fun getStatRowsForExercise(exerciseId: String): List<ExerciseStatRow>

    /** Batch version: fetches stat rows for multiple exercises at once, avoiding N+1 queries. */
    @Query(
        """
        SELECT ws.id AS setId, w.id AS workoutId, w.title AS workoutTitle, w.started_at AS workoutStartedAt,
               w.routine_id AS routineId, ws.order_index AS orderIndex, ws.set_type AS setType,
               ws.weight_kg AS weightKg, ws.reps AS reps, ws.duration_seconds AS durationSeconds,
               ws.distance_meters AS distanceMeters, ws.custom_metric AS customMetric,
               ws.is_completed AS isCompleted, ws.rpe AS rpe, we.exercise_id AS exerciseId
        FROM workout_sets ws
        JOIN workout_exercises we ON we.id = ws.workout_exercise_id
        JOIN workouts w ON w.id = we.workout_id
        WHERE we.exercise_id IN (:exerciseIds) AND w.status = 'COMPLETED'
        ORDER BY w.started_at DESC, ws.order_index ASC
        """,
    )
    suspend fun getStatRowsForExercises(exerciseIds: List<String>): List<ExerciseStatRowWithExerciseId>

    /**
     * Returns all sets for a workout in one query — replaces N per-workout-exercise queries.
     * The repository groups the rows by `workout_exercise_id` (the instance id, never the
     * exercise id — one exercise can appear twice in a workout). Used by
     * WorkoutLoggerViewModel.init.
     */
    @Query(
        """
        SELECT ws.*, we.exercise_id AS exerciseId
        FROM workout_sets ws
        JOIN workout_exercises we ON we.id = ws.workout_exercise_id
        WHERE we.workout_id = :workoutId
        ORDER BY we.order_index ASC, ws.order_index ASC
        """,
    )
    suspend fun getAllSetsForWorkout(workoutId: String): List<WorkoutSetWithExerciseIdRow>

    /**
     * Most recent `completed_at` per exercise, across all COMPLETED-workout sets — backs the
     * Exercise Library's "recently logged first" sort (§5.2).
     */
    @Query(
        """
        SELECT we.exercise_id AS exerciseId, MAX(ws.completed_at) AS lastCompletedAt
        FROM workout_sets ws
        JOIN workout_exercises we ON we.id = ws.workout_exercise_id
        JOIN workouts w ON w.id = we.workout_id
        WHERE ws.is_completed = 1 AND w.status = 'COMPLETED'
        GROUP BY we.exercise_id
        """,
    )
    suspend fun getMostRecentUsagePerExercise(): List<ExerciseRecencyRow>

    /** Whole-structure insert (workout + exercises + sets) — used by tests and the M4c save transaction. */
    @Transaction
    suspend fun insertFullWorkout(
        workout: WorkoutEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
    ) {
        upsertWorkout(workout)
        insertWorkoutExercises(exercises)
        insertWorkoutSets(sets)
    }

    // --- M4c finish flow (§5.1.8) ---

    /** §5.1.8 save transaction step 1: "delete uncompleted `workout_sets`". */
    @Query(
        """
        DELETE FROM workout_sets
        WHERE is_completed = 0
          AND workout_exercise_id IN (SELECT id FROM workout_exercises WHERE workout_id = :workoutId)
        """,
    )
    suspend fun deleteUncompletedSetsForWorkout(workoutId: String)

    /** Prunes exercises the uncompleted-set purge left empty — deleting sets does not cascade upward to their parent. */
    @Query(
        """
        DELETE FROM workout_exercises
        WHERE workout_id = :workoutId
          AND id NOT IN (SELECT DISTINCT workout_exercise_id FROM workout_sets)
        """,
    )
    suspend fun deleteEmptyExercisesForWorkout(workoutId: String)

    /**
     * §5.1.8's save transaction, atomic: purge uncompleted sets and any exercise they emptied,
     * then flip the workout to COMPLETED with its final (possibly user-edited, possibly
     * backdated) timestamps. Routine updates and the PR rebuild follow *after* this commits —
     * they read the COMPLETED rows this writes.
     */
    @Transaction
    suspend fun finishWorkout(workout: WorkoutEntity) {
        deleteUncompletedSetsForWorkout(workout.id)
        deleteEmptyExercisesForWorkout(workout.id)
        updateWorkout(workout)
    }

    @Query("DELETE FROM workout_exercises WHERE workout_id = :workoutId")
    suspend fun deleteExercisesForWorkout(workoutId: String)

    /**
     * §5.1.10's edit save: swap a workout's whole child structure for the edited one and update the
     * row itself. Deleting the exercises cascades their sets, so the re-insert starts from a clean
     * slate rather than diffing adds/removes/reorders/replacements against the live rows.
     *
     * Unlike the live logger — which never delete-and-reinserts, because row identity has to
     * survive mid-session — an edit already ends with a full `personal_records` rebuild, so the
     * fresh set ids get re-pointed at as part of the same transaction.
     */
    @Transaction
    suspend fun replaceWorkoutStructure(
        workout: WorkoutEntity,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
    ) {
        deleteExercisesForWorkout(workout.id)
        insertWorkoutExercises(exercises)
        insertWorkoutSets(sets)
        updateWorkout(workout)
    }

    /**
     * Every set of one workout with its exercise id, in one query — the finish summary's volume
     * and set counts, and the "which exercises need a PR rebuild" list, both need the whole
     * workout at once rather than the per-exercise N+1 the live logger uses.
     *
     * Carries `workoutExerciseId`/`exerciseOrderIndex` as well as `exerciseId`: the same exercise
     * can legitimately occupy two blocks in one session (a main lift plus a burnout block), so
     * anything pairing logged sets back to routine slots must key on the *block*, not the
     * exercise — keying on `exerciseId` alone silently merges the two.
     */
    @Query(
        """
        SELECT ws.id AS setId, we.exercise_id AS exerciseId, we.id AS workoutExerciseId,
               we.order_index AS exerciseOrderIndex, w.id AS workoutId, w.started_at AS workoutStartedAt,
               w.routine_id AS routineId, ws.order_index AS orderIndex, ws.set_type AS setType,
               ws.weight_kg AS weightKg, ws.reps AS reps, ws.duration_seconds AS durationSeconds,
               ws.distance_meters AS distanceMeters, ws.custom_metric AS customMetric,
               ws.is_completed AS isCompleted, ws.rpe AS rpe
        FROM workout_sets ws
        JOIN workout_exercises we ON we.id = ws.workout_exercise_id
        JOIN workouts w ON w.id = we.workout_id
        WHERE w.id = :workoutId
        ORDER BY we.order_index ASC, ws.order_index ASC
        """,
    )
    suspend fun getSetsWithExerciseForWorkout(workoutId: String): List<WorkoutSetWithExerciseRow>

    /**
     * The same projection across *every* COMPLETED workout at once — the History feed's card
     * stats (§5.2: "card stats come from a single DAO aggregate query"). The feed can't run the
     * per-workout query above once per card: a year of training is hundreds of cards, and each
     * would cost its own round trip on every emission of the feed Flow.
     *
     * Volume is deliberately still summed in Kotlin rather than SQL — §8.3's per-`ExerciseType`
     * branching (bodyweight-eligible, assisted, weighted) is a matrix `SUM()` cannot express, and
     * duplicating it in SQL would be a second source of truth for the app's most load-bearing
     * number. This query's job is to make that one pass over the data cheap, not to do the math.
     */
    @Query(
        """
        SELECT ws.id AS setId, we.exercise_id AS exerciseId, we.id AS workoutExerciseId,
               we.order_index AS exerciseOrderIndex, w.id AS workoutId, w.started_at AS workoutStartedAt,
               w.routine_id AS routineId, ws.order_index AS orderIndex, ws.set_type AS setType,
               ws.weight_kg AS weightKg, ws.reps AS reps, ws.duration_seconds AS durationSeconds,
               ws.distance_meters AS distanceMeters, ws.custom_metric AS customMetric,
               ws.is_completed AS isCompleted, ws.rpe AS rpe
        FROM workout_sets ws
        JOIN workout_exercises we ON we.id = ws.workout_exercise_id
        JOIN workouts w ON w.id = we.workout_id
        WHERE w.status = 'COMPLETED'
        ORDER BY w.started_at DESC, we.order_index ASC, ws.order_index ASC
        """,
    )
    suspend fun getSetsWithExerciseForCompletedWorkouts(): List<WorkoutSetWithExerciseRow>

    /**
     * Every logged set, joined with the names the CSV export needs. Distinct from
     * [getSetsWithExerciseForCompletedWorkouts] on three counts: it carries the exercise NAME and
     * the workout/exercise notes (which the stats query has no use for), and it orders ascending
     * because an export reads as a chronological record rather than a feed.
     *
     * No filter on `is_completed`: finishing a workout already purges uncompleted sets, and every
     * set being a row is what the target schema expects.
     */
    @Query(
        """
        SELECT w.title AS workoutTitle, w.notes AS workoutNotes, w.started_at AS startedAt,
               w.ended_at AS endedAt, e.name AS exerciseName, we.superset_group AS supersetGroup,
               we.notes AS exerciseNotes, ws.order_index AS setIndex, ws.set_type AS setType,
               ws.weight_kg AS weightKg, ws.reps AS reps, ws.distance_meters AS distanceMeters,
               ws.duration_seconds AS durationSeconds, ws.rpe AS rpe
        FROM workout_sets ws
        JOIN workout_exercises we ON we.id = ws.workout_exercise_id
        JOIN workouts w ON w.id = we.workout_id
        JOIN exercises e ON e.id = we.exercise_id
        WHERE w.status = 'COMPLETED'
        ORDER BY w.started_at ASC, we.order_index ASC, ws.order_index ASC
        """,
    )
    suspend fun getWorkoutCsvRows(): List<WorkoutCsvRow>

    /**
     * Ordinal workout count for the summary's "Workout #47" line — COMPLETED only, counting this
     * one. Chronological rank by `started_at`, so a backdated session takes the number it would
     * have had at the time. Exact `started_at` ties break on `id` so the ordinal is a total order:
     * a plain `<=` counted both sides of a tie and handed the same number to two workouts.
     */
    @Query(
        """
        SELECT COUNT(*) FROM workouts
        WHERE status = 'COMPLETED'
          AND (started_at < :startedAt OR (started_at = :startedAt AND id <= :workoutId))
        """,
    )
    suspend fun countCompletedWorkoutsUpTo(startedAt: Long, workoutId: String): Int
}

/**
 * The fields every StatSet-projecting DAO row below shares, so [WorkoutRepositoryImpl]'s
 * `toStatSet()` mapping exists exactly once (2026-09-19 debt audit — these three row types
 * previously carried three byte-identical copies of that mapping, one per receiver type). Room's
 * column-to-constructor-parameter binding for a `@Query` result only inspects the primary
 * constructor, so implementing this interface has no effect on how these rows are populated.
 */
interface StatSetRowFields {
    val setId: String
    val workoutId: String
    val workoutStartedAt: Long
    val routineId: String?
    val orderIndex: Int
    val setType: com.enil.logez.core.domain.model.SetType
    val weightKg: Double?
    val reps: Int?
    val durationSeconds: Int?
    val distanceMeters: Double?
    val customMetric: Double?
    val isCompleted: Boolean
    val rpe: Double?
}

/** Flat projection backing [WorkoutDao.getSetsWithExerciseForWorkout] — carries the exercise and its block identity, both of which `StatSet` deliberately omits. */
data class WorkoutSetWithExerciseRow(
    override val setId: String,
    val exerciseId: String,
    val workoutExerciseId: String,
    val exerciseOrderIndex: Int,
    override val workoutId: String,
    override val workoutStartedAt: Long,
    override val routineId: String?,
    override val orderIndex: Int,
    override val setType: com.enil.logez.core.domain.model.SetType,
    override val weightKg: Double?,
    override val reps: Int?,
    override val durationSeconds: Int?,
    override val distanceMeters: Double?,
    override val customMetric: Double?,
    override val isCompleted: Boolean,
    override val rpe: Double?,
) : StatSetRowFields

/** Flat projection backing [WorkoutDao.getStatRowsForExercise] — mapped to `StatSet` or `ExerciseHistoryEntry`. */
data class ExerciseStatRow(
    override val setId: String,
    override val workoutId: String,
    val workoutTitle: String,
    override val workoutStartedAt: Long,
    override val routineId: String?,
    override val orderIndex: Int,
    override val setType: com.enil.logez.core.domain.model.SetType,
    override val weightKg: Double?,
    override val reps: Int?,
    override val durationSeconds: Int?,
    override val distanceMeters: Double?,
    override val customMetric: Double?,
    override val isCompleted: Boolean,
    override val rpe: Double?,
) : StatSetRowFields

/** Flat projection backing [WorkoutDao.getMostRecentUsagePerExercise]. */
data class ExerciseRecencyRow(
    val exerciseId: String,
    val lastCompletedAt: Long?,
)

/** Flat projection backing [WorkoutDao.getStatRowsForExercises] — same as ExerciseStatRow but with exerciseId. */
data class ExerciseStatRowWithExerciseId(
    override val setId: String,
    override val workoutId: String,
    val workoutTitle: String,
    override val workoutStartedAt: Long,
    override val routineId: String?,
    override val orderIndex: Int,
    override val setType: com.enil.logez.core.domain.model.SetType,
    override val weightKg: Double?,
    override val reps: Int?,
    override val durationSeconds: Int?,
    override val distanceMeters: Double?,
    override val customMetric: Double?,
    override val isCompleted: Boolean,
    override val rpe: Double?,
    @androidx.room.ColumnInfo(name = "exerciseId") val exerciseId: String,
) : StatSetRowFields

/** Flat projection backing [WorkoutDao.getAllSetsForWorkout]. */
data class WorkoutSetWithExerciseIdRow(
    val id: String,
    @androidx.room.ColumnInfo(name = "workout_exercise_id") val workoutExerciseId: String,
    @androidx.room.ColumnInfo(name = "order_index") val orderIndex: Int,
    @androidx.room.ColumnInfo(name = "set_type") val setType: com.enil.logez.core.domain.model.SetType,
    @androidx.room.ColumnInfo(name = "weight_kg") val weightKg: Double?,
    val reps: Int?,
    @androidx.room.ColumnInfo(name = "duration_seconds") val durationSeconds: Int?,
    @androidx.room.ColumnInfo(name = "distance_meters") val distanceMeters: Double?,
    @androidx.room.ColumnInfo(name = "custom_metric") val customMetric: Double?,
    @androidx.room.ColumnInfo(name = "is_completed") val isCompleted: Boolean,
    @androidx.room.ColumnInfo(name = "completed_at") val completedAt: Long?,
    val rpe: Double?,
    @androidx.room.ColumnInfo(name = "exerciseId") val exerciseId: String,
)
