package com.enil.logez.feature.workout.session

import com.enil.logez.core.common.Clock
import com.enil.logez.core.common.ElapsedRealtimeClock
import com.enil.logez.core.domain.repository.ActiveSessionRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WorkoutNotificationContent(
    val title: String,
    val text: String,
    val isResting: Boolean,
    /** §9.3 "Complete set" action target — the exercise/set the notification's primary action would complete. */
    val actionableExerciseId: String? = null,
    val actionableSetId: String? = null,
)

data class InlineTimerState(val exerciseId: String, val setId: String, val startElapsedRealtimeMillis: Long)

data class WorkoutSessionState(
    val workoutId: String? = null,
    val isPaused: Boolean = false,
    val accumulatedActiveSeconds: Long = 0L,
    val lastResumedAtMillis: Long? = null,
    val isEmptyWorkoutTimerMode: Boolean = false,
    val restDeadlineElapsedRealtimeMillis: Long? = null,
    val restExerciseId: String? = null,
    val inlineTimer: InlineTimerState? = null,
    val notificationContent: WorkoutNotificationContent? = null,
) {
    val hasActiveSession: Boolean get() = workoutId != null
}

/**
 * PHASE2_PLAN.md §9.2 — single source of truth for the live workout's fast-tick timer state,
 * shared (Hilt `@Singleton`, not a Service binder — Service and ViewModel already live in the
 * same process) between [com.enil.logez.feature.workout.WorkoutLoggerViewModel] and
 * `WorkoutSessionService`. Deliberately framework-free beyond the injected [Clock]/
 * [ElapsedRealtimeClock] abstractions, so it's directly unit-testable under virtual time
 * (§10.6). Rest-timer expiry detection and anything needing a live `PowerManager`/
 * `NotificationManager` (§9.3/§9.4) is the Service's job, not this class's — it only exposes the
 * deadline for the Service to watch.
 */
@Singleton
class WorkoutSessionController @Inject constructor(
    private val activeSessionRepository: ActiveSessionRepository,
    private val clock: Clock,
    private val elapsedRealtimeClock: ElapsedRealtimeClock,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(WorkoutSessionState())
    val state: StateFlow<WorkoutSessionState> get() = _state

    private val _restTimerFired = MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** Emits the owning exerciseId once, each time a rest timer reaches zero (fired by the Service — see class doc). */
    val restTimerFired: Flow<String> = _restTimerFired.asSharedFlow()

    private val _setCompletedExternally = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 4)
    /**
     * (workoutExerciseId, setId) each time the Service completes a set on Room's behalf (the
     * notification's "Complete set" action, which must work with no ViewModel alive — §9.3). An
     * already-open Logger screen subscribes to this to mirror the change into its own in-memory
     * write-through state instead of going stale until the next full reload.
     */
    val setCompletedExternally: Flow<Pair<String, String>> = _setCompletedExternally.asSharedFlow()
    fun notifySetCompletedExternally(workoutExerciseId: String, setId: String) {
        _setCompletedExternally.tryEmit(workoutExerciseId to setId)
    }

    private var rehydrated = false

    /** §9.5 process-death recovery: loads the persisted snapshot once. Safe to call repeatedly (no-ops after the first). */
    suspend fun rehydrate() {
        if (rehydrated) return
        rehydrated = true
        val snapshot = activeSessionRepository.getSnapshot()
        if (snapshot.workoutId != null) {
            _state.value = WorkoutSessionState(
                workoutId = snapshot.workoutId,
                isPaused = snapshot.isPaused,
                accumulatedActiveSeconds = snapshot.accumulatedActiveSeconds,
                lastResumedAtMillis = snapshot.lastResumedAtMillis,
                isEmptyWorkoutTimerMode = snapshot.isEmptyWorkoutTimerMode,
                restDeadlineElapsedRealtimeMillis = snapshot.restDeadlineElapsedRealtimeMillis,
                restExerciseId = snapshot.restExerciseId,
            )
        }
    }

    /**
     * `suspend` and awaits the persist directly (not fire-and-forget) — a process death right
     * after this returns must never leave the persisted snapshot without a workoutId while Room's
     * workout row is genuinely IN_PROGRESS: [rehydrate] only ever repairs a null in-memory state
     * from a non-null persisted one, so a lost fire-and-forget write here would have made the
     * controller believe no session was active for the rest of the process's life (breaking the
     * notification's "Complete set" action and freezing the elapsed-time display) with no way to
     * recover short of restarting the app.
     */
    suspend fun startSession(workoutId: String, waitForFirstExercise: Boolean = false) {
        val now = clock.now().toEpochMilliseconds()
        val resumedAt = now.takeUnless { waitForFirstExercise }
        _state.value = WorkoutSessionState(
            workoutId = workoutId,
            isPaused = waitForFirstExercise,
            lastResumedAtMillis = resumedAt,
            isEmptyWorkoutTimerMode = waitForFirstExercise,
        )
        activeSessionRepository.startSession(workoutId, resumedAt, waitForFirstExercise)
    }

    /** Finish/discard — clears both in-memory and persisted session state. */
    fun endSession() {
        _state.value = WorkoutSessionState()
        scope.launch { activeSessionRepository.clearSession() }
    }

    /** `suspend` for the same reason as [startSession] — a lost write here rehydrates a stale
     * pre-pause snapshot after a process death, inflating the live elapsed-time display by however
     * long the process was gone (Room's own persisted `durationSeconds`, computed independently
     * from `startedAt` at finish time, is unaffected — this only corrupts the live UI). */
    suspend fun pause() {
        val s = _state.value
        if (s.workoutId == null || s.isPaused) return
        val now = clock.now().toEpochMilliseconds()
        val accumulated = WorkoutDurationEngine.accumulateOnPause(s.accumulatedActiveSeconds, s.lastResumedAtMillis, now)
        _state.update { it.copy(isPaused = true, accumulatedActiveSeconds = accumulated, lastResumedAtMillis = null) }
        activeSessionRepository.updateDurationBookkeeping(true, accumulated, null)
    }

    suspend fun resume() {
        val s = _state.value
        if (s.workoutId == null || !s.isPaused) return
        val now = clock.now().toEpochMilliseconds()
        _state.update { it.copy(isPaused = false, lastResumedAtMillis = now) }
        activeSessionRepository.updateDurationBookkeeping(false, s.accumulatedActiveSeconds, now)
    }

    /** Restarts the visible session clock while preserving the workout and empty-workout mode. */
    suspend fun resetElapsedTime(paused: Boolean) {
        val s = _state.value
        if (s.workoutId == null) return
        val resumedAt = clock.now().toEpochMilliseconds().takeUnless { paused }
        _state.update {
            it.copy(isPaused = paused, accumulatedActiveSeconds = 0L, lastResumedAtMillis = resumedAt)
        }
        activeSessionRepository.updateDurationBookkeeping(paused, 0L, resumedAt)
    }

    fun elapsedSeconds(nowMillis: Long = clock.now().toEpochMilliseconds()): Long {
        val s = _state.value
        return WorkoutDurationEngine.elapsedSeconds(s.accumulatedActiveSeconds, s.isPaused, s.lastResumedAtMillis, nowMillis)
    }

    /** Ticks once/sec while collected (cold — no cost when nobody's watching); freezes automatically once paused. */
    val elapsedSecondsFlow: Flow<Long> = flow {
        while (true) {
            if (_state.value.workoutId != null) emit(elapsedSeconds())
            delay(1_000)
        }
    }

    // --- Rest timer (§5.1.4/§9.4) ---

    /** `durationSeconds <= 0` means "off" (§5.1.4: `restTimerSeconds = 0` -> no bar, no sound) — treated as an immediate skip. */
    fun startRestTimer(exerciseId: String, durationSeconds: Int) {
        if (durationSeconds <= 0) {
            skipRestTimer()
            return
        }
        val deadline = RestTimerEngine.startDeadline(elapsedRealtimeClock.elapsedRealtimeMillis(), durationSeconds)
        _state.update { it.copy(restDeadlineElapsedRealtimeMillis = deadline, restExerciseId = exerciseId) }
        scope.launch { activeSessionRepository.updateRestTimer(deadline, exerciseId) }
    }

    fun adjustRestTimer(deltaSeconds: Int) {
        val s = _state.value
        val deadline = s.restDeadlineElapsedRealtimeMillis ?: return
        val adjusted = RestTimerEngine.adjustDeadline(deadline, deltaSeconds, elapsedRealtimeClock.elapsedRealtimeMillis())
        if (adjusted == null) {
            skipRestTimer()
            return
        }
        _state.update { it.copy(restDeadlineElapsedRealtimeMillis = adjusted) }
        scope.launch { activeSessionRepository.updateRestTimer(adjusted, s.restExerciseId) }
    }

    fun skipRestTimer() {
        if (_state.value.restDeadlineElapsedRealtimeMillis == null) return
        _state.update { it.copy(restDeadlineElapsedRealtimeMillis = null, restExerciseId = null) }
        scope.launch { activeSessionRepository.updateRestTimer(null, null) }
    }

    /** Called by the Service when its own watcher observes the deadline has passed — clears state and fires the alert exactly once. */
    fun markRestTimerFired() {
        val exerciseId = _state.value.restExerciseId ?: return
        _state.update { it.copy(restDeadlineElapsedRealtimeMillis = null, restExerciseId = null) }
        scope.launch { activeSessionRepository.updateRestTimer(null, null) }
        _restTimerFired.tryEmit(exerciseId)
    }

    /** Ticks once/sec while a rest timer is active; emits null (and stops ticking meaningfully) once it clears. */
    val restRemainingMillisFlow: Flow<Long?> = flow {
        while (true) {
            val deadline = _state.value.restDeadlineElapsedRealtimeMillis
            emit(if (deadline == null) null else RestTimerEngine.remainingMillis(deadline, elapsedRealtimeClock.elapsedRealtimeMillis()))
            delay(1_000)
        }
    }

    // --- Inline timer (§5.1.3: DURATION-family TIME-cell stopwatch, one at a time, in-memory only — not process-death-critical) ---

    fun startInlineTimer(exerciseId: String, setId: String) {
        _state.update { it.copy(inlineTimer = InlineTimerState(exerciseId, setId, elapsedRealtimeClock.elapsedRealtimeMillis())) }
    }

    /** Returns elapsed seconds to commit into `durationSeconds`, or null if no inline timer was running for this exact set. */
    fun stopInlineTimer(exerciseId: String, setId: String): Int? {
        val running = _state.value.inlineTimer ?: return null
        if (running.exerciseId != exerciseId || running.setId != setId) return null
        val elapsedMs = elapsedRealtimeClock.elapsedRealtimeMillis() - running.startElapsedRealtimeMillis
        _state.update { it.copy(inlineTimer = null) }
        return (elapsedMs / 1000).toInt().coerceAtLeast(0)
    }

    /** Ticks once/sec while an inline timer is running, for the TIME cell's live stopwatch display. */
    val inlineTimerSecondsFlow: Flow<Int?> = flow {
        while (true) {
            val running = _state.value.inlineTimer
            emit(running?.let { ((elapsedRealtimeClock.elapsedRealtimeMillis() - it.startElapsedRealtimeMillis) / 1000).toInt().coerceAtLeast(0) })
            delay(1_000)
        }
    }

    /** §9.3 — pushed by the ViewModel whenever the displayable "current exercise" summary changes; read by the Service to build the notification. */
    fun updateNotificationContent(content: WorkoutNotificationContent?) {
        _state.update { it.copy(notificationContent = content) }
    }
}
