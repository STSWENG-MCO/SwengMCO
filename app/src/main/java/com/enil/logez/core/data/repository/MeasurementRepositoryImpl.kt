package com.enil.logez.core.data.repository

import com.enil.logez.core.data.dao.MeasurementDao
import com.enil.logez.core.data.entity.BodyMeasurementEntity
import com.enil.logez.core.data.entity.ProgressPhotoEntity
import com.enil.logez.core.domain.repository.BodyMeasurement
import com.enil.logez.core.domain.repository.MeasurementRepository
import com.enil.logez.core.domain.repository.ProgressPhoto
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MeasurementRepositoryImpl @Inject constructor(
    private val dao: MeasurementDao,
) : MeasurementRepository {
    override fun observeAll(): Flow<List<BodyMeasurement>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun upsert(measurement: BodyMeasurement) = dao.upsertMeasurement(measurement.toEntity())
    override suspend fun getByDate(date: String): BodyMeasurement? = dao.getByDate(date)?.toDomain()
    override suspend fun deleteByDate(date: String) = dao.deleteByDate(date)
    override suspend fun getLatestWeightKgOnOrBefore(date: String): Double? =
        dao.getLatestWeightOnOrBefore(date)?.weightKg

    override fun observeAllPhotos(): Flow<List<ProgressPhoto>> = dao.observeAllPhotos().map { list -> list.map { it.toDomain() } }
    override suspend fun upsertPhoto(photo: ProgressPhoto) = dao.upsertPhoto(photo.toEntity())
    override suspend fun getPhotoByDate(date: String): ProgressPhoto? = dao.getPhotoByDate(date)?.toDomain()
    override suspend fun deletePhoto(photo: ProgressPhoto) = dao.deletePhoto(photo.toEntity())
}

private fun BodyMeasurementEntity.toDomain() = BodyMeasurement(
    date = date, weightKg = weightKg, leanMassKg = leanMassKg, fatPercent = fatPercent,
    neckCm = neckCm, shoulderCm = shoulderCm, chestCm = chestCm,
    leftBicepCm = leftBicepCm, rightBicepCm = rightBicepCm,
    leftForearmCm = leftForearmCm, rightForearmCm = rightForearmCm,
    abdomenCm = abdomenCm, waistCm = waistCm, hipsCm = hipsCm,
    leftThighCm = leftThighCm, rightThighCm = rightThighCm,
    leftCalfCm = leftCalfCm, rightCalfCm = rightCalfCm, updatedAt = updatedAt,
)

private fun BodyMeasurement.toEntity() = BodyMeasurementEntity(
    date = date, weightKg = weightKg, leanMassKg = leanMassKg, fatPercent = fatPercent,
    neckCm = neckCm, shoulderCm = shoulderCm, chestCm = chestCm,
    leftBicepCm = leftBicepCm, rightBicepCm = rightBicepCm,
    leftForearmCm = leftForearmCm, rightForearmCm = rightForearmCm,
    abdomenCm = abdomenCm, waistCm = waistCm, hipsCm = hipsCm,
    leftThighCm = leftThighCm, rightThighCm = rightThighCm,
    leftCalfCm = leftCalfCm, rightCalfCm = rightCalfCm, updatedAt = updatedAt,
)

private fun ProgressPhotoEntity.toDomain() = ProgressPhoto(id = id, date = date, filePath = filePath, createdAt = createdAt)

private fun ProgressPhoto.toEntity() = ProgressPhotoEntity(id = id, date = date, filePath = filePath, createdAt = createdAt)
