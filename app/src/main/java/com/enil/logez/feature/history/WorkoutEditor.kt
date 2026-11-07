package com.enil.logez.feature.history

import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.WidgetRefresher
import com.enil.logez.core.domain.repository.TransactionRunner
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE2_PLAN.md §5.1.10's save transaction for an edited past workout:
 * "update in place — `status` stays `COMPLETED`, `startedAt`/`durationSeconds`/`endedAt` per the
 * edited fields, uncompleted rows dropped after a warning — then rebuild `personal_records` for
 * the union of exercises present before and after the edit."
 *
 * The union is the load-bearing part. Rebuilding only the exercises still present would leave a
 * record standing for an exercise the edit *removed*: its sets are gone, but `personal_records`
 * would keep pointing at them until something else happened to touch that exercise.
 *
 * Structure is written by delete-and-reinsert rather than a per-row diff. The editor can add,
 * remove, reorder, replace and re-superset in one sitting, so a diff would have to reconcile all
 * of that against the live rows; replacing the workout's children wholesale is one obvious
 * operation whose outcome is exactly what the user sees on screen. It is safe here in a way it
 * would not be mid-session because it happens inside the same transaction as everything else.
 */
@Singleton
class WorkoutEditor @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val personalRecordsUpdater: PersonalRecordsUpdater,
    private val transactionRunner: TransactionRunner,
    private val clock: Clock,
    private val widgetRefresher: WidgetRefresher = WidgetRefresher.NoOp,
) {
    suspend fun save(
        workout: WorkoutEntity,
        startedAt: Long,
        durationSeconds: Int,
        exercises: List<WorkoutExerciseEntity>,
        sets: List<WorkoutSetEntity>,
    ) {
        val now = clock.now().toEpochMilliseconds()
        // Same clamp the finish flow applies (§5.1.8): a workout cannot start in the future.
        val effectiveStartedAt = minOf(startedAt, now)
        // DataStore, not Room — resolved before the transaction opens so it isn't held across
        // unrelated I/O (the care WorkoutFinisher documents).
        val includeWarmups = personalRecordsUpdater.includeWarmupsInStats()

        transactionRunner.runInTransaction {
            val exerciseIdsBefore = workoutRepository.getSetsWithExerciseForWorkout(workout.id)
                .map { it.exerciseId }
                .toSet()

            // §5.1.10: "uncompleted rows dropped after a warning" — the warning is the screen's
            // job; dropping them is this transaction's. Exercises left with no sets go too, so an
            // edit can't leave an empty block behind (matching the finish flow's own purge).
            val keptSets = sets.filter { it.isCompleted }
            val keptSetsByExercise = keptSets.groupBy { it.workoutExerciseId }
            val keptExercises = exercises
                .filter { keptSetsByExercise.containsKey(it.id) }
                .sortedBy { it.orderIndex }
                .mapIndexed { index, we -> we.copy(orderIndex = index) }
            val keptExerciseIds = keptExercises.map { it.id }.toSet()

            workoutRepository.replaceWorkoutStructure(
                workout = workout.copy(
                    status = WorkoutStatus.COMPLETED,
                    startedAt = effectiveStartedAt,
                    endedAt = effectiveStartedAt + durationSeconds * 1000L,
                    durationSeconds = durationSeconds,
                    updatedAt = now,
                ),
                exercises = dropOrphanSupersets(keptExercises),
                sets = keptSets
                    .filter { it.workoutExerciseId in keptExerciseIds }
                    .groupBy { it.workoutExerciseId }
                    .flatMap { (_, group) -> group.sortedBy { it.orderIndex }.mapIndexed { index, s -> s.copy(orderIndex = index) } },
            )

            val exerciseIdsAfter = keptExercises.map { it.exerciseId }.toSet()
            val touched = exerciseIdsBefore + exerciseIdsAfter
            if (touched.isNotEmpty()) {
                personalRecordsUpdater.rebuildForExercises(touched, workout.id, includeWarmups)
            }
        }
        // A backdate can move a workout into or out of the current week, and extend or break the
        // streak, so an edit matters to the widget as much as a finish does.
        widgetRefresher.refresh()
    }

    /**
     * Dropping a superset partner (or having its sets purged) can leave a group with one member,
     * which is not a valid state anywhere in the app — the logger and routine builder both clean
     * it up, and the finish flow's structural rewrite does the same.
     */
    private fun dropOrphanSupersets(exercises: List<WorkoutExerciseEntity>): List<WorkoutExerciseEntity> {
        val memberCount = exercises.mapNotNull { it.supersetGroup }.groupingBy { it }.eachCount()
        return exercises.map {
            if (it.supersetGroup != null && (memberCount[it.supersetGroup] ?: 0) < 2) it.copy(supersetGroup = null) else it
        }
    }
}
