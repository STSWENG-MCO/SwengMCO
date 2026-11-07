package com.enil.logez.feature.workout.finish

import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.domain.calc.PrRebuilder
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.MeasurementRepository
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * PHASE2_PLAN.md §8.4's derived-cache rule, wired at M4c: `personal_records` is rebuilt wholesale
 * for every exercise a saved workout touched — never incrementally patched. Orchestration lives
 * here rather than in [PersonalRecordsRepository] (which stays a thin DAO passthrough) because a
 * rebuild needs collaborators a repository shouldn't hold: exercise metadata, bodyweight, and
 * the warm-up-inclusion setting.
 *
 * Must run *after* the workout is COMPLETED — the stat-set query filters on that status, so a
 * rebuild triggered any earlier would silently omit the very session that prompted it.
 */
@Singleton
class PersonalRecordsUpdater @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val personalRecordsRepository: PersonalRecordsRepository,
    private val measurementRepository: MeasurementRepository,
    private val settingsRepository: SettingsRepository,
) {
    /**
     * Reads the one setting a rebuild depends on. Separate from [rebuildForExercises] because that
     * runs inside the save's database transaction, and DataStore is not part of it — resolving
     * settings there would hold the transaction open across unrelated I/O.
     */
    suspend fun includeWarmupsInStats(): Boolean = settingsRepository.settings.first().includeWarmupsInStats

    /** Rebuilds every exercise in [exerciseIds]; returns the PR rows that name [workoutId], i.e. this session's medals. */
    suspend fun rebuildForExercises(
        exerciseIds: Set<String>,
        workoutId: String,
        includeWarmupsInStats: Boolean,
    ): List<PersonalRecordEntity> {
        // One measurement lookup per distinct calendar date, reused across exercises: a rebuild
        // spans an exercise's whole history, and most users' workouts cluster on few dates.
        val bodyweightByDate = mutableMapOf<String, Double?>()

        exerciseIds.forEach { exerciseId ->
            val exercise = exerciseRepository.getById(exerciseId) ?: return@forEach
            val history = workoutRepository.getStatSetsForExercise(exerciseId)
            // Bodyweight only ever changes a volume figure for eligible exercises (§8.3), so
            // everything else skips the lookups entirely and reads null.
            val bodyweightByWorkoutId: Map<String, Double?> =
                if (!exercise.isBodyweightVolumeEligible) {
                    emptyMap()
                } else {
                    history.associate { it.workoutId to it.workoutStartedAt }
                        .mapValues { (_, startedAt) ->
                            val date = localDateOf(startedAt)
                            if (bodyweightByDate.containsKey(date)) {
                                bodyweightByDate[date]
                            } else {
                                measurementRepository.getLatestWeightKgOnOrBefore(date)
                                    .also { bodyweightByDate[date] = it }
                            }
                        }
                }
            val records = PrRebuilder.rebuild(
                type = exercise.exerciseType,
                sets = history,
                eligible = exercise.isBodyweightVolumeEligible,
                bodyweightByWorkoutId = bodyweightByWorkoutId,
                includeWarmupsInStats = includeWarmupsInStats,
            ).map { pr ->
                PersonalRecordEntity(
                    id = UUID.randomUUID().toString(),
                    exerciseId = exerciseId,
                    workoutId = pr.workoutId,
                    workoutSetId = pr.workoutSetId,
                    prType = pr.prType,
                    value = pr.value,
                    achievedAt = pr.achievedAt,
                )
            }
            personalRecordsRepository.rebuildFor(exerciseId, records)
        }

        return personalRecordsRepository.getForWorkout(workoutId)
    }

    /** A workout's local calendar date in the DAO's `yyyy-MM-dd` form — §8.1's bodyweight lookup key. */
    private fun localDateOf(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
}
