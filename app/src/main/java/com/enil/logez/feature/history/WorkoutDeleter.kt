package com.enil.logez.feature.history

import com.enil.logez.core.domain.WidgetRefresher
import com.enil.logez.core.domain.repository.TransactionRunner
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE2_PLAN.md §5.2 Workout Detail "Delete Workout": "hard delete cascades `workout_exercises`/
 * `workout_sets`, then PR rebuild for every exercise that appeared in it."
 *
 * The cascade (Room FK `onDelete = CASCADE`) already removes the deleted workout's own
 * `personal_records` rows for free, but that alone leaves any record it *held* stale: if the
 * deleted session was an exercise's best set, the next-best historical session never becomes the
 * new winner until something re-runs the rebuild. So exercise ids must be captured before the
 * delete (after it, their sets are gone) and rebuilt after (so the rebuild's own COMPLETED-only
 * query no longer sees the deleted rows) — same shape as [PersonalRecordsUpdater]'s other callers,
 * wrapped in one [TransactionRunner] transaction so a crash mid-way can't leave a workout gone but
 * its records stale forever.
 */
@Singleton
class WorkoutDeleter @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val personalRecordsUpdater: PersonalRecordsUpdater,
    private val transactionRunner: TransactionRunner,
    private val widgetRefresher: WidgetRefresher = WidgetRefresher.NoOp,
) {
    suspend fun delete(workoutId: String) {
        // DataStore, not Room — resolved before the transaction opens so it doesn't hold Room's
        // transaction open across unrelated I/O (same care WorkoutFinisher takes).
        val includeWarmups = personalRecordsUpdater.includeWarmupsInStats()

        transactionRunner.runInTransaction {
            val touchedExerciseIds = workoutRepository.getSetsWithExerciseForWorkout(workoutId)
                .map { it.exerciseId }
                .toSet()
            workoutRepository.deleteById(workoutId)
            if (touchedExerciseIds.isNotEmpty()) {
                personalRecordsUpdater.rebuildForExercises(touchedExerciseIds, workoutId, includeWarmups)
            }
        }
        widgetRefresher.refresh()
    }
}
