package com.enil.logez.core.data.repository

import com.enil.logez.core.data.dao.RecordsDao
import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class PersonalRecordsRepositoryImpl @Inject constructor(
    private val dao: RecordsDao,
) : PersonalRecordsRepository {
    override suspend fun rebuildFor(exerciseId: String, records: List<PersonalRecordEntity>) =
        dao.rebuildFor(exerciseId, records)

    override suspend fun getForWorkout(workoutId: String): List<PersonalRecordEntity> = dao.getForWorkout(workoutId)
    override suspend fun getWorkoutIdsWithRecords(): Set<String> = dao.getWorkoutIdsWithRecords().toSet()
    override suspend fun getAchievedBetween(fromMillis: Long, untilMillis: Long): List<PersonalRecordEntity> =
        dao.getAchievedBetween(fromMillis, untilMillis)
    override suspend fun getForExercise(exerciseId: String): List<PersonalRecordEntity> = dao.getForExercise(exerciseId)
    override fun observeForWorkout(workoutId: String): Flow<List<PersonalRecordEntity>> = dao.observeForWorkout(workoutId)
    override fun observeForExercise(exerciseId: String): Flow<List<PersonalRecordEntity>> = dao.observeForExercise(exerciseId)
}
