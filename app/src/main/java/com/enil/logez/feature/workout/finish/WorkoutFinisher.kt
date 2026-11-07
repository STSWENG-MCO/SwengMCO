package com.enil.logez.feature.workout.finish

import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.LoggedSetValues
import com.enil.logez.core.domain.calc.RoutineSetTargets
import com.enil.logez.core.domain.calc.RoutineValueUpdater
import com.enil.logez.core.domain.calc.StatSet
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.domain.repository.RoutineRepository
import com.enil.logez.core.domain.repository.TransactionRunner
import com.enil.logez.core.domain.repository.WorkoutHeartRateSampleRepository
import com.enil.logez.core.domain.WidgetRefresher
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.core.domain.repository.WorkoutSetWithExercise
import com.enil.logez.core.wellness.HealthMetricsSource
import com.enil.logez.core.wellness.WorkoutHeartRateBackfill
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.canRead
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

/** What the user chose on the §5.1.8(b) prompt — absent when the session made no structural change. */
enum class RoutineStructureChoice { UPDATE_ROUTINE, KEEP_ORIGINAL }

/** Everything the summary screen needs, computed as part of the save so it never re-derives from a moving target. */
data class FinishResult(
    val workoutId: String,
    val prs: List<PersonalRecordEntity>,
)

/**
 * PHASE2_PLAN.md §5.1.8's save transaction, in the order the plan specifies:
 * delete uncompleted sets → mark COMPLETED with final timestamps → apply routine value and/or
 * structural updates → rebuild `personal_records` for affected exercises.
 *
 * The ordering is load-bearing, not stylistic: the PR rebuild reads stat sets through a query
 * that filters `status = 'COMPLETED'`, so it must run after the status flip or it would omit the
 * very session that triggered it; and it must run after the uncompleted-set purge or abandoned
 * sets would count toward records.
 *
 * All four steps share one [TransactionRunner] transaction. Room's `@Transaction` cannot span the
 * three DAOs involved, and a partial save is unrecoverable: once the workout is COMPLETED the
 * finish screen can never be reached again, so a routine update or PR rebuild lost to a crash or
 * a cancelled coroutine would stay lost.
 */
@Singleton
class WorkoutFinisher @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val personalRecordsUpdater: PersonalRecordsUpdater,
    private val transactionRunner: TransactionRunner,
    private val healthMetricsSource: HealthMetricsSource,
    private val heartRateSampleRepository: WorkoutHeartRateSampleRepository,
    private val clock: Clock,
    private val logger: AppLogger = AppLogger.NoOp,
    private val widgetRefresher: WidgetRefresher = WidgetRefresher.NoOp,
) {
    private val heartRateBackfill = WorkoutHeartRateBackfill(healthMetricsSource, heartRateSampleRepository, logger)

    suspend fun finish(
        workout: WorkoutEntity,
        title: String,
        notes: String?,
        startedAt: Long,
        durationSeconds: Int,
        updateRoutineValues: Boolean,
        structureChoice: RoutineStructureChoice?,
    ): FinishResult {
        val now = clock.now().toEpochMilliseconds()
        // §5.1.8 edge case: "Setting date/time into the future is clamped to now."
        val effectiveStartedAt = minOf(startedAt, now)
        // Read before the transaction opens: this is DataStore, not Room, so resolving it inside
        // would pin the transaction open across unrelated I/O.
        val includeWarmups = personalRecordsUpdater.includeWarmupsInStats()

        return transactionRunner.runInTransaction {
            // Captured before the purge — every block-pairing decision below (values-only update,
            // structural rewrite) must key off this ORIGINAL block ordering. finishWorkout() below
            // deletes any workout-exercise whose sets were all uncompleted; re-deriving "the Nth
            // block of this exercise" from what survives that purge silently renumbers every later
            // same-exercise block whenever an earlier one was skipped entirely, pairing a routine
            // slot with the wrong session block (§5.1.8 regression: skipping a main lift block
            // shifted a burnout block's values onto it).
            val setsBefore = workoutRepository.getSetsWithExerciseForWorkout(workout.id)
            val occurrenceByWorkoutExerciseId = originalOccurrenceIndex(setsBefore)

            workoutRepository.finishWorkout(
                workout.copy(
                    title = title.ifBlank { workout.title },
                    notes = notes?.takeIf { it.isNotBlank() },
                    status = WorkoutStatus.COMPLETED,
                    startedAt = effectiveStartedAt,
                    endedAt = effectiveStartedAt + durationSeconds * 1000L,
                    durationSeconds = durationSeconds,
                    updatedAt = now,
                ),
            )

            val routineId = workout.routineId
            if (routineId != null) {
                if (structureChoice == RoutineStructureChoice.UPDATE_ROUTINE) {
                    rewriteRoutineStructure(routineId, workout.id, occurrenceByWorkoutExerciseId)
                } else if (updateRoutineValues) {
                    // Skipped when the structure was just rewritten: that rewrite already carries
                    // the session's own values, so a second values pass would be redundant work
                    // against freshly-minted row ids.
                    applyRoutineValueUpdates(routineId, setsBefore, occurrenceByWorkoutExerciseId)
                }
            }

            val touchedExerciseIds = setsBefore.map { it.exerciseId }.toSet()
            val prs = personalRecordsUpdater.rebuildForExercises(touchedExerciseIds, workout.id, includeWarmups)
            FinishResult(workoutId = workout.id, prs = prs)
        }.also {
            // M21f: outside the transaction, deliberately — same reasoning as includeWarmups above
            // (a Health Connect read is local-IPC-but-still-not-Room I/O, so it must not pin the
            // Room transaction open across it). A missing/denied Health Connect grant degrades
            // silently to "no samples saved," never a failed finish -- a workout must always be
            // saveable with or without a connected wearable.
            saveHeartRateSamples(workoutId = workout.id, startedAt = effectiveStartedAt, endedAt = effectiveStartedAt + durationSeconds * 1000L)
            // After the COMPLETED commit, or the widget would recount without this workout.
            widgetRefresher.refresh()
        }
    }

    /**
     * The first heart-rate read for this workout. Shares [WorkoutHeartRateBackfill] with the
     * summary and History, which read again later when they come back to the foreground, since a
     * watch's heart rate usually reaches Health Connect after Save (2026-09-26). Never throws.
     */
    private suspend fun saveHeartRateSamples(workoutId: String, startedAt: Long, endedAt: Long) {
        heartRateBackfill.backfill(workoutId, startedAt, endedAt)
    }

    private companion object {
        private const val TAG = "WorkoutFinisher"
    }

    /**
     * Each workout-exercise block's 0-based position among blocks of the *same* exercise, using
     * [setsBefore]'s pre-purge block order — the one true numbering both pairing helpers below key
     * off, whether or not a given block goes on to survive the uncompleted-set purge.
     */
    private fun originalOccurrenceIndex(setsBefore: List<WorkoutSetWithExercise>): Map<String, Int> {
        val counters = mutableMapOf<String, Int>()
        return setsBefore
            .distinctBy { it.workoutExerciseId }
            .sortedBy { it.exerciseOrderIndex }
            .associate { row -> row.workoutExerciseId to (counters.merge(row.exerciseId, 1, Int::plus)!! - 1) }
    }

    /** §5.1.8(a) "Update Routine Values": positional, values-only, rep-ranges never touched. */
    private suspend fun applyRoutineValueUpdates(
        routineId: String,
        setsBefore: List<WorkoutSetWithExercise>,
        occurrenceByWorkoutExerciseId: Map<String, Int>,
    ) {
        // (exerciseId, original occurrence index) -> that block's sets, pre-purge. A fully-skipped
        // block still lands its own entry here (RoutineValueUpdater already no-ops on an
        // uncompleted set), so it correctly consumes its slot instead of vanishing from the count.
        val setsByExerciseAndOccurrence: Map<Pair<String, Int>, List<StatSet>> = setsBefore
            .groupBy { it.workoutExerciseId }
            .entries
            .associate { (weId, rows) ->
                val exerciseId = rows.first().exerciseId
                (exerciseId to occurrenceByWorkoutExerciseId.getValue(weId)) to rows.map { it.set }.sortedBy { it.orderIndex }
            }

        val routineExercises = routineRepository.getExercisesForRoutine(routineId).sortedBy { it.orderIndex }
        val occurrence = mutableMapOf<String, Int>()

        routineExercises.forEach { routineExercise ->
            // Pair the Nth routine block of an exercise with the Nth *original* session block of
            // it — never a freshly-recounted "Nth survivor", which is what let a skipped block
            // shift everything after it.
            val nth = occurrence.merge(routineExercise.exerciseId, 1, Int::plus)!! - 1
            val loggedSets = setsByExerciseAndOccurrence[routineExercise.exerciseId to nth] ?: return@forEach
            if (loggedSets.none { it.isCompleted }) return@forEach
            val routineSets = routineRepository.getSetsForRoutineExercise(routineExercise.id)

            val updated = RoutineValueUpdater.updatedTargets(
                routineSets = routineSets.map { it.toTargets() },
                loggedSets = loggedSets.map { it.toLoggedValues() },
            )
            // Positional pairing — updatedTargets preserves input order, so index i maps back to
            // routineSets[i] and its stable row id (no delete/reinsert, no id churn).
            updated.forEachIndexed { index, targets ->
                val original = routineSets.getOrNull(index) ?: return@forEachIndexed
                if (targets.hasSameTargetsAs(original)) return@forEachIndexed
                routineRepository.updateRoutineSetTargets(
                    id = original.id,
                    targetWeightKg = targets.targetWeightKg,
                    targetReps = targets.targetReps,
                    targetDurationSeconds = targets.targetDurationSeconds,
                    targetDistanceMeters = targets.targetDistanceMeters,
                )
            }
        }
    }

    /**
     * §5.1.8(b) "Update Routine": rewrite the routine's structure to match what was actually
     * performed. The session supplies the *shape* (which exercises, how many sets) and the
     * concrete values; anything the session has no opinion about is carried over from the routine
     * slot it replaces, matched by Nth-occurrence of the exercise and then by set position.
     *
     * That carry-over is what keeps a rewrite from being lossy. A structural change to one
     * exercise re-mints every row in the routine, so without it an authored 8-12 rep range on an
     * untouched exercise would be erased — and `updateRoutineStructure` is a delete-and-reinsert,
     * so there is no undo.
     */
    private suspend fun rewriteRoutineStructure(
        routineId: String,
        workoutId: String,
        occurrenceByWorkoutExerciseId: Map<String, Int>,
    ) {
        val routine = routineRepository.getRoutineById(routineId) ?: return
        // Post-purge on purpose: this *is* the surviving shape the rewrite writes out. Only the
        // occurrence lookup below needs the pre-purge numbering.
        val workoutExercises = workoutRepository.getExercisesForWorkout(workoutId).sortedBy { it.orderIndex }

        val originalExercises = routineRepository.getExercisesForRoutine(routineId).sortedBy { it.orderIndex }
        val originalByExercise = originalExercises.groupBy { it.exerciseId }
        val originalSets = originalExercises.associate { re ->
            re.id to routineRepository.getSetsForRoutineExercise(re.id).sortedBy { it.orderIndex }
        }
        val survivingSetsByWorkoutExerciseId = workoutExercises.associate { we ->
            we.id to workoutRepository.getSetsForWorkoutExercise(we.id).sortedBy { it.orderIndex }
        }

        val isCircuit = routine.structure == WorkoutStructure.CIRCUIT
        val circuitRounds = if (isCircuit) {
            survivingSetsByWorkoutExerciseId.values.maxOfOrNull { sets -> sets.maxOfOrNull { it.orderIndex + 1 } ?: 0 } ?: 0
        } else {
            0
        }

        val newExercises = mutableListOf<RoutineExerciseEntity>()
        val newSets = mutableListOf<RoutineSetEntity>()

        workoutExercises.forEachIndexed { exerciseIndex, we ->
            // A surviving block's TRUE original position among same-exercise blocks — not its
            // position among survivors — so a skipped earlier block never shifts this one onto
            // the wrong routine slot's notes/rep-range.
            val nth = occurrenceByWorkoutExerciseId.getValue(we.id)
            val original = originalByExercise[we.exerciseId]?.getOrNull(nth)
            val routineExerciseId = UUID.randomUUID().toString()
            newExercises += RoutineExerciseEntity(
                id = routineExerciseId,
                routineId = routineId,
                exerciseId = we.exerciseId,
                orderIndex = exerciseIndex,
                supersetGroup = we.supersetGroup,
                restTimerSeconds = we.restTimerSeconds,
                // The routine's persistent coaching note, NOT the session note: the two are
                // documented as distinct on WorkoutExerciseEntity, and the session's "shoulder
                // tight today" must not become the routine's standing cue. A block with no
                // routine counterpart (added mid-session) simply starts with no note.
                notes = original?.notes,
            )
            val survivingSets = survivingSetsByWorkoutExerciseId.getValue(we.id)
            val originalRoutineSets = original?.let { originalSets[it.id] }.orEmpty()
            newSets += if (isCircuit) {
                circuitExerciseSets(routineExerciseId, circuitRounds, survivingSets, originalRoutineSets)
            } else {
                regularExerciseSets(routineExerciseId, survivingSets, originalRoutineSets)
            }
        }

        routineRepository.updateRoutineStructure(
            routine.copy(updatedAt = clock.now().toEpochMilliseconds()),
            dropOrphanSupersets(newExercises),
            newSets,
        )
    }

    /** The REGULAR-structure rewrite: purely positional, one routine set per surviving session set. */
    private fun regularExerciseSets(
        routineExerciseId: String,
        survivingSets: List<WorkoutSetEntity>,
        originalRoutineSets: List<RoutineSetEntity>,
    ): List<RoutineSetEntity> =
        survivingSets.mapIndexed { setIndex, ws -> sessionRoutineSet(routineExerciseId, setIndex, ws, originalRoutineSets.getOrNull(setIndex)) }

    /**
     * M11 circuits: a set row's orderIndex IS its round, and the uncompleted-set purge deletes
     * skipped rows WITHOUT re-indexing — survivors can be {0,2}. Compacting them positionally (as
     * [regularExerciseSets] does) would write round 3's logged values into the routine's round-2
     * slot and leave exercises with unequal set counts, breaking the rectangle invariant in a
     * persisted CIRCUIT routine (which the builder then "repairs" by duplicating the shifted last
     * row — cementing the misattribution). So this pairs by orderIndex and PADS a skipped round
     * from the routine's own authored target for that round instead of compacting, keeping every
     * round's targets on the round they belong to.
     */
    private fun circuitExerciseSets(
        routineExerciseId: String,
        circuitRounds: Int,
        survivingSets: List<WorkoutSetEntity>,
        originalRoutineSets: List<RoutineSetEntity>,
    ): List<RoutineSetEntity> {
        val exerciseNewSets = mutableListOf<RoutineSetEntity>()
        (0 until circuitRounds).forEach { round ->
            val ws = survivingSets.find { it.orderIndex == round }
            val originalSet = originalRoutineSets.getOrNull(round)
            exerciseNewSets += when {
                // Performed round: the session's values, rep range carried over.
                ws != null -> sessionRoutineSet(routineExerciseId, round, ws, originalSet)
                // Skipped round: keep the routine's own authored target for THIS round —
                // never a later round's values shifted into its place.
                originalSet != null -> originalSet.copy(
                    id = UUID.randomUUID().toString(),
                    routineExerciseId = routineExerciseId,
                    orderIndex = round,
                )
                // No session row and no authored target (e.g. an exercise added mid-session
                // whose row for this round was purged): pad from the previous round's targets —
                // the builder's own padToRounds semantics.
                else -> exerciseNewSets.lastOrNull().let { last ->
                    RoutineSetEntity(
                        id = UUID.randomUUID().toString(),
                        routineExerciseId = routineExerciseId,
                        orderIndex = round,
                        setType = SetType.NORMAL,
                        targetWeightKg = last?.targetWeightKg,
                        targetReps = last?.targetReps,
                        targetRepRangeMin = last?.targetRepRangeMin,
                        targetRepRangeMax = last?.targetRepRangeMax,
                        targetDurationSeconds = last?.targetDurationSeconds,
                        targetDistanceMeters = last?.targetDistanceMeters,
                    )
                }
            }
        }
        return exerciseNewSets
    }

    /**
     * One rewritten routine set from a performed session row. A rep range is an authored target
     * the session cannot express — logging 10 reps against "8-12" does not mean the user wants
     * the range replaced by an exact 10. Same rule the values-only pass already applies (§8.10).
     */
    private fun sessionRoutineSet(
        routineExerciseId: String,
        orderIndex: Int,
        ws: WorkoutSetEntity,
        originalSet: RoutineSetEntity?,
    ): RoutineSetEntity {
        val repRangeMin = originalSet?.targetRepRangeMin
        val repRangeMax = originalSet?.targetRepRangeMax
        val isRepRange = repRangeMin != null || repRangeMax != null
        return RoutineSetEntity(
            id = UUID.randomUUID().toString(),
            routineExerciseId = routineExerciseId,
            orderIndex = orderIndex,
            setType = ws.setType,
            targetWeightKg = ws.weightKg,
            targetReps = if (isRepRange) originalSet?.targetReps else ws.reps,
            targetRepRangeMin = repRangeMin,
            targetRepRangeMax = repRangeMax,
            targetDurationSeconds = ws.durationSeconds,
            targetDistanceMeters = ws.distanceMeters,
        )
    }

    /**
     * The uncompleted-set purge can delete one half of a superset, leaving its partner alone in a
     * group. A one-member group is not a valid state anywhere else in the app (both the logger and
     * the routine builder actively clean it up), so it must not be written into a routine either.
     */
    private fun dropOrphanSupersets(exercises: List<RoutineExerciseEntity>): List<RoutineExerciseEntity> {
        val memberCount = exercises.mapNotNull { it.supersetGroup }.groupingBy { it }.eachCount()
        return exercises.map {
            if (it.supersetGroup != null && (memberCount[it.supersetGroup] ?: 0) < 2) it.copy(supersetGroup = null) else it
        }
    }

}

private fun RoutineSetEntity.toTargets() = RoutineSetTargets(
    orderIndex = orderIndex,
    targetWeightKg = targetWeightKg,
    targetReps = targetReps,
    targetRepRangeMin = targetRepRangeMin,
    targetRepRangeMax = targetRepRangeMax,
    targetDurationSeconds = targetDurationSeconds,
    targetDistanceMeters = targetDistanceMeters,
)

private fun StatSet.toLoggedValues() = LoggedSetValues(
    orderIndex = orderIndex,
    weightKg = weightKg,
    reps = reps,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    isCompleted = isCompleted,
)

private fun RoutineSetTargets.hasSameTargetsAs(entity: RoutineSetEntity): Boolean =
    targetWeightKg == entity.targetWeightKg &&
        targetReps == entity.targetReps &&
        targetDurationSeconds == entity.targetDurationSeconds &&
        targetDistanceMeters == entity.targetDistanceMeters
