package com.enil.logez.feature.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.domain.calc.PlateCalculator
import com.enil.logez.core.designsystem.parseDecimalInput

/** Which add dialog is open, if any. */
private enum class PlateEquipmentDialog { ADD_BAR, ADD_PLATE }

/**
 * M17 Plate Equipment sub-page (§5.1.5's "Manage", relocated into the Settings tree): the bars and
 * plate denominations the Plate Calculator can load. Every add/remove persists instantly through
 * [SettingsViewModel] — no Save button, back = done, matching the rest of the tree. Adds are
 * validated (> 0), rounded to the calculator's quarter-kg grid, and deduplicated; the last bar is
 * not removable (its remove control disappears — a calculator without a bar is meaningless).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateEquipmentScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val equipment = settings.plateEquipment
    var openDialog by remember { mutableStateOf<PlateEquipmentDialog?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { ScreenTitle(stringResource(R.string.settings_plate_equipment_row)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "section_bars") { SettingsSectionHeader(stringResource(R.string.settings_plate_bars_section)) }
            items(count = equipment.barsKg.size, key = { "bar_${equipment.barsKg[it]}" }) { index ->
                val bar = equipment.barsKg[index]
                EquipmentWeightRow(
                    label = formatEquipmentKg(bar),
                    removable = equipment.barsKg.size > 1,
                    onRemove = { viewModel.removeBar(bar) },
                )
            }
            item(key = "add_bar") {
                TextButton(onClick = { openDialog = PlateEquipmentDialog.ADD_BAR }, modifier = Modifier.padding(horizontal = Spacing.xs)) {
                    Text(stringResource(R.string.settings_plate_add_bar))
                }
            }
            item(key = "last_bar_note") {
                Text(
                    stringResource(R.string.settings_plate_last_bar_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }

            item(key = "section_plates") { SettingsSectionHeader(stringResource(R.string.settings_plate_plates_section)) }
            items(count = equipment.platesKg.size, key = { "plate_${equipment.platesKg[it]}" }) { index ->
                val plate = equipment.platesKg[index]
                EquipmentWeightRow(
                    label = formatEquipmentKg(plate),
                    removable = true,
                    onRemove = { viewModel.removePlate(plate) },
                )
            }
            item(key = "add_plate") {
                TextButton(onClick = { openDialog = PlateEquipmentDialog.ADD_PLATE }, modifier = Modifier.padding(horizontal = Spacing.xs)) {
                    Text(stringResource(R.string.settings_plate_add_plate))
                }
            }
            item(key = "rounding_note") {
                Text(
                    stringResource(R.string.settings_plate_rounding_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
    }

    when (openDialog) {
        PlateEquipmentDialog.ADD_BAR -> AddWeightDialog(
            title = stringResource(R.string.settings_plate_add_bar),
            existing = equipment.barsKg,
            onConfirm = { viewModel.addBar(it); openDialog = null },
            onDismiss = { openDialog = null },
        )
        PlateEquipmentDialog.ADD_PLATE -> AddWeightDialog(
            title = stringResource(R.string.settings_plate_add_plate),
            existing = equipment.platesKg,
            onConfirm = { viewModel.addPlate(it); openDialog = null },
            onDismiss = { openDialog = null },
        )
        null -> Unit
    }
}

/** One bar/plate row: the weight, plus a remove control unless the invariant forbids it. */
@Composable
private fun EquipmentWeightRow(label: String, removable: Boolean, onRemove: () -> Unit) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = { Text(label) },
        trailingContent = {
            if (removable) {
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.settings_plate_remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
    HorizontalDivider()
}

/**
 * Numeric-input dialog for a new bar or plate. Validation mirrors what the persist path enforces
 * (positive, quarter-kg rounded, no duplicates) so Add is only enabled when the write would
 * actually change the list — a disabled Add plus the inline reason beats a silent no-op.
 */
@Composable
private fun AddWeightDialog(
    title: String,
    existing: List<Double>,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val rounded = parseDecimalInput(text)?.let { PlateCalculator.roundToQuarterKg(it) }
    val error = when {
        text.isEmpty() -> null
        rounded == null || rounded <= 0.0 -> stringResource(R.string.settings_plate_invalid_weight)
        rounded in existing -> stringResource(R.string.settings_plate_duplicate_weight)
        else -> null
    }
    val valid = rounded != null && rounded > 0.0 && rounded !in existing

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.settings_plate_weight_label)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: stringResource(R.string.settings_plate_rounding_note)) },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { rounded?.let(onConfirm) }, enabled = valid) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** "20 kg", "1.25 kg" — the editor is kg-native (plates are physical kg denominations). */
private fun formatEquipmentKg(kg: Double): String {
    val number = if (kg == Math.floor(kg)) kg.toLong().toString() else "%.2f".format(java.util.Locale.ROOT, kg).trimEnd('0').trimEnd('.')
    return "$number kg"
}
