package com.enil.logez.feature.measurements

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.enil.logez.R
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.calc.LengthDisplay
import com.enil.logez.core.domain.calc.WeightDisplay
import com.enil.logez.core.domain.model.LengthUnit
import com.enil.logez.core.domain.model.MeasurementsTrackingMode
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.BodyMeasurement
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import com.enil.logez.core.designsystem.parseDecimalInput

/**
 * Add/edit sheet for one date's body-measurement entry — every field optional (any subset
 * fillable per §5.2), so the only validation error is unparseable non-blank text, never "empty."
 * [initial] non-null locks the date (it's the row's own primary key); null means adding a new
 * entry, backdatable via the date field.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasurementEntryDialog(
    initial: BodyMeasurement?,
    defaultDate: LocalDate,
    weightUnit: WeightUnit,
    lengthUnit: LengthUnit,
    trackingMode: MeasurementsTrackingMode,
    onConfirm: (BodyMeasurement) -> Unit,
    onDismiss: () -> Unit,
) {
    var date by remember { mutableStateOf(initial?.date?.let { LocalDate.parse(it) } ?: defaultDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    var weightText by remember { mutableStateOf(initial?.weightKg.toFieldText { WeightDisplay.toDisplay(it, weightUnit) }) }
    var leanMassText by remember { mutableStateOf(initial?.leanMassKg.toFieldText { WeightDisplay.toDisplay(it, weightUnit) }) }
    var fatPercentText by remember { mutableStateOf(initial?.fatPercent.toFieldText()) }
    var neckText by remember { mutableStateOf(initial?.neckCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }
    var shoulderText by remember { mutableStateOf(initial?.shoulderCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }
    var chestText by remember { mutableStateOf(initial?.chestCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }
    var leftBicepText by remember { mutableStateOf(initial?.leftBicepCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }
    var rightBicepText by remember { mutableStateOf(initial?.rightBicepCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }
    var leftForearmText by remember { mutableStateOf(initial?.leftForearmCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }
    var rightForearmText by remember { mutableStateOf(initial?.rightForearmCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }
    var abdomenText by remember { mutableStateOf(initial?.abdomenCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }
    var waistText by remember { mutableStateOf(initial?.waistCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }
    var hipsText by remember { mutableStateOf(initial?.hipsCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }
    var leftThighText by remember { mutableStateOf(initial?.leftThighCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }
    var rightThighText by remember { mutableStateOf(initial?.rightThighCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }
    var leftCalfText by remember { mutableStateOf(initial?.leftCalfCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }
    var rightCalfText by remember { mutableStateOf(initial?.rightCalfCm.toFieldText { LengthDisplay.toDisplay(it, lengthUnit) }) }

    val weight = parseDecimalInput(weightText)
    val leanMass = parseDecimalInput(leanMassText)
    val fatPercent = parseDecimalInput(fatPercentText)
    val neck = parseDecimalInput(neckText)
    val shoulder = parseDecimalInput(shoulderText)
    val chest = parseDecimalInput(chestText)
    val leftBicep = parseDecimalInput(leftBicepText)
    val rightBicep = parseDecimalInput(rightBicepText)
    val leftForearm = parseDecimalInput(leftForearmText)
    val rightForearm = parseDecimalInput(rightForearmText)
    val abdomen = parseDecimalInput(abdomenText)
    val waist = parseDecimalInput(waistText)
    val hips = parseDecimalInput(hipsText)
    val leftThigh = parseDecimalInput(leftThighText)
    val rightThigh = parseDecimalInput(rightThighText)
    val leftCalf = parseDecimalInput(leftCalfText)
    val rightCalf = parseDecimalInput(rightCalfText)

    // Blank is always valid (every field is optional) -- only unparseable, non-blank text errors.
    val fieldsAndValues = listOf(
        weightText to weight, leanMassText to leanMass, fatPercentText to fatPercent,
        neckText to neck, shoulderText to shoulder, chestText to chest,
        leftBicepText to leftBicep, rightBicepText to rightBicep,
        leftForearmText to leftForearm, rightForearmText to rightForearm,
        abdomenText to abdomen, waistText to waist, hipsText to hips,
        leftThighText to leftThigh, rightThighText to rightThigh,
        leftCalfText to leftCalf, rightCalfText to rightCalf,
    )
    val hasError = fieldsAndValues.any { (text, value) -> text.isNotEmpty() && value == null }
    val errorText = stringResource(R.string.measurements_invalid_number)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.measurements_add_entry else R.string.measurements_edit_entry)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = date.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                    onValueChange = {},
                    readOnly = true,
                    enabled = initial == null,
                    label = { Text(stringResource(R.string.measurements_date_label)) },
                    modifier = Modifier.fillMaxWidth().let { if (initial == null) it.padding(bottom = Spacing.xs) else it },
                )
                if (initial == null) {
                    TextButton(onClick = { showDatePicker = true }) { Text(stringResource(R.string.measurements_change_date)) }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
                Text(stringResource(R.string.measurements_section_weight), style = MaterialTheme.typography.labelLarge)
                MeasurementNumberField(stringResource(weightLabelRes(weightUnit)), weightText, { weightText = it }, weightText.isNotEmpty() && weight == null, errorText)
                MeasurementNumberField(stringResource(leanMassLabelRes(weightUnit)), leanMassText, { leanMassText = it }, leanMassText.isNotEmpty() && leanMass == null, errorText)
                MeasurementNumberField(stringResource(R.string.measurements_fat_percent_label), fatPercentText, { fatPercentText = it }, fatPercentText.isNotEmpty() && fatPercent == null, errorText)
                // Circumference fields' state/computation above and their inclusion in
                // fieldsAndValues/hasError below stay unconditional regardless of trackingMode --
                // only this section's rendering is gated, so a Simplified-mode edit of an entry
                // that already has circumference data carries it through unchanged on save
                // instead of silently nulling it out.
                if (trackingMode == MeasurementsTrackingMode.COMPLETE) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
                    Text(stringResource(R.string.measurements_section_circumferences), style = MaterialTheme.typography.labelLarge)
                    val lengthLabel = stringResource(lengthLabelRes(lengthUnit))
                    MeasurementNumberField(stringResource(R.string.measurements_neck_label, lengthLabel), neckText, { neckText = it }, neckText.isNotEmpty() && neck == null, errorText)
                    MeasurementNumberField(stringResource(R.string.measurements_shoulder_label, lengthLabel), shoulderText, { shoulderText = it }, shoulderText.isNotEmpty() && shoulder == null, errorText)
                    MeasurementNumberField(stringResource(R.string.measurements_chest_label, lengthLabel), chestText, { chestText = it }, chestText.isNotEmpty() && chest == null, errorText)
                    MeasurementNumberField(stringResource(R.string.measurements_left_bicep_label, lengthLabel), leftBicepText, { leftBicepText = it }, leftBicepText.isNotEmpty() && leftBicep == null, errorText)
                    MeasurementNumberField(stringResource(R.string.measurements_right_bicep_label, lengthLabel), rightBicepText, { rightBicepText = it }, rightBicepText.isNotEmpty() && rightBicep == null, errorText)
                    MeasurementNumberField(stringResource(R.string.measurements_left_forearm_label, lengthLabel), leftForearmText, { leftForearmText = it }, leftForearmText.isNotEmpty() && leftForearm == null, errorText)
                    MeasurementNumberField(stringResource(R.string.measurements_right_forearm_label, lengthLabel), rightForearmText, { rightForearmText = it }, rightForearmText.isNotEmpty() && rightForearm == null, errorText)
                    MeasurementNumberField(stringResource(R.string.measurements_abdomen_label, lengthLabel), abdomenText, { abdomenText = it }, abdomenText.isNotEmpty() && abdomen == null, errorText)
                    MeasurementNumberField(stringResource(R.string.measurements_waist_label, lengthLabel), waistText, { waistText = it }, waistText.isNotEmpty() && waist == null, errorText)
                    MeasurementNumberField(stringResource(R.string.measurements_hips_label, lengthLabel), hipsText, { hipsText = it }, hipsText.isNotEmpty() && hips == null, errorText)
                    MeasurementNumberField(stringResource(R.string.measurements_left_thigh_label, lengthLabel), leftThighText, { leftThighText = it }, leftThighText.isNotEmpty() && leftThigh == null, errorText)
                    MeasurementNumberField(stringResource(R.string.measurements_right_thigh_label, lengthLabel), rightThighText, { rightThighText = it }, rightThighText.isNotEmpty() && rightThigh == null, errorText)
                    MeasurementNumberField(stringResource(R.string.measurements_left_calf_label, lengthLabel), leftCalfText, { leftCalfText = it }, leftCalfText.isNotEmpty() && leftCalf == null, errorText)
                    MeasurementNumberField(stringResource(R.string.measurements_right_calf_label, lengthLabel), rightCalfText, { rightCalfText = it }, rightCalfText.isNotEmpty() && rightCalf == null, errorText)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !hasError,
                onClick = {
                    onConfirm(
                        BodyMeasurement(
                            date = date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                            weightKg = weight?.let { WeightDisplay.toKg(it, weightUnit) },
                            leanMassKg = leanMass?.let { WeightDisplay.toKg(it, weightUnit) },
                            fatPercent = fatPercent,
                            neckCm = neck?.let { LengthDisplay.toCm(it, lengthUnit) },
                            shoulderCm = shoulder?.let { LengthDisplay.toCm(it, lengthUnit) },
                            chestCm = chest?.let { LengthDisplay.toCm(it, lengthUnit) },
                            leftBicepCm = leftBicep?.let { LengthDisplay.toCm(it, lengthUnit) },
                            rightBicepCm = rightBicep?.let { LengthDisplay.toCm(it, lengthUnit) },
                            leftForearmCm = leftForearm?.let { LengthDisplay.toCm(it, lengthUnit) },
                            rightForearmCm = rightForearm?.let { LengthDisplay.toCm(it, lengthUnit) },
                            abdomenCm = abdomen?.let { LengthDisplay.toCm(it, lengthUnit) },
                            waistCm = waist?.let { LengthDisplay.toCm(it, lengthUnit) },
                            hipsCm = hips?.let { LengthDisplay.toCm(it, lengthUnit) },
                            leftThighCm = leftThigh?.let { LengthDisplay.toCm(it, lengthUnit) },
                            rightThighCm = rightThigh?.let { LengthDisplay.toCm(it, lengthUnit) },
                            leftCalfCm = leftCalf?.let { LengthDisplay.toCm(it, lengthUnit) },
                            rightCalfCm = rightCalf?.let { LengthDisplay.toCm(it, lengthUnit) },
                            updatedAt = 0L, // overwritten by MeasurementsViewModel.saveEntry with the real clock value
                        ),
                    )
                },
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { picked ->
                        date = Instant.ofEpochMilli(picked).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@Composable
private fun MeasurementNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    errorText: String,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        isError = isError,
        supportingText = if (isError) { { Text(errorText) } } else null,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
    )
}

private fun weightLabelRes(unit: WeightUnit) = if (unit == WeightUnit.KG) R.string.measurements_weight_label_kg else R.string.measurements_weight_label_lb
private fun leanMassLabelRes(unit: WeightUnit) = if (unit == WeightUnit.KG) R.string.measurements_lean_mass_label_kg else R.string.measurements_lean_mass_label_lb
private fun lengthLabelRes(unit: LengthUnit) = if (unit == LengthUnit.CM) R.string.measurements_unit_cm else R.string.measurements_unit_in

/** Converts a canonical stored value (kg/cm) to the display unit and formats it for a text field, or "" if absent. */
private fun Double?.toFieldText(toDisplay: (Double) -> Double = { it }): String =
    this?.let { toDisplay(it) }?.let { if (it == Math.floor(it)) it.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, it) }.orEmpty()
