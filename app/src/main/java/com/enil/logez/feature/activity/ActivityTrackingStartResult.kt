package com.enil.logez.feature.activity

/** M21a. Kept separate from `com.enil.logez.feature.workout.StartResult` — that type's `Started`
 * carries only a workoutId and is asserted against by existing tests; a tracked run also needs the
 * created set's id to hand to the tracking service/controller. */
sealed class ActivityTrackingStartResult {
    data class Started(val workoutId: String, val workoutSetId: String) : ActivityTrackingStartResult()
    data class AlreadyInProgress(val workoutId: String) : ActivityTrackingStartResult()
}
