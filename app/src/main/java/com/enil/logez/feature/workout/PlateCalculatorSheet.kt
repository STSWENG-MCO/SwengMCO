package com.enil.logez.feature.workout

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.calc.PlateCalculator
import com.enil.logez.core.domain.model.PlateEquipment
import com.enil.logez.core.domain.model.WeightUnit
import com.skydoves.flexible.bottomsheet.material3.FlexibleBottomSheet
import com.skydoves.flexible.core.FlexibleSheetSize
import com.skydoves.flexible.core.rememberFlexibleBottomSheetState
import com.enil.logez.core.designsystem.parseDecimalInput

/**
 * §5.1.5 Plate Calculator sheet (M17). Deliberately thin — every solve goes through the tested
 * [PlateCalculator]; this composable only converts between the display unit and canonical kg and
 * renders the result. The solve itself always runs in kg: when the user's unit is LB, the target
 * field displays and accepts pounds, but [onApply] still hands back the canonical kg total.
 *
 * §5.1.5's Canvas bar-loading diagram is deliberately not built (M17 keeps the text list + totals;
 * the diagram is pure decoration over the same numbers and can land later without data changes).
 *
 * M20d: rebuilt on FlexibleBottomSheet, hoisted to screen level ([WorkoutLoggerScreen]'s
 * `plateTarget`) so there is exactly one sheet instance shared by every row, in both the regular
 * and circuit tables — replacing the old per-row `ModalBottomSheet`. Non-modal (`isModal = false`,
 * the milestone's actual goal): the set list stays interactive behind the sheet, verified
 * on-device — completing a set behind the open sheet registered correctly, and the sheet reopened
 * pre-filled from the newly-applied weight.
 *
 * A suspected IME rendering bug ("field focuses, `mInputShown=true`, but no on-screen keyboard
 * ever renders") led to a brief detour to `isModal = true` mid-milestone -- a follow-up control
 * test then reproduced the *identical* symptom on the app's own, unrelated, pre-existing
 * `ExercisePickerSheet` (a plain Material3 `ModalBottomSheet`, `feature/exercises/
 * ExercisePickerSheet.kt`) under the same host-load-starved emulator session (`uptime` load
 * average 12-14 on an 8-core host), and force-stopping/restarting the Gboard IME process did not
 * clear it either. That rules out FlexibleBottomSheet specifically -- reverted back to non-modal.
 *
 * Non-modal cuts both ways: because the set list behind the sheet stays tappable, a user can
 * retarget it to a *different* set's calculator icon without dismissing the open one first. The
 * sheet stays mounted across that (`WorkoutLoggerScreen`'s `plateTarget?.let { ... }` recomposes
 * the same call site rather than tearing it down), so [targetText] is keyed on [setId] to reseed
 * from the new set's weight — found retargeting without dismissing during the M20a-h code audit
 * (2026-09-08); before this it kept the previous set's prefilled number and "Use X" would have
 * applied it to the newly-targeted set. [selectedBarKg] stays unkeyed on purpose: which bar you're
 * loading plates onto is a per-session choice, not a per-set one, so it should carry over.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlateCalculatorSheet(
    setId: String,
    initialWeightKg: Double?,
    weightUnit: WeightUnit,
    equipment: PlateEquipment,
    onApply: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    // Defensive: the equipment editor never persists zero bars, but an empty list here would make
    // every solve meaningless — fall back to the standard 20 kg bar.
    val bars = equipment.barsKg.ifEmpty { listOf(20.0) }
    var selectedBarKg by remember { mutableStateOf(bars.first()) }
    var targetText by remember(setId) {
        mutableStateOf(initialWeightKg?.let { formatWeightNumber(toDisplay(it, weightUnit)) }.orEmpty())
    }

    FlexibleBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlexibleBottomSheetState(
            isModal = false,
            skipSlightlyExpanded = false,
            flexibleSheetSize = FlexibleSheetSize(fullyExpanded = 0.85f, intermediatelyExpanded = 0.45f, slightlyExpanded = 0.15f),
        ),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.md).padding(bottom = Spacing.lg)) {
            Text(
                stringResource(R.string.workout_plate_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            OutlinedTextField(
                value = targetText,
                onValueChange = { targetText = it },
                label = { Text(stringResource(R.string.workout_plate_target_label)) },
                suffix = { Text(weightUnit.shortLabel()) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
            )

            if (bars.size > 1) {
                Text(
                    stringResource(R.string.workout_plate_bar_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md),
                )
                LazyRow(modifier = Modifier.padding(top = Spacing.xxs)) {
                    items(items = bars, key = { it }) { bar ->
                        FilterChip(
                            selected = selectedBarKg == bar,
                            onClick = { selectedBarKg = bar },
                            label = { Text(formatWeight(bar, weightUnit)) },
                            modifier = Modifier.padding(end = Spacing.xs),
                        )
                    }
                }
            }

            val targetKg = parseDecimalInput(targetText)?.let { toKg(it, weightUnit) }
            if (targetKg == null || targetKg <= 0.0) {
                Text(
                    stringResource(R.string.workout_plate_enter_target),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.md),
                )
            } else {
                // Live solve: recomputed only when target, bar, or equipment actually changes.
                val result = remember(targetKg, selectedBarKg, equipment.platesKg) {
                    PlateCalculator.solve(targetKg, selectedBarKg, equipment.platesKg)
                }
                PlateSolveResult(result = result, weightUnit = weightUnit, platesEmpty = equipment.platesKg.isEmpty(), onApply = onApply, onDismiss = onDismiss)
            }
        }
    }
}

@Composable
private fun PlateSolveResult(
    result: PlateCalculator.Result,
    weightUnit: WeightUnit,
    platesEmpty: Boolean,
    onApply: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    if (result.belowBar) {
        // §5.1.5 edge case: "Target < bar weight → 'Bar alone weighs Y'."
        Text(
            stringResource(R.string.workout_plate_bar_alone, formatWeight(result.achievedKg, weightUnit)),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = Spacing.md),
        )
        return
    }

    val perSideLabel = if (result.perSideKg.isEmpty()) {
        "—"
    } else {
        result.perSideKg.joinToString(" · ") { formatWeightNumber(toDisplay(it, weightUnit)) }
    }
    Text(
        stringResource(R.string.workout_plate_per_side, perSideLabel),
        style = LogEzMono.dataMedium,
        modifier = Modifier.padding(top = Spacing.md),
    )
    Text(
        stringResource(R.string.workout_plate_total, formatWeight(result.achievedKg, weightUnit)),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = Spacing.xxs),
    )

    if (!result.exact) {
        if (platesEmpty) {
            // §5.1.5 edge case: no plates at all — say why the solve can only offer the bar.
            Text(
                stringResource(R.string.workout_plate_no_plates),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.md),
            )
        }
        // §5.1.5 fallback: closest banner + "Use X" writing the achieved total into the cell (kg).
        Text(
            stringResource(R.string.workout_plate_closest, formatWeight(result.achievedKg, weightUnit)),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
            Button(onClick = { onApply(result.achievedKg); onDismiss() }) {
                Text(stringResource(R.string.workout_plate_use, formatWeight(result.achievedKg, weightUnit)))
            }
        }
    }
}

private fun WeightUnit.shortLabel(): String = if (this == WeightUnit.KG) "kg" else "lb"

// M18: the sheet's kg ↔ display conversions delegate to WeightDisplay — the same single boundary
// the weight cells use — so the two surfaces can never disagree about what "100 lb" means. The
// apply path stays canonical kg end-to-end (solve → achievedKg → onApply → the cell's kg value),
// so the sheet's internal conversion and the cell's display conversion never stack.
private fun toDisplay(kg: Double, unit: WeightUnit): Double = com.enil.logez.core.domain.calc.WeightDisplay.toDisplay(kg, unit)

private fun toKg(display: Double, unit: WeightUnit): Double = com.enil.logez.core.domain.calc.WeightDisplay.toKg(display, unit)

private fun formatWeight(kg: Double, unit: WeightUnit): String =
    "${formatWeightNumber(toDisplay(kg, unit))} ${unit.shortLabel()}"

/**
 * Whole numbers bare, otherwise up to two decimals with trailing zeros trimmed ("1.25", "2.5").
 * Locale.ROOT keeps the decimal separator a dot on comma-decimal locales — the pre-filled target
 * text must round-trip through [String.toDoubleOrNull], which only parses dots.
 */
private fun formatWeightNumber(value: Double): String = com.enil.logez.core.domain.calc.WeightDisplay.format(value)
