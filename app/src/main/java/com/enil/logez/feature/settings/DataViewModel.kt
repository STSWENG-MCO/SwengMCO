package com.enil.logez.feature.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enil.logez.BuildConfig
import com.enil.logez.R
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.backup.BackupManifest
import com.enil.logez.core.data.backup.BackupReader
import com.enil.logez.core.data.backup.BackupRestorer
import com.enil.logez.core.data.backup.BackupWriter
import com.enil.logez.core.data.backup.LocalDataEraser
import com.enil.logez.core.data.backup.RestoreMarker
import com.enil.logez.core.data.export.CsvExporter
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.HealthConnectDisconnector
import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.HealthMetricsSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class ExportKind { WORKOUTS_CSV, MEASUREMENTS_CSV, BACKUP_ZIP }

sealed interface DataJob {
    data object Idle : DataJob
    data class Working(val labelRes: Int) : DataJob

    /** Staged and verified; nothing has been replaced yet. The user decides from here. */
    data class ConfirmRestore(val manifest: BackupManifest) : DataJob
    data class Done(val messageRes: Int) : DataJob
    data class Failed(val messageRes: Int) : DataJob
}

data class DataUiState(
    val workoutSetCount: Int? = null,
    val measurementCount: Int? = null,
    /** Gates the "Manage Health Connect access" row; the disconnect-and-delete row always shows. */
    val healthConnectAvailable: Boolean = false,
    /**
     * What LogEZ may read right now, shown under the manage row (2026-09-26). Nothing in the app
     * used to show whether heart rate itself was allowed: Profile shows steps and calories, so a
     * grant without heart rate looked complete. Null until first loaded.
     */
    val grantedHealthTypes: Set<HealthDataType>? = null,
    /** Disconnected this session: access ends when LogEZ restarts, reconnect from Profile. */
    val healthDisconnectedThisSession: Boolean = false,
    val job: DataJob = DataJob.Idle,
)

@HiltViewModel
class DataViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val csvExporter: CsvExporter,
    private val backupWriter: BackupWriter,
    private val backupReader: BackupReader,
    private val backupRestorer: BackupRestorer,
    private val workoutRepository: WorkoutRepository,
    private val healthMetricsSource: HealthMetricsSource,
    private val healthConnectDisconnector: HealthConnectDisconnector,
    private val localDataEraser: LocalDataEraser,
    private val logger: AppLogger,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DataUiState())
    val uiState: StateFlow<DataUiState> = _uiState.asStateFlow()

    private val stagingDir get() = RestoreMarker.stagingDirIn(context.filesDir)

    /**
     * One data job at a time (2026-09-25 review): a backup racing a delete-all exported a zip whose
     * row counts no longer matched its contents, and a delete-all racing a restore let restored
     * photos survive the delete. The screen also disables its rows and back arrow while busy.
     */
    private val busy: Boolean get() = _uiState.value.job is DataJob.Working

    init {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    workoutSetCount = csvExporter.workoutSetCount(),
                    measurementCount = csvExporter.measurementCount(),
                    healthConnectAvailable = healthMetricsSource.availability() == HealthConnectAvailability.Available,
                )
            }
        }
    }

    /** Re-reads the grants; the screen calls this on resume, e.g. back from Health Connect's settings. */
    fun refreshHealthAccess() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    grantedHealthTypes = healthMetricsSource.grantedTypes(),
                    healthDisconnectedThisSession = healthMetricsSource.accessEndsOnRestart,
                )
            }
        }
    }

    fun export(kind: ExportKind, uri: Uri) {
        if (busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(job = DataJob.Working(labelRes(kind))) }
            try {
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: error("the chosen location is not writable")
                stream.use { out ->
                    when (kind) {
                        ExportKind.WORKOUTS_CSV -> csvExporter.exportWorkouts(out)
                        ExportKind.MEASUREMENTS_CSV -> csvExporter.exportMeasurements(out)
                        ExportKind.BACKUP_ZIP -> backupWriter.write(
                            out = out,
                            mediaRoot = context.filesDir,
                            appVersionName = BuildConfig.VERSION_NAME,
                            appVersionCode = BuildConfig.VERSION_CODE.toLong(),
                        )
                    }
                }
                _uiState.update { it.copy(job = DataJob.Done(R.string.data_export_done)) }
            } catch (t: Throwable) {
                logger.e(TAG, "Export failed for $kind", t)
                // The picker created the document the moment it returned, so without this a failed
                // export leaves a broken empty file wherever the user chose to put it.
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_export_failed)) }
            }
        }
    }

    /**
     * Unpacks and checks the archive, then stops and asks. Nothing the user has is touched until
     * they confirm — staging is deliberately the whole first half of a restore.
     */
    fun prepareRestore(uri: Uri) {
        if (busy) return
        viewModelScope.launch {
            if (workoutRepository.getInProgress() != null) {
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_restore_blocked_in_progress)) }
                return@launch
            }
            _uiState.update { it.copy(job = DataJob.Working(R.string.data_restore_reading)) }
            try {
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("the chosen file could not be opened")
                val manifest = input.use { backupReader.stage(it, stagingDir) }

                when (manifest.compatibility(BackupWriter.ROOM_SCHEMA_VERSION)) {
                    BackupManifest.Compatibility.TooNew -> {
                        stagingDir.deleteRecursively()
                        _uiState.update { it.copy(job = DataJob.Failed(R.string.data_restore_too_new)) }
                    }
                    BackupManifest.Compatibility.Ok ->
                        _uiState.update { it.copy(job = DataJob.ConfirmRestore(manifest)) }
                }
            } catch (t: Throwable) {
                logger.e(TAG, "Reading the backup failed", t)
                stagingDir.deleteRecursively()
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_restore_unreadable)) }
            }
        }
    }

    /** The destructive half. Only reachable from [DataJob.ConfirmRestore]. */
    fun confirmRestore(manifest: BackupManifest) {
        if (busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(job = DataJob.Working(R.string.data_restore_restoring)) }
            try {
                // NonCancellable: leaving the screen mid-restore used to cancel it between the
                // database commit and the photo swap.
                withContext(NonCancellable) { backupRestorer.restore(stagingDir, context.filesDir, manifest) }
                _uiState.update {
                    it.copy(
                        job = DataJob.Done(R.string.data_restore_done),
                        workoutSetCount = csvExporter.workoutSetCount(),
                        measurementCount = csvExporter.measurementCount(),
                    )
                }
            } catch (t: Throwable) {
                logger.e(TAG, "Restore failed", t)
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_restore_failed)) }
            }
        }
    }

    fun cancelRestore() {
        viewModelScope.launch {
            stagingDir.deleteRecursively()
            _uiState.update { it.copy(job = DataJob.Idle) }
        }
    }

    /**
     * Revokes Health Connect access and deletes every step, calorie and heart-rate reading LogEZ
     * saved from it. Workouts, routes and measurements are untouched. Confirmed by the screen.
     */
    fun disconnectHealthConnect() {
        if (busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(job = DataJob.Working(R.string.data_health_disconnecting)) }
            try {
                withContext(NonCancellable) { healthConnectDisconnector.disconnectAndDelete() }
                _uiState.update { it.copy(job = DataJob.Done(R.string.data_health_disconnected)) }
                refreshHealthAccess()
            } catch (t: Throwable) {
                logger.e(TAG, "Deleting Health Connect data failed", t)
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_health_disconnect_failed)) }
            }
        }
    }

    /**
     * Erases everything LogEZ holds and restores default settings. Refused while a workout is in
     * progress, for the same reason restore is: the live session points at rows this deletes.
     */
    fun deleteAllData() {
        if (busy) return
        viewModelScope.launch {
            if (workoutRepository.getInProgress() != null) {
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_delete_all_blocked_in_progress)) }
                return@launch
            }
            _uiState.update { it.copy(job = DataJob.Working(R.string.data_delete_all_working)) }
            try {
                localDataEraser.eraseEverything()
                _uiState.update {
                    it.copy(
                        job = DataJob.Done(R.string.data_delete_all_done),
                        workoutSetCount = csvExporter.workoutSetCount(),
                        measurementCount = csvExporter.measurementCount(),
                    )
                }
            } catch (t: Throwable) {
                logger.e(TAG, "Deleting all data failed", t)
                _uiState.update { it.copy(job = DataJob.Failed(R.string.data_delete_all_failed)) }
            }
        }
    }

    /** The picker was dismissed — nothing was created, nothing to clean up. */
    fun pickerCancelled() = _uiState.update { it.copy(job = DataJob.Idle) }

    fun dismissJob() = _uiState.update { it.copy(job = DataJob.Idle) }

    private fun labelRes(kind: ExportKind) = when (kind) {
        ExportKind.WORKOUTS_CSV, ExportKind.MEASUREMENTS_CSV -> R.string.data_exporting
        ExportKind.BACKUP_ZIP -> R.string.data_backing_up
    }

    private companion object {
        const val TAG = "DataViewModel"
    }
}
