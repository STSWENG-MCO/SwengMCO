package com.enil.logez.feature.measurements

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.media.ProgressPhotoStore
import com.enil.logez.core.domain.calc.BodyMeasurementChart
import com.enil.logez.core.domain.calc.BodyMeasurementMetric
import com.enil.logez.core.domain.calc.BodyMeasurementPoint
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.calc.visibleMetrics
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.MeasurementsTrackingMode
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.BodyMeasurement
import com.enil.logez.core.domain.repository.MeasurementRepository
import com.enil.logez.core.domain.repository.ProgressPhoto
import com.enil.logez.core.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.enil.logez.core.data.media.MediaFileCleaner

/**
 * PHASE2_PLAN.md §5.2 "Measurements" (Profile → Measurements). Reactive, not refresh-driven — both
 * [MeasurementRepository.observeAll] and [MeasurementRepository.observeAllPhotos] are Flows already
 * (unlike the workout-history repositories' one-shot suspend reads), so this combines them directly
 * with settings and local UI-only selection state, matching [com.enil.logez.feature.history.HistoryViewModel]'s
 * shape rather than [com.enil.logez.feature.analytics.AnalyticsViewModel]'s manual-snapshot one.
 */
@HiltViewModel
class MeasurementsViewModel @Inject constructor(
    private val measurementRepository: MeasurementRepository,
    private val settingsRepository: SettingsRepository,
    private val progressPhotoStore: ProgressPhotoStore,
    private val clock: Clock,
    private val mediaFileCleaner: MediaFileCleaner = MediaFileCleaner.NoOp,
) : ViewModel() {

    private data class Selections(
        val metric: BodyMeasurementMetric = BodyMeasurementMetric.WEIGHT,
        val range: ChartRange = ChartRange.LAST_3_MONTHS,
        val pendingPhotoReplace: PendingPhotoReplace? = null,
        val photoCaptureError: Boolean = false,
    )

    private val selections = MutableStateFlow(Selections())

    val uiState: StateFlow<MeasurementsUiState> = combine(
        measurementRepository.observeAll(),
        measurementRepository.observeAllPhotos(),
        settingsRepository.settings,
        selections,
    ) { entries, photos, settings, sel ->
        val visible = visibleMetrics(settings.measurementsTrackingMode)
        val effectiveMetric = sel.metric.takeIf { it in visible } ?: BodyMeasurementMetric.WEIGHT
        MeasurementsUiState(
            isLoading = false,
            entries = entries,
            photos = photos,
            selectedMetric = effectiveMetric,
            selectedRange = sel.range,
            chartPoints = BodyMeasurementChart.points(effectiveMetric, entries, sel.range, today()),
            weightUnit = settings.weightUnit,
            lengthUnit = settings.lengthUnit,
            trackingMode = settings.measurementsTrackingMode,
            pendingPhotoReplace = sel.pendingPhotoReplace,
            photoCaptureError = sel.photoCaptureError,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MeasurementsUiState())

    fun selectMetric(metric: BodyMeasurementMetric) = selections.update { it.copy(metric = metric) }
    fun selectRange(range: ChartRange) = selections.update { it.copy(range = range) }
    fun setTrackingMode(mode: MeasurementsTrackingMode) = viewModelScope.launch { settingsRepository.setMeasurementsTrackingMode(mode) }

    fun saveEntry(measurement: BodyMeasurement) {
        viewModelScope.launch { measurementRepository.upsert(measurement.copy(updatedAt = clock.now().toEpochMilliseconds())) }
    }

    fun deleteEntry(date: String) {
        viewModelScope.launch { measurementRepository.deleteByDate(date) }
    }

    /** [uri] is a `file://` Uri into [android.content.Context.getCacheDir] from [CameraCaptureScreen] —
     * never persisted as-is; only ever consumed by [persistCapturedPhoto], which always deletes it. */
    fun attachPhotoFromCapture(uri: Uri) {
        viewModelScope.launch {
            val date = today().format(DateTimeFormatter.ISO_LOCAL_DATE)
            val existing = uiState.value.photos.find { it.date == date }
            if (existing != null) {
                selections.update { it.copy(pendingPhotoReplace = PendingPhotoReplace(existing, uri)) }
            } else {
                persistCapturedPhoto(uri, date, replacing = null)
            }
        }
    }

    fun confirmReplaceTodaysPhoto() {
        val pending = selections.value.pendingPhotoReplace ?: return
        viewModelScope.launch {
            persistCapturedPhoto(pending.newCacheUri, pending.existing.date, replacing = pending.existing)
            selections.update { it.copy(pendingPhotoReplace = null) }
        }
    }

    fun cancelReplaceTodaysPhoto() {
        val pending = selections.value.pendingPhotoReplace ?: return
        selections.update { it.copy(pendingPhotoReplace = null) }
        deleteCacheFile(pending.newCacheUri)
    }

    /** Deletes the row, then the JPEG itself; until 2026-09-25 the file outlived its row forever. */
    fun deletePhoto(photo: ProgressPhoto) {
        viewModelScope.launch {
            measurementRepository.deletePhoto(photo)
            mediaFileCleaner.deleteIfUnreferenced(photo.filePath)
        }
    }

    fun dismissPhotoCaptureError() = selections.update { it.copy(photoCaptureError = false) }

    private suspend fun persistCapturedPhoto(cacheUri: Uri, date: String, replacing: ProgressPhoto?) {
        try {
            val path = progressPhotoStore.copyToAppStorage(cacheUri)
            if (path == null) {
                selections.update { it.copy(photoCaptureError = true) }
                return
            }
            measurementRepository.upsertPhoto(
                ProgressPhoto(id = UUID.randomUUID().toString(), date = date, filePath = path, createdAt = clock.now().toEpochMilliseconds()),
            )
            replacing?.let {
                measurementRepository.deletePhoto(it)
                mediaFileCleaner.deleteIfUnreferenced(it.filePath)
            }
        } finally {
            deleteCacheFile(cacheUri)
        }
    }

    private fun deleteCacheFile(uri: Uri) {
        uri.path?.let { File(it).delete() }
    }

    private fun today(): LocalDate =
        Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(ZoneId.systemDefault()).toLocalDate()
}

/** Non-null while the "replace today's photo?" confirm is open — [newCacheUri] is the just-captured
 * temp file, still sitting in cacheDir, not yet copied into permanent storage either way. */
data class PendingPhotoReplace(val existing: ProgressPhoto, val newCacheUri: Uri)

data class MeasurementsUiState(
    val isLoading: Boolean = true,
    val entries: List<BodyMeasurement> = emptyList(),
    val photos: List<ProgressPhoto> = emptyList(),
    val selectedMetric: BodyMeasurementMetric = BodyMeasurementMetric.WEIGHT,
    val selectedRange: ChartRange = ChartRange.LAST_3_MONTHS,
    val chartPoints: List<BodyMeasurementPoint> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val lengthUnit: LengthUnit = LengthUnit.CM,
    val trackingMode: MeasurementsTrackingMode = MeasurementsTrackingMode.COMPLETE,
    val pendingPhotoReplace: PendingPhotoReplace? = null,
    val photoCaptureError: Boolean = false,
)
