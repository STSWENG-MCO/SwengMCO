package com.enil.logez.core.domain.repository

import kotlinx.coroutines.flow.Flow

/** Body measurements and progress photos, tracked over time independently of workouts. */
interface MeasurementRepository {
    fun observeAll(): Flow<List<BodyMeasurement>>
    suspend fun upsert(measurement: BodyMeasurement)
    suspend fun getByDate(date: String): BodyMeasurement?
    suspend fun deleteByDate(date: String)

    /** §8.1 bodyweight resolution: newest entry on/before [date] with a non-null weight. */
    suspend fun getLatestWeightKgOnOrBefore(date: String): Double?

    fun observeAllPhotos(): Flow<List<ProgressPhoto>>
    suspend fun upsertPhoto(photo: ProgressPhoto)
    suspend fun getPhotoByDate(date: String): ProgressPhoto?
    suspend fun deletePhoto(photo: ProgressPhoto)
}

/** Domain model mirroring [com.enil.logez.core.data.entity.BodyMeasurementEntity] field-for-field. */
data class BodyMeasurement(
    val date: String,
    val weightKg: Double?,
    val leanMassKg: Double?,
    val fatPercent: Double?,
    val neckCm: Double?,
    val shoulderCm: Double?,
    val chestCm: Double?,
    val leftBicepCm: Double?,
    val rightBicepCm: Double?,
    val leftForearmCm: Double?,
    val rightForearmCm: Double?,
    val abdomenCm: Double?,
    val waistCm: Double?,
    val hipsCm: Double?,
    val leftThighCm: Double?,
    val rightThighCm: Double?,
    val leftCalfCm: Double?,
    val rightCalfCm: Double?,
    val updatedAt: Long,
)

/** Domain model mirroring [com.enil.logez.core.data.entity.ProgressPhotoEntity] field-for-field. */
data class ProgressPhoto(
    val id: String,
    val date: String,
    val filePath: String,
    val createdAt: Long,
)
