package com.enil.logez.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.enil.logez.core.data.entity.PersonalRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * `personal_records` is a derived cache (PHASE2_PLAN.md §3.2, §8.4): [rebuildFor] is the only
 * writer, always delete-then-reinsert per exercise — never an incremental patch.
 */
@Dao
interface RecordsDao {
    @Query("DELETE FROM personal_records WHERE exercise_id = :exerciseId")
    suspend fun deleteForExercise(exerciseId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<PersonalRecordEntity>)

    @Transaction
    suspend fun rebuildFor(exerciseId: String, records: List<PersonalRecordEntity>) {
        deleteForExercise(exerciseId)
        insertAll(records)
    }

    @Query("SELECT * FROM personal_records WHERE workout_id = :workoutId")
    suspend fun getForWorkout(workoutId: String): List<PersonalRecordEntity>

    /**
     * Every workout id that any record points at — the History feed's "Records" chip gate for the
     * whole feed in one query, rather than one [getForWorkout] per card. The chip only needs
     * existence, never the record itself (§5.2: "when there are none, the chip is absent — never
     * '0 PRs'").
     */
    @Query("SELECT DISTINCT workout_id FROM personal_records")
    suspend fun getWorkoutIdsWithRecords(): List<String>

    /** Cached bests for one exercise — seeds the live PR banner's comparison (§8.4 point 1). */
    @Query("SELECT * FROM personal_records WHERE exercise_id = :exerciseId")
    suspend fun getForExercise(exerciseId: String): List<PersonalRecordEntity>

    /** Records achieved inside a half-open window — the Monthly Report's PR list (§5.2 card 6). */
    @Query("SELECT * FROM personal_records WHERE achieved_at >= :fromMillis AND achieved_at < :untilMillis")
    suspend fun getAchievedBetween(fromMillis: Long, untilMillis: Long): List<PersonalRecordEntity>

    @Query("SELECT * FROM personal_records WHERE workout_id = :workoutId")
    fun observeForWorkout(workoutId: String): Flow<List<PersonalRecordEntity>>

    @Query("SELECT * FROM personal_records WHERE exercise_id = :exerciseId")
    fun observeForExercise(exerciseId: String): Flow<List<PersonalRecordEntity>>
}
