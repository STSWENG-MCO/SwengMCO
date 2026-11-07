package com.enil.logez.feature.measurements

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.ConfirmDialog
import com.enil.logez.core.designsystem.LineChart
import com.enil.logez.core.designsystem.LineChartPoint
import com.enil.logez.core.designsystem.LocalImage
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.domain.calc.BodyMeasurementMetric
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.calc.LengthDisplay
import com.enil.logez.core.domain.calc.MetricKind
import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.calc.visibleMetrics
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.MeasurementsTrackingMode
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.calc.valueFor
import com.enil.logez.core.domain.repository.BodyMeasurement
import com.enil.logez.core.domain.repository.ProgressPhoto
import com.enil.logez.feature.settings.SettingsRadioDialog
import com.enil.logez.feature.settings.SettingsValueRow
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.enil.logez.core.common.PermissionDenial
import com.enil.logez.core.common.openAppDetailsSettings
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalResources
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment

/**
 * PHASE2_PLAN.md §5.2 "Measurements" (Profile → Measurements). No overlay-camera compositing this
 * pass (Owner-approved scope cut) — the "+ photo" affordance opens a plain in-app camera preview
 * ([CameraCaptureScreen]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementsScreen(
    onBack: () -> Unit,
    onOpenCamera: () -> Unit,
    capturedPhotoPath: String?,
    onCapturedPhotoConsumed: () -> Unit,
    viewModel: MeasurementsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var entryDialogTarget by remember { mutableStateOf<EntryDialogTarget?>(null) }
    var deletingDate by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingPhoto by remember { mutableStateOf<ProgressPhoto?>(null) }
    var showTrackingModeDialog by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current

    // A camera denial used to do nothing at all: the tap just appeared to fail.
    val requestCameraPermission = rememberRequestCameraPermission(
        onGranted = onOpenCamera,
        onDenied = { denial ->
            scope.launch {
                val blocked = denial == PermissionDenial.Blocked
                val result = snackbarHostState.showSnackbar(
                    message = resources.getString(if (blocked) R.string.measurements_camera_blocked else R.string.measurements_camera_denied),
                    actionLabel = if (blocked) resources.getString(R.string.permission_open_settings) else null,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) openAppDetailsSettings(context)
            }
        },
    )

    LaunchedEffect(capturedPhotoPath) {
        if (capturedPhotoPath != null) {
            viewModel.attachPhotoFromCapture(Uri.parse(capturedPhotoPath))
            onCapturedPhotoConsumed()
        }
    }

    val captureFailedMessage = stringResource(R.string.measurements_capture_failed)
    LaunchedEffect(uiState.photoCaptureError) {
        if (uiState.photoCaptureError) {
            snackbarHostState.showSnackbar(captureFailedMessage)
            viewModel.dismissPhotoCaptureError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { Text(stringResource(R.string.measurements_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { entryDialogTarget = EntryDialogTarget(initial = null, defaultDate = LocalDate.now()) }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.measurements_add_entry))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "tracking_mode") {
                SettingsValueRow(
                    title = stringResource(R.string.measurements_tracking_mode),
                    value = trackingModeShortLabel(uiState.trackingMode),
                    onClick = { showTrackingModeDialog = true },
                )
            }
            item(key = "range") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md, vertical = Spacing.sm),
                ) {
                    ChartRange.entries.forEach { range ->
                        FilterChip(
                            selected = uiState.selectedRange == range,
                            onClick = { viewModel.selectRange(range) },
                            label = { Text(stringResource(range.labelRes())) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }
            item(key = "metrics") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md),
                ) {
                    visibleMetrics(uiState.trackingMode).forEach { metric ->
                        FilterChip(
                            selected = uiState.selectedMetric == metric,
                            onClick = { viewModel.selectMetric(metric) },
                            label = { Text(stringResource(metric.labelRes())) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }
            item(key = "chart") {
                if (uiState.chartPoints.isEmpty()) {
                    Text(
                        if (uiState.entries.isEmpty()) stringResource(R.string.measurements_empty_title) else stringResource(R.string.measurements_no_data_in_range),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xl),
                    )
                } else {
                    LineChart(
                        points = uiState.chartPoints.map { LineChartPoint(dateStringToEpochMillis(it.date), it.value) },
                        yLabel = { formatMetricValue(uiState.selectedMetric, it, uiState.weightUnit, uiState.lengthUnit) },
                        xLabel = { formatChartDate(it) },
                        selectedIndex = null,
                        onPointTap = {},
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                    )
                }
            }

            item(key = "photos_title") {
                Text(
                    stringResource(R.string.measurements_photos_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
            item(key = "photos") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                ) {
                    item(key = "add_photo") {
                        IconButton(onClick = { requestCameraPermission() }, modifier = Modifier.size(72.dp)) {
                            Icon(Icons.Outlined.AddAPhoto, contentDescription = stringResource(R.string.measurements_add_photo))
                        }
                    }
                    items(items = uiState.photos, key = { it.id }) { photo ->
                        Box {
                            LocalImage(
                                absolutePath = File(context.filesDir, photo.filePath).absolutePath,
                                contentDescription = photo.date,
                                modifier = Modifier.size(72.dp),
                            )
                            // 48dp touch target in the thumbnail's corner (was a 24dp button).
                            IconButton(onClick = { deletingPhoto = photo }, modifier = Modifier.align(Alignment.TopEnd).size(48.dp)) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                if (uiState.photos.isEmpty()) {
                    Text(
                        stringResource(R.string.measurements_photos_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                    )
                }
            }

            item(key = "entries_title") {
                Text(
                    stringResource(R.string.measurements_entries_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
            items(items = uiState.entries, key = { it.date }) { entry ->
                MeasurementEntryRow(
                    entry = entry,
                    metric = uiState.selectedMetric,
                    weightUnit = uiState.weightUnit,
                    lengthUnit = uiState.lengthUnit,
                    onClick = { entryDialogTarget = EntryDialogTarget(initial = entry, defaultDate = LocalDate.parse(entry.date)) },
                    onDelete = { deletingDate = entry.date },
                )
            }
        }
    }

    entryDialogTarget?.let { target ->
        MeasurementEntryDialog(
            initial = target.initial,
            defaultDate = target.defaultDate,
            weightUnit = uiState.weightUnit,
            lengthUnit = uiState.lengthUnit,
            trackingMode = uiState.trackingMode,
            onConfirm = { measurement -> viewModel.saveEntry(measurement); entryDialogTarget = null },
            onDismiss = { entryDialogTarget = null },
        )
    }

    if (showTrackingModeDialog) {
        SettingsRadioDialog(
            title = stringResource(R.string.measurements_tracking_mode),
            options = MeasurementsTrackingMode.entries,
            selected = uiState.trackingMode,
            optionLabel = { trackingModeDescriptiveLabel(it) },
            onSelect = { viewModel.setTrackingMode(it); showTrackingModeDialog = false },
            onDismiss = { showTrackingModeDialog = false },
        )
    }

    deletingDate?.let { date ->
        ConfirmDialog(
            onDismissRequest = { deletingDate = null },
            title = stringResource(R.string.measurements_delete_entry_title),
            body = stringResource(R.string.measurements_delete_entry_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deleteEntry(date) },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    deletingPhoto?.let { photo ->
        ConfirmDialog(
            onDismissRequest = { deletingPhoto = null },
            title = stringResource(R.string.measurements_delete_photo_title),
            body = stringResource(R.string.measurements_delete_photo_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.deletePhoto(photo) },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }

    // M22a one-per-day rule: replacing is the only way to add a second photo on the same date.
    uiState.pendingPhotoReplace?.let {
        ConfirmDialog(
            onDismissRequest = viewModel::cancelReplaceTodaysPhoto,
            title = stringResource(R.string.measurements_replace_photo_title),
            body = stringResource(R.string.measurements_replace_photo_body),
            confirmLabel = stringResource(R.string.measurements_replace_photo_confirm),
            onConfirm = viewModel::confirmReplaceTodaysPhoto,
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

private data class EntryDialogTarget(val initial: BodyMeasurement?, val defaultDate: LocalDate)

@Composable
private fun MeasurementEntryRow(
    entry: BodyMeasurement,
    metric: BodyMeasurementMetric,
    weightUnit: WeightUnit,
    lengthUnit: LengthUnit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val value = entry.valueFor(metric)
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        headlineContent = { Text(formatChartDate(dateStringToEpochMillis(entry.date))) },
        supportingContent = if (value != null) { { Text(formatMetricValue(metric, value, weightUnit, lengthUnit)) } } else null,
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

private fun dateStringToEpochMillis(date: String): Long =
    LocalDate.parse(date).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()

private fun formatChartDate(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate().format(DateTimeFormatter.ofPattern("d MMM"))

/** [value] is always the canonical stored value (kg/cm) -- converted to the current display unit here, once. */
private fun formatMetricValue(metric: BodyMeasurementMetric, value: Double, weightUnit: WeightUnit, lengthUnit: LengthUnit): String = when (metric.kind) {
    MetricKind.WEIGHT -> WeightDisplay.format(WeightDisplay.toDisplay(value, weightUnit))
    MetricKind.LENGTH -> LengthDisplay.format(LengthDisplay.toDisplay(value, lengthUnit))
    MetricKind.PERCENT -> WeightDisplay.format(value)
}

@Composable
private fun trackingModeShortLabel(mode: MeasurementsTrackingMode): String = when (mode) {
    MeasurementsTrackingMode.SIMPLIFIED -> stringResource(R.string.measurements_tracking_mode_simplified_short)
    MeasurementsTrackingMode.COMPLETE -> stringResource(R.string.measurements_tracking_mode_complete_short)
}

@Composable
private fun trackingModeDescriptiveLabel(mode: MeasurementsTrackingMode): String = when (mode) {
    MeasurementsTrackingMode.SIMPLIFIED -> stringResource(R.string.measurements_tracking_mode_simplified)
    MeasurementsTrackingMode.COMPLETE -> stringResource(R.string.measurements_tracking_mode_complete)
}

private fun ChartRange.labelRes(): Int = when (this) {
    ChartRange.LAST_30_DAYS -> R.string.chart_range_30d
    ChartRange.LAST_3_MONTHS -> R.string.chart_range_3m
    ChartRange.LAST_YEAR -> R.string.chart_range_1y
    ChartRange.ALL_TIME -> R.string.chart_range_all
}

private fun BodyMeasurementMetric.labelRes(): Int = when (this) {
    BodyMeasurementMetric.WEIGHT -> R.string.measurements_metric_weight
    BodyMeasurementMetric.LEAN_MASS -> R.string.measurements_metric_lean_mass
    BodyMeasurementMetric.FAT_PERCENT -> R.string.measurements_metric_fat_percent
    BodyMeasurementMetric.NECK -> R.string.measurements_metric_neck
    BodyMeasurementMetric.SHOULDER -> R.string.measurements_metric_shoulder
    BodyMeasurementMetric.CHEST -> R.string.measurements_metric_chest
    BodyMeasurementMetric.LEFT_BICEP -> R.string.measurements_metric_left_bicep
    BodyMeasurementMetric.RIGHT_BICEP -> R.string.measurements_metric_right_bicep
    BodyMeasurementMetric.LEFT_FOREARM -> R.string.measurements_metric_left_forearm
    BodyMeasurementMetric.RIGHT_FOREARM -> R.string.measurements_metric_right_forearm
    BodyMeasurementMetric.ABDOMEN -> R.string.measurements_metric_abdomen
    BodyMeasurementMetric.WAIST -> R.string.measurements_metric_waist
    BodyMeasurementMetric.HIPS -> R.string.measurements_metric_hips
    BodyMeasurementMetric.LEFT_THIGH -> R.string.measurements_metric_left_thigh
    BodyMeasurementMetric.RIGHT_THIGH -> R.string.measurements_metric_right_thigh
    BodyMeasurementMetric.LEFT_CALF -> R.string.measurements_metric_left_calf
    BodyMeasurementMetric.RIGHT_CALF -> R.string.measurements_metric_right_calf
}
