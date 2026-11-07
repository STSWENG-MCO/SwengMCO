package com.enil.logez.feature.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.activity.ActivityTrackingController
import com.enil.logez.feature.workout.session.WorkoutSessionController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Backs the mini-bar docked above the bottom tab bar, showing a summary of an in-progress workout
 * while the user is elsewhere in the app. Lives at the [com.enil.logez.LogEzApp] root
 * (Activity-scoped, outside the Logger's own nav back-stack entry) so it survives navigating away
 * from the Logger, which pops that destination off the stack (destroying its ViewModel); Room and
 * the controller-backed session state are the real source of truth either way. Elapsed/rest
 * countdown are exposed as separate `Flow`s, not folded into [uiState] — see
 * [WorkoutLoggerViewModel]'s equivalent doc comment: folding a per-second self-ticking flow into an
 * eagerly-shared StateFlow would start it the instant this Activity-scoped ViewModel is
 * constructed (i.e. for the app's entire foreground lifetime) and recompose the whole bar every
 * second instead of just its digits.
 */
@HiltViewModel
class MiniBarViewModel @Inject constructor(
    workoutRepository: WorkoutRepository,
    activityTrackingController: ActivityTrackingController,
    private val sessionController: WorkoutSessionController,
) : ViewModel() {
    init {
        // §9.5 cold-start recovery safety net: if nothing else has rehydrated the controller yet
        // (the Logger/Service may not have run this app process), the elapsed/rest flows would
        // otherwise silently show nothing for an IN_PROGRESS workout.
        viewModelScope.launch { sessionController.rehydrate() }
    }

    val elapsedSecondsFlow: Flow<Long> = sessionController.elapsedSecondsFlow
    val restRemainingMillisFlow: Flow<Long?> = sessionController.restRemainingMillisFlow

    val uiState: StateFlow<MiniBarUiState> = combine(
        workoutRepository.observeInProgress(),
        activityTrackingController.state,
    ) { workout, tracking ->
        if (workout == null) {
            MiniBarUiState()
        } else {
            MiniBarUiState(
                visible = true,
                workoutId = workout.id,
                title = workout.title,
                startedAt = workout.startedAt,
                kind = workout.kind,
                gpsSessionAlive = tracking.workoutId == workout.id,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MiniBarUiState())
}

data class MiniBarUiState(
    val visible: Boolean = false,
    val workoutId: String? = null,
    val title: String = "",
    val startedAt: Long = 0L,
    /** Persisted, so it survives a process death — unlike [gpsSessionAlive]. */
    val kind: WorkoutKind = WorkoutKind.STRENGTH,
    /** Whether tracking is still actually collecting, which only in-memory state can answer. */
    val gpsSessionAlive: Boolean = false,
)
