package com.enil.logez.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.enil.logez.core.di.ActiveSessionDataStore
import com.enil.logez.core.domain.model.ActiveSessionSnapshot
import com.enil.logez.core.domain.repository.ActiveSessionRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** DataStore-backed, deliberately separate from [SettingsRepositoryImpl]'s file (§9.5 — its own store, not Room). */
class ActiveSessionRepositoryImpl @Inject constructor(
    @ActiveSessionDataStore private val dataStore: DataStore<Preferences>,
) : ActiveSessionRepository {
    private object Keys {
        val WORKOUT_ID = stringPreferencesKey("workoutId")
        val IS_PAUSED = booleanPreferencesKey("isPaused")
        val ACCUMULATED_ACTIVE_SECONDS = longPreferencesKey("accumulatedActiveSeconds")
        val LAST_RESUMED_AT_MILLIS = longPreferencesKey("lastResumedAtMillis")
        val IS_EMPTY_WORKOUT_TIMER_MODE = booleanPreferencesKey("isEmptyWorkoutTimerMode")
        val REST_DEADLINE_ELAPSED_REALTIME_MILLIS = longPreferencesKey("restDeadlineElapsedRealtimeMillis")
        val REST_EXERCISE_ID = stringPreferencesKey("restExerciseId")
    }

    override suspend fun getSnapshot(): ActiveSessionSnapshot {
        val prefs = dataStore.data.first()
        return ActiveSessionSnapshot(
            workoutId = prefs[Keys.WORKOUT_ID],
            isPaused = prefs[Keys.IS_PAUSED] ?: false,
            accumulatedActiveSeconds = prefs[Keys.ACCUMULATED_ACTIVE_SECONDS] ?: 0L,
            lastResumedAtMillis = prefs[Keys.LAST_RESUMED_AT_MILLIS],
            isEmptyWorkoutTimerMode = prefs[Keys.IS_EMPTY_WORKOUT_TIMER_MODE] ?: false,
            restDeadlineElapsedRealtimeMillis = prefs[Keys.REST_DEADLINE_ELAPSED_REALTIME_MILLIS],
            restExerciseId = prefs[Keys.REST_EXERCISE_ID],
        )
    }

    override suspend fun startSession(workoutId: String, lastResumedAtMillis: Long?, isEmptyWorkoutTimerMode: Boolean) {
        dataStore.edit { prefs ->
            prefs.clear()
            prefs[Keys.WORKOUT_ID] = workoutId
            prefs[Keys.IS_PAUSED] = lastResumedAtMillis == null
            prefs[Keys.ACCUMULATED_ACTIVE_SECONDS] = 0L
            prefs[Keys.IS_EMPTY_WORKOUT_TIMER_MODE] = isEmptyWorkoutTimerMode
            if (lastResumedAtMillis != null) prefs[Keys.LAST_RESUMED_AT_MILLIS] = lastResumedAtMillis
        }
    }

    override suspend fun clearSession() {
        dataStore.edit { it.clear() }
    }

    override suspend fun updateDurationBookkeeping(isPaused: Boolean, accumulatedActiveSeconds: Long, lastResumedAtMillis: Long?) {
        dataStore.edit { prefs ->
            prefs[Keys.IS_PAUSED] = isPaused
            prefs[Keys.ACCUMULATED_ACTIVE_SECONDS] = accumulatedActiveSeconds
            if (lastResumedAtMillis != null) prefs[Keys.LAST_RESUMED_AT_MILLIS] = lastResumedAtMillis else prefs.remove(Keys.LAST_RESUMED_AT_MILLIS)
        }
    }

    override suspend fun updateRestTimer(deadlineElapsedRealtimeMillis: Long?, exerciseId: String?) {
        dataStore.edit { prefs ->
            if (deadlineElapsedRealtimeMillis != null) prefs[Keys.REST_DEADLINE_ELAPSED_REALTIME_MILLIS] = deadlineElapsedRealtimeMillis else prefs.remove(Keys.REST_DEADLINE_ELAPSED_REALTIME_MILLIS)
            if (exerciseId != null) prefs[Keys.REST_EXERCISE_ID] = exerciseId else prefs.remove(Keys.REST_EXERCISE_ID)
        }
    }
}
