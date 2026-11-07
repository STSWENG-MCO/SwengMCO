package com.enil.logez.feature.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.wellness.HealthMetricsSource
import com.enil.logez.core.wellness.HeartRateSample
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.wellness.HeartRateAccess
import com.enil.logez.core.wellness.heartRateAccessFlow
import com.enil.logez.core.wellness.liveHeartRateHistoryFlow
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.session.WorkoutSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * M21a. A thin wrapper around the shared [ActivityTrackingController] singleton — tracking itself
 * (both this controller and [WorkoutSessionController]'s shared elapsed-time state) was already
 * started in `WorkoutTabViewModel` before navigating here.
 */
@HiltViewModel
class ActivityTrackingViewModel @Inject constructor(
    private val controller: ActivityTrackingController,
    private val sessionDiscarder: SessionDiscarder,
    private val sessionController: WorkoutSessionController,
    private val settingsRepository: SettingsRepository,
    val healthMetricsSource: HealthMetricsSource,
    private val clock: Clock,
    private val logger: AppLogger,
) : ViewModel() {
    val state: StateFlow<ActivityTrackingState> = controller.state
    val elapsedSecondsFlow: Flow<Int> = controller.elapsedSecondsFlow

    /**
     * Whether heart rate can be read, so the Vitals card can say "not allowed" rather than "no
     * data" (2026-09-26). The BPM itself is now the newest sample in [heartRateHistoryFlow]: a
     * separate "last 5 minutes" poll went blank whenever the watch's sync ran late, which with
     * Samsung Health is most of the time, and cost a second Health Connect read every tick.
     */
    val heartRateAccessFlow: Flow<HeartRateAccess> = heartRateAccessFlow(healthMetricsSource)

    /** The user answered the in-card permission request; reads may resume after a disconnect. */
    fun onHeartRatePermissionResult(anyGranted: Boolean) {
        if (anyGranted) healthMetricsSource.onPermissionsRegranted()
    }

    /**
     * The vitals card's heart-rate chart. A plain function, not a property initialized once at
     * construction time, because [ActivityTrackingState.startedAtMillis] isn't known until
     * tracking has actually started -- the caller supplies it once `state.startedAtMillis` is
     * non-null, keyed via `remember(startedAtMillis)` so a fresh flow (and fresh poll) starts only
     * if a genuinely new session's start time ever appears.
     */
    fun heartRateHistoryFlow(startedAtMillis: Long): Flow<List<HeartRateSample>> =
        liveHeartRateHistoryFlow(healthMetricsSource, startedAtMillis, clock, logger = logger)

    /** Distance unit (for pace) and max heart rate (for the live zone) -- the two settings the
     * screen's pace/zone display needs. */
    val settings: StateFlow<UserSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    /**
     * M21 redesign (2026-09-11): Finish now goes straight to the Save Workout screen instead of
     * handing off into the strength Logger (which used to be the thing that later called
     * `sessionController.endSession()` itself, in `WorkoutLoggerViewModel.prepareForFinish()`).
     * Ending it here instead, mirroring [cancel]'s existing call -- otherwise the mini-bar's
     * "in-progress, tap to resume" state would keep pointing at a workoutId that's about to become
     * COMPLETED, since nothing else on the new direct path ever clears it.
     */
    suspend fun finish(): FinishedTrack? {
        val result = controller.finishTracking()
        sessionController.endSession()
        return result
    }

    suspend fun cancel() = sessionDiscarder.discardInProgress()
}
