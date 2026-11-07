package com.enil.logez.fakes

import com.enil.logez.core.domain.repository.BodyMeasurement
import com.enil.logez.core.domain.repository.MeasurementRepository
import com.enil.logez.core.domain.repository.ProgressPhoto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake (PHASE2_PLAN.md §10.1 rule 2). Bodyweight is the only field M4c's volume/PR math
 * reads. [bodyweightKg] gives one weight for all of history; [weightsByDate] models a weight that
 * actually changed over time, which is what §8.3's per-workout resolution depends on — the lookup
 * honours "on or before", so a workout dated between two measurements sees the earlier one.
 */
class FakeMeasurementRepository(
    private val bodyweightKg: Double? = null,
    private val weightsByDate: Map<String, Double> = emptyMap(),
) : MeasurementRepository {
    private val state = MutableStateFlow<List<BodyMeasurement>>(emptyList())
    private val photos = MutableStateFlow<List<ProgressPhoto>>(emptyList())

    /** Every date this fake was asked about, in call order — proves the resolution is per-workout. */
    val queriedDates = mutableListOf<String>()

    override fun observeAll(): Flow<List<BodyMeasurement>> = state
    override suspend fun upsert(measurement: BodyMeasurement) {
        state.value = state.value.filterNot { it.date == measurement.date } + measurement
    }

    override suspend fun getByDate(date: String): BodyMeasurement? = state.value.find { it.date == date }
    override suspend fun deleteByDate(date: String) {
        state.value = state.value.filterNot { it.date == date }
    }

    override suspend fun getLatestWeightKgOnOrBefore(date: String): Double? {
        queriedDates += date
        if (weightsByDate.isEmpty()) return bodyweightKg
        return weightsByDate.entries.filter { it.key <= date }.maxByOrNull { it.key }?.value
    }

    override fun observeAllPhotos(): Flow<List<ProgressPhoto>> = photos
    override suspend fun upsertPhoto(photo: ProgressPhoto) {
        photos.value = photos.value.filterNot { it.date == photo.date } + photo
    }

    override suspend fun getPhotoByDate(date: String): ProgressPhoto? = photos.value.find { it.date == date }
    override suspend fun deletePhoto(photo: ProgressPhoto) {
        photos.value = photos.value.filterNot { it.id == photo.id }
    }
}
