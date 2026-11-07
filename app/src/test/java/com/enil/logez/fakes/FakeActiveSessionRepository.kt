package com.enil.logez.fakes

import com.enil.logez.core.domain.model.ActiveSessionSnapshot
import com.enil.logez.core.domain.repository.ActiveSessionRepository

/** In-memory fake (PHASE2_PLAN.md §10.1 rule 2) — mirrors the real DataStore-backed store's persist/clear/read semantics. */
class FakeActiveSessionRepository(initial: ActiveSessionSnapshot = ActiveSessionSnapshot()) : ActiveSessionRepository {
    var snapshot: ActiveSessionSnapshot = initial
        private set

    override suspend fun getSnapshot(): ActiveSessionSnapshot = snapshot

    override suspend fun startSession(workoutId: String, lastResumedAtMillis: Long?, isEmptyWorkoutTimerMode: Boolean) {
        snapshot = ActiveSessionSnapshot(
            workoutId = workoutId,
            isPaused = lastResumedAtMillis == null,
            lastResumedAtMillis = lastResumedAtMillis,
            isEmptyWorkoutTimerMode = isEmptyWorkoutTimerMode,
        )
    }

    override suspend fun clearSession() {
        snapshot = ActiveSessionSnapshot()
    }

    override suspend fun updateDurationBookkeeping(isPaused: Boolean, accumulatedActiveSeconds: Long, lastResumedAtMillis: Long?) {
        snapshot = snapshot.copy(isPaused = isPaused, accumulatedActiveSeconds = accumulatedActiveSeconds, lastResumedAtMillis = lastResumedAtMillis)
    }

    override suspend fun updateRestTimer(deadlineElapsedRealtimeMillis: Long?, exerciseId: String?) {
        snapshot = snapshot.copy(restDeadlineElapsedRealtimeMillis = deadlineElapsedRealtimeMillis, restExerciseId = exerciseId)
    }
}
