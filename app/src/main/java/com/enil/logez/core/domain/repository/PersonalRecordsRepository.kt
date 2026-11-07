package com.enil.logez.core.domain.repository

import com.enil.logez.core.data.entity.PersonalRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * The sole writer of the `personal_records` derived cache (PHASE2_PLAN.md §8.4). [rebuildFor]
 * fully recomputes an exercise's records from its COMPLETED-workout history — this stays a
 * thin passthrough at M1 (no consumer yet); the real rebuild algorithm (querying stat sets,
 * computing per-PrType bests, resolving the earliest achiever) is [PrCalculator]'s job, wired in
 * at M4c where a workout save first triggers it.
 */
interface PersonalRecordsRepository {
    suspend fun rebuildFor(exerciseId: String, records: List<PersonalRecordEntity>)
    suspend fun getForWorkout(workoutId: String): List<PersonalRecordEntity>

    /** Workout ids holding at least one record — the History feed's Records-chip gate, whole feed in one query. */
    suspend fun getWorkoutIdsWithRecords(): Set<String>

    /** Records achieved inside a half-open millis window — the Monthly Report's PR list (§5.2 card 6). */
    suspend fun getAchievedBetween(fromMillis: Long, untilMillis: Long): List<PersonalRecordEntity>
    suspend fun getForExercise(exerciseId: String): List<PersonalRecordEntity>
    fun observeForWorkout(workoutId: String): Flow<List<PersonalRecordEntity>>
    fun observeForExercise(exerciseId: String): Flow<List<PersonalRecordEntity>>
}
