package com.enil.logez.feature.settings

import android.content.Intent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.PreviousValuesMode
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.feature.privacy.PrivacyPolicyActivity
import java.time.DayOfWeek
import java.time.format.TextStyle
import com.enil.logez.BuildConfig
import androidx.compose.runtime.saveable.rememberSaveable
import com.enil.logez.core.designsystem.Spacing
import androidx.compose.material3.MaterialTheme
import com.enil.logez.feature.workout.hasNotificationPermission
import com.enil.logez.feature.workout.openAppNotificationSettings
import androidx.lifecycle.compose.LifecycleResumeEffect

/** Which selection dialog is open, if any. One at a time — each row opens its own. */
private enum class SettingsDialog { WEIGHT_UNIT, DISTANCE_UNIT, LENGTH_UNIT, BODY_DIAGRAM_VARIANT, WEEKLY_ACTIVE_DAY_TARGET, FIRST_DAY, REST_TIMER, PREVIOUS_VALUES, MAX_HEART_RATE }

/**
 * M16 Workout Settings (PHASE2_PLAN.md §5.2 Settings tree). Every editor writes through the
 * repository the moment it changes — no Save button, back = done. The Calculators section's two
 * toggles persist real fields today but gate nothing visible until M17 (plate calculator) and
 * M18 (warm-up generator) land.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onSoundsClick: () -> Unit,
    onPlateEquipmentClick: () -> Unit,
    onWarmupSetsClick: () -> Unit,
    onDataClick: () -> Unit,
    onLicensesClick: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Saveable (2026-09-25): an open dialog used to vanish on rotation or process death.
    var openDialog by rememberSaveable { mutableStateOf<SettingsDialog?>(null) }
    val context = LocalContext.current
    // Re-checked on every resume, so the row disappears as soon as the user turns notifications on.
    var notificationsAllowed by remember { mutableStateOf(hasNotificationPermission(context)) }
    LifecycleResumeEffect(Unit) {
        notificationsAllowed = hasNotificationPermission(context)
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { ScreenTitle(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            // --- PREFERENCES ---
            item(key = "section_preferences") { SettingsSectionHeader(stringResource(R.string.settings_section_preferences)) }
            item(key = "weight_unit") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_weight_unit),
                    value = weightUnitShortLabel(settings.weightUnit),
                    onClick = { openDialog = SettingsDialog.WEIGHT_UNIT },
                )
            }
            item(key = "distance_unit") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_distance_unit),
                    value = distanceUnitShortLabel(settings.distanceUnit),
                    onClick = { openDialog = SettingsDialog.DISTANCE_UNIT },
                )
            }
            item(key = "length_unit") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_length_unit),
                    value = lengthUnitShortLabel(settings.lengthUnit),
                    onClick = { openDialog = SettingsDialog.LENGTH_UNIT },
                )
            }
            item(key = "body_diagram_variant") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_body_diagram_variant),
                    value = bodyDiagramVariantShortLabel(settings.muscleDiagramVariant),
                    onClick = { openDialog = SettingsDialog.BODY_DIAGRAM_VARIANT },
                )
            }
            item(key = "weekly_active_day_target") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_weekly_active_day_target),
                    value = pluralStringResource(R.plurals.settings_weekly_active_day_target_value, settings.weeklyActiveDayTarget, settings.weeklyActiveDayTarget),
                    onClick = { openDialog = SettingsDialog.WEEKLY_ACTIVE_DAY_TARGET },
                )
            }
            item(key = "first_day") {
                SettingsValueRow(
                    title = stringResource(R.string.calendar_first_day_of_week),
                    value = settings.firstDayOfWeek.getDisplayName(TextStyle.FULL, currentLocale()),
                    onClick = { openDialog = SettingsDialog.FIRST_DAY },
                )
            }

            // --- WORKOUTS ---
            item(key = "section_workouts") { SettingsSectionHeader(stringResource(R.string.settings_section_workouts)) }
            item(key = "rest_timer") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_rest_timer),
                    subtitle = stringResource(R.string.settings_rest_timer_subtitle),
                    value = restTimerLabel(settings.defaultRestTimerSeconds),
                    onClick = { openDialog = SettingsDialog.REST_TIMER },
                )
            }
            item(key = "previous_values") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_previous_values),
                    subtitle = stringResource(R.string.settings_previous_values_subtitle),
                    value = previousValuesLabel(settings.previousValuesMode),
                    onClick = { openDialog = SettingsDialog.PREVIOUS_VALUES },
                )
            }
            item(key = "keep_awake") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_keep_awake),
                    subtitle = stringResource(R.string.settings_keep_awake_subtitle),
                    checked = settings.keepAwake,
                    onCheckedChange = viewModel::setKeepAwake,
                )
            }
            item(key = "smart_superset") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_smart_superset),
                    subtitle = stringResource(R.string.settings_smart_superset_subtitle),
                    checked = settings.smartSupersetScrolling,
                    onCheckedChange = viewModel::setSmartSupersetScrolling,
                )
            }
            item(key = "inline_timer") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_inline_timer),
                    subtitle = stringResource(R.string.settings_inline_timer_subtitle),
                    checked = settings.inlineTimerEnabled,
                    onCheckedChange = viewModel::setInlineTimerEnabled,
                )
            }
            item(key = "live_pr") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_live_pr),
                    subtitle = stringResource(R.string.settings_live_pr_subtitle),
                    checked = settings.livePrNotificationEnabled,
                    onCheckedChange = viewModel::setLivePrNotificationEnabled,
                )
            }
            if (!notificationsAllowed) {
                item(key = "notifications_off") {
                    SettingsValueRow(
                        title = stringResource(R.string.settings_notifications_row),
                        subtitle = stringResource(R.string.workout_notification_permission_denied_hint),
                        value = stringResource(R.string.workout_notification_permission_denied_settings),
                        onClick = { openAppNotificationSettings(context) },
                    )
                }
            }
            item(key = "max_heart_rate") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_max_heart_rate),
                    subtitle = stringResource(R.string.settings_max_heart_rate_subtitle),
                    value = settings.maxHeartRateBpm?.let { stringResource(R.string.settings_max_heart_rate_value, it) }
                        ?: stringResource(R.string.settings_max_heart_rate_not_set),
                    onClick = { openDialog = SettingsDialog.MAX_HEART_RATE },
                )
            }
            item(key = "rpe_tracking") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_rpe),
                    subtitle = stringResource(R.string.settings_rpe_subtitle),
                    checked = settings.rpeTrackingEnabled,
                    onCheckedChange = viewModel::setRpeTrackingEnabled,
                )
            }
            item(key = "include_warmups") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_include_warmups),
                    subtitle = stringResource(R.string.settings_include_warmups_subtitle),
                    checked = settings.includeWarmupsInStats,
                    onCheckedChange = viewModel::setIncludeWarmupsInStats,
                )
            }
            item(key = "show_heatmap") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_show_heatmap),
                    subtitle = stringResource(R.string.settings_show_heatmap_subtitle),
                    checked = settings.showHeatmap,
                    onCheckedChange = viewModel::setShowHeatmap,
                )
            }
            item(key = "show_goals") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_show_goals),
                    subtitle = stringResource(R.string.settings_show_goals_subtitle),
                    checked = settings.showGoals,
                    onCheckedChange = viewModel::setShowGoals,
                )
            }

            // --- CALCULATORS (persist today; the visible affordances land in M17/M18) ---
            item(key = "section_calculators") { SettingsSectionHeader(stringResource(R.string.settings_section_calculators)) }
            item(key = "plate_calculator") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_plate_calculator),
                    subtitle = stringResource(R.string.settings_plate_calculator_subtitle),
                    checked = settings.plateCalculatorEnabled,
                    onCheckedChange = viewModel::setPlateCalculatorEnabled,
                )
            }
            item(key = "plate_equipment") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_plate_equipment_row),
                    subtitle = stringResource(R.string.settings_plate_equipment_subtitle),
                    value = stringResource(
                        R.string.settings_plate_equipment_value,
                        pluralStringResource(R.plurals.settings_plate_bar_count, settings.plateEquipment.barsKg.size, settings.plateEquipment.barsKg.size),
                        pluralStringResource(R.plurals.settings_plate_plate_count, settings.plateEquipment.platesKg.size, settings.plateEquipment.platesKg.size),
                    ),
                    onClick = onPlateEquipmentClick,
                )
            }
            item(key = "warmup_calculator") {
                SettingsToggleRow(
                    title = stringResource(R.string.settings_warmup_calculator),
                    subtitle = stringResource(R.string.settings_warmup_calculator_subtitle),
                    checked = settings.warmupCalculatorEnabled,
                    onCheckedChange = viewModel::setWarmupCalculatorEnabled,
                )
            }
            item(key = "warmup_method") {
                // M18: value-preview of the persisted ladder ("40% ×5 · 60% ×5 · 80% ×3") — percent
                // rows are unit-less by construction, so nothing here needs kg/lb conversion.
                SettingsValueRow(
                    title = stringResource(R.string.settings_warmup_method_row),
                    subtitle = stringResource(R.string.settings_warmup_method_subtitle),
                    value = settings.warmupMethod
                        .map { stringResource(R.string.settings_warmup_step_value, it.displayPercent(), it.reps) }
                        .joinToString(" · "),
                    onClick = onWarmupSetsClick,
                )
            }

            // --- SOUNDS ---
            item(key = "section_sounds") { SettingsSectionHeader(stringResource(R.string.settings_section_sounds)) }
            item(key = "sounds_nav") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_sounds_row),
                    subtitle = stringResource(R.string.settings_sounds_row_subtitle),
                    value = "",
                    onClick = onSoundsClick,
                )
            }

            // --- ABOUT ---
            item(key = "section_data") { SettingsSectionHeader(stringResource(R.string.settings_section_data)) }
            item(key = "data_nav") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_data_row),
                    value = "",
                    onClick = onDataClick,
                )
            }

            item(key = "section_about") { SettingsSectionHeader(stringResource(R.string.settings_section_about)) }
            item(key = "app_version") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_version_row),
                    value = BuildConfig.VERSION_NAME,
                    onClick = {},
                )
            }
            // Hidden until the public contact address is configured (gradle.properties).
            if (BuildConfig.CONTACT_EMAIL.isNotBlank()) {
                item(key = "send_feedback") {
                    SettingsValueRow(
                        title = stringResource(R.string.settings_feedback_row),
                        subtitle = stringResource(R.string.settings_feedback_subtitle),
                        value = "",
                        onClick = { sendFeedbackEmail(context) },
                    )
                }
            }
            item(key = "privacy_policy") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_privacy_policy_row),
                    value = "",
                    onClick = { context.startActivity(Intent(context, PrivacyPolicyActivity::class.java)) },
                )
            }
            item(key = "licenses") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_licenses_row),
                    subtitle = stringResource(R.string.settings_licenses_subtitle),
                    value = "",
                    onClick = onLicensesClick,
                )
            }
            item(key = "health_disclaimer") {
                Text(
                    stringResource(R.string.settings_health_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.md),
                )
            }
        }
    }

    when (openDialog) {
        SettingsDialog.WEIGHT_UNIT -> SettingsRadioDialog(
            title = stringResource(R.string.settings_weight_unit),
            options = WeightUnit.entries,
            selected = settings.weightUnit,
            optionLabel = { weightUnitLabel(it) },
            onSelect = viewModel::setWeightUnit,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.DISTANCE_UNIT -> SettingsRadioDialog(
            title = stringResource(R.string.settings_distance_unit),
            options = DistanceUnit.entries,
            selected = settings.distanceUnit,
            optionLabel = { distanceUnitLabel(it) },
            onSelect = viewModel::setDistanceUnit,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.LENGTH_UNIT -> SettingsRadioDialog(
            title = stringResource(R.string.settings_length_unit),
            options = LengthUnit.entries,
            selected = settings.lengthUnit,
            optionLabel = { lengthUnitLabel(it) },
            onSelect = viewModel::setLengthUnit,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.BODY_DIAGRAM_VARIANT -> SettingsRadioDialog(
            title = stringResource(R.string.settings_body_diagram_variant),
            options = MuscleDiagramVariant.entries,
            selected = settings.muscleDiagramVariant,
            optionLabel = { bodyDiagramVariantLabel(it) },
            onSelect = viewModel::setMuscleDiagramVariant,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.WEEKLY_ACTIVE_DAY_TARGET -> SettingsRadioDialog(
            title = stringResource(R.string.settings_weekly_active_day_target),
            // 1-7: a seven-day target is reachable but not the point -- rest days are training,
            // which is the whole reason the widget counts days per week rather than a daily streak.
            options = (1..7).toList(),
            selected = settings.weeklyActiveDayTarget,
            optionLabel = { pluralStringResource(R.plurals.settings_weekly_active_day_target_value, it, it) },
            onSelect = viewModel::setWeeklyActiveDayTarget,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.FIRST_DAY -> SettingsRadioDialog(
            title = stringResource(R.string.calendar_first_day_of_week),
            // §5.2 lists exactly these three — the conventional week starts (same trio as the
            // Calendar screen's picker), not all seven days.
            options = listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            selected = settings.firstDayOfWeek,
            optionLabel = { it.getDisplayName(TextStyle.FULL, currentLocale()) },
            onSelect = viewModel::setFirstDayOfWeek,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.REST_TIMER -> SettingsRadioDialog(
            title = stringResource(R.string.settings_rest_timer),
            // 0 = off (spine convention shared with RestTimerPickerSheet), then the common gym
            // rest lengths — not the routine picker's full 5s-step list, which would be 61 rows.
            options = listOf(0, 30, 45, 60, 90, 120, 150, 180, 240, 300),
            selected = settings.defaultRestTimerSeconds,
            optionLabel = { restTimerLabel(it) },
            onSelect = viewModel::setDefaultRestTimerSeconds,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.PREVIOUS_VALUES -> SettingsRadioDialog(
            title = stringResource(R.string.settings_previous_values),
            options = PreviousValuesMode.entries,
            selected = settings.previousValuesMode,
            optionLabel = { previousValuesLabel(it) },
            onSelect = viewModel::setPreviousValuesMode,
            onDismiss = { openDialog = null },
        )
        SettingsDialog.MAX_HEART_RATE -> MaxHeartRateDialog(
            initial = settings.maxHeartRateBpm,
            onSave = viewModel::setMaxHeartRateBpm,
            onDismiss = { openDialog = null },
        )
        null -> Unit
    }
}

/**
 * Free-text bpm entry — unlike every other row on this screen, there's no small fixed option set
 * to pick from (a `SettingsRadioDialog` doesn't fit), so this is a plain Material3 `AlertDialog`
 * with one numeric field, matching `WarmupSetsScreen`'s/`PlateEquipmentScreen`'s existing
 * `OutlinedTextField(keyboardType = Number)` convention rather than inventing a new generic
 * input-dialog component for this one field. Blank clears the setting (zones simply stop
 * rendering, same graceful-degrade rule as everywhere else); a non-numeric or out-of-range entry
 * disables Save rather than persisting nonsense.
 */
@Composable
private fun MaxHeartRateDialog(initial: Int?, onSave: (Int?) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial?.toString().orEmpty()) }
    val parsed = text.toIntOrNull()
    val isError = text.isNotEmpty() && (parsed == null || parsed !in 1..300)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_max_heart_rate_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.settings_max_heart_rate_field_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = isError,
                supportingText = if (isError) {
                    { Text(stringResource(R.string.settings_max_heart_rate_invalid)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.ifEmpty { null }?.toIntOrNull()); onDismiss() }, enabled = !isError) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun weightUnitLabel(unit: WeightUnit): String = when (unit) {
    WeightUnit.KG -> stringResource(R.string.settings_weight_unit_kg)
    WeightUnit.LB -> stringResource(R.string.settings_weight_unit_lb)
}

/** Matches the lowercase "kg"/"lb" used everywhere values render (PreviousValueFormatter etc.). */
private fun weightUnitShortLabel(unit: WeightUnit): String = if (unit == WeightUnit.KG) "kg" else "lb"

@Composable
private fun distanceUnitLabel(unit: DistanceUnit): String = when (unit) {
    DistanceUnit.KM -> stringResource(R.string.settings_distance_unit_km)
    DistanceUnit.MILES -> stringResource(R.string.settings_distance_unit_miles)
}

private fun distanceUnitShortLabel(unit: DistanceUnit): String = if (unit == DistanceUnit.KM) "km" else "mi"

@Composable
private fun lengthUnitLabel(unit: LengthUnit): String = when (unit) {
    LengthUnit.CM -> stringResource(R.string.settings_length_unit_cm)
    LengthUnit.IN -> stringResource(R.string.settings_length_unit_in)
}

/** Matches the lowercase "cm"/"in" used everywhere values render. */
private fun lengthUnitShortLabel(unit: LengthUnit): String = if (unit == LengthUnit.CM) "cm" else "in"

@Composable
private fun bodyDiagramVariantLabel(variant: MuscleDiagramVariant): String = when (variant) {
    MuscleDiagramVariant.MALE -> stringResource(R.string.settings_body_diagram_variant_male)
    MuscleDiagramVariant.FEMALE -> stringResource(R.string.settings_body_diagram_variant_female)
}

@Composable
private fun bodyDiagramVariantShortLabel(variant: MuscleDiagramVariant): String = bodyDiagramVariantLabel(variant)

@Composable
private fun previousValuesLabel(mode: PreviousValuesMode): String = when (mode) {
    PreviousValuesMode.ANY_WORKOUT -> stringResource(R.string.settings_previous_values_any)
    PreviousValuesMode.SAME_ROUTINE -> stringResource(R.string.settings_previous_values_same_routine)
}

/** "Off" for 0, otherwise m:ss — the same rendering the routine rest-timer picker uses. */
@Composable
internal fun restTimerLabel(totalSeconds: Int): String {
    if (totalSeconds == 0) return stringResource(R.string.rest_timer_off_option)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * Opens the user's email app addressed to the developer, with the app and Android versions filled
 * in so a tester's report says which build it is about. No attachment, no logs: nothing leaves the
 * phone unless the user writes and sends the email themselves.
 */
private fun sendFeedbackEmail(context: android.content.Context) {
    val subject = "LogEZ feedback (${BuildConfig.VERSION_NAME}, Android ${android.os.Build.VERSION.RELEASE})"
    val intent = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:")).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.CONTACT_EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    runCatching { context.startActivity(intent) }
}
