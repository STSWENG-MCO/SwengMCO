package com.enil.logez.feature.workout

import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.activity.ActivityTrackingController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What kind of session is in progress, and whether anything is still collecting for it.
 *
 * Every surface that offers to resume a workout needs all three answers, and each one that
 * hand-rolled them got a different subset right — sending GPS runs into the strength logger, which
 * then starts a second foreground service alongside the location one. Resolved in one place so a
 * new resume surface cannot reintroduce that.
 */
sealed interface InProgressWorkout {
    val id: String

    /** Typed session. Resumable from the ActiveSession store, so the logger is correct. */
    data class Strength(override val id: String) : InProgressWorkout

    /** Tracking is still collecting — re-enter the live screen and start nothing. */
    data class LiveGpsRun(override val id: String) : InProgressWorkout

    /**
     * The process died mid-run. Route and distance lived only in memory and are gone, so this
     * cannot be resumed at all — the user has to decide what happens to the row.
     */
    data class InterruptedGpsRun(override val id: String, val startedAt: Long) : InProgressWorkout
}

@Singleton
class InProgressWorkoutResolver @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val activityTrackingController: ActivityTrackingController,
) {
    suspend fun resolve(): InProgressWorkout? {
        val workout = workoutRepository.getInProgress() ?: return null
        return when {
            workout.kind == WorkoutKind.STRENGTH -> InProgressWorkout.Strength(workout.id)
            // The id, not just isTracking: a controller left pointing at a different,
            // already-finished workout must not be read as this row's live session.
            activityTrackingController.state.value.workoutId == workout.id ->
                InProgressWorkout.LiveGpsRun(workout.id)
            else -> InProgressWorkout.InterruptedGpsRun(workout.id, workout.startedAt)
        }
    }
}
