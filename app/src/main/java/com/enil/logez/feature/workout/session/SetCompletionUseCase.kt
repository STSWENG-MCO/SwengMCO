package com.enil.logez.feature.workout.session

import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * PHASE2_PLAN.md §5.1.3 Check-tap steps 3-5 (mark completed, resolve + start the rest timer
 * unless the next set is a DROPSET) — extracted so both [com.enil.logez.feature.workout.WorkoutLoggerViewModel]
 * (in-app tap) and `WorkoutSessionService` (notification "Complete set" action, which must work
 * even with no ViewModel alive — §9.3 lock-screen actions) share one implementation rather than
 * the Service reimplementing the dropset-exception/rest-timer-resolution rule on its own.
 */
@Singleton
class SetCompletionUseCase @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    private val sessionController: WorkoutSessionController,
    private val clock: Clock,
) {
    /**
     * Returns false if rejected (a FAILURE set with no/zero reps — §5.1.3 step 2), true once
     * completed and any rest timer started. `workoutId` is taken explicitly rather than read from
     * [WorkoutSessionController.state] — every caller already knows its own workoutId (the
     * ViewModel from its nav arg, the Service from the notification action's intent extra), and
     * reading it indirectly off session state would silently degrade to the Default Rest Timer
     * setting instead of the exercise's own override whenever the controller hadn't been told
     * about this session yet (e.g. a stray call before `startSession`/`rehydrate`).
     */
    suspend fun completeSet(workoutId: String, workoutExerciseId: String, setId: String): Boolean {
        val allSets = workoutRepository.getSetsForWorkoutExercise(workoutExerciseId).sortedBy { it.orderIndex }
        val set = allSets.find { it.id == setId } ?: return false
        if (set.setType == SetType.FAILURE && (set.reps == null || set.reps == 0)) return false

        workoutRepository.updateWorkoutSetCompletion(setId, true, clock.now().toEpochMilliseconds())

        val nextSet = allSets.getOrNull(allSets.indexOf(set) + 1)
        if (nextSet?.setType != SetType.DROPSET) {
            val workoutExercise = workoutRepository.getExercisesForWorkout(workoutId).find { it.id == workoutExerciseId }
            val defaultSeconds = settingsRepository.settings.first().defaultRestTimerSeconds
            sessionController.startRestTimer(workoutExerciseId, workoutExercise?.restTimerSeconds ?: defaultSeconds)
        }
        return true
    }
}
