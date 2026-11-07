package com.enil.logez.feature.workout

import com.enil.logez.feature.activity.ActivityTrackingController
import com.enil.logez.feature.workout.session.WorkoutSessionController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Throws the in-progress session away, whatever kind it is.
 *
 * Five screens used to do this by hand, and none of them cancelled a live GPS session — so
 * discarding a run from a resume dialog deleted its row while the location collector and its
 * foreground notification kept going against nothing, until reboot.
 *
 * Tracking is cancelled first: the collector references the set row that is about to be deleted.
 * The tracking service watches the controller and stops itself once the session is gone, so no
 * caller needs a Context to reach the service.
 */
@Singleton
class SessionDiscarder @Inject constructor(
    private val workoutStarter: WorkoutStarter,
    private val sessionController: WorkoutSessionController,
    private val activityTrackingController: ActivityTrackingController,
) {
    suspend fun discardInProgress() {
        activityTrackingController.cancelTracking()
        workoutStarter.discardInProgress()
        sessionController.endSession()
    }
}
