package com.enil.logez.feature.workout.finish

import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.calc.PrCalculator
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.calc.isIncluded
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.MeasurementRepository
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PHASE2_PLAN.md §8.4 point 1 — the live banner check that runs on each set completion. Compares
 * the just-completed set's candidates against `max(cached bests, earlier sets this session)`.
 *
 * Only set-scoped PrTypes can fire live: session-scoped ones (BEST_SESSION_VOLUME,
 * MOST_SESSION_REPS) aren't final until the session ends, so [PrCalculator.liveBanners] excludes
 * them and they surface as medals on the summary instead.
 */
@Singleton
class LivePrDetector @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val exerciseRepository: ExerciseRepository,
    private val personalRecordsRepository: PersonalRecordsRepository,
    private val measurementRepository: MeasurementRepository,
    private val clock: Clock,
) {
    suspend fun detect(
        workoutId: String,
        workoutExerciseId: String,
        setId: String,
        includeWarmupsInStats: Boolean,
    ): List<PrType> {
        val workoutExercise = workoutRepository.getExercisesForWorkout(workoutId)
            .find { it.id == workoutExerciseId } ?: return emptyList()
        val exercise = exerciseRepository.getById(workoutExercise.exerciseId) ?: return emptyList()

        val sessionSets = workoutRepository.getSetsWithExerciseForWorkout(workoutId)
            .filter { it.exerciseId == workoutExercise.exerciseId }
        val newSet = sessionSets.find { it.set.setId == setId }?.set ?: return emptyList()

        val cachedBests = personalRecordsRepository.getForExercise(workoutExercise.exerciseId)
            .associate { it.prType to it.value }

        // "Never on an exercise's first-ever log" (§8.4): with no cached records and no prior
        // history, everything would trivially be a PR and every first set would flash a banner.
        // "History" here means *included* sets, not raw rows: the stat query filters on COMPLETED
        // status only, so an exercise logged once as a warm-up looks non-empty while the rebuild
        // correctly stored zero records for it — leaving every priorBest at -inf and firing a
        // banner on what is really the first working set.
        val history = workoutRepository.getStatSetsForExercise(workoutExercise.exerciseId)
        val isFirstEverLog = history.none { isIncluded(it, includeWarmupsInStats) }
        if (cachedBests.isEmpty() && isFirstEverLog) return emptyList()

        val bodyweightKg = latestBodyweightKg()

        // Earlier completed sets of this same exercise in this same session — the set just logged
        // must beat those too, so a third set doesn't re-announce a PR the second set already set.
        val earlierSessionBests = mutableMapOf<PrType, Double>()
        sessionSets
            .map { it.set }
            .filter { it.setId != setId && isIncluded(it, includeWarmupsInStats) }
            .forEach { earlier ->
                PrCalculator.setCandidates(exercise.exerciseType, earlier, exercise.isBodyweightVolumeEligible, bodyweightKg)
                    .forEach { (prType, value) ->
                        earlierSessionBests[prType] = maxOf(earlierSessionBests[prType] ?: Double.NEGATIVE_INFINITY, value)
                    }
            }

        return PrCalculator.liveBanners(
            type = exercise.exerciseType,
            newSet = newSet,
            eligible = exercise.isBodyweightVolumeEligible,
            bodyweightKg = bodyweightKg,
            cachedBests = cachedBests,
            earlierSessionBests = earlierSessionBests,
            isFirstEverLog = isFirstEverLog,
            includeWarmupsInStats = includeWarmupsInStats,
        )
    }

    private suspend fun latestBodyweightKg(): Double? {
        val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds())
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
        return measurementRepository.getLatestWeightKgOnOrBefore(today)
    }
}
