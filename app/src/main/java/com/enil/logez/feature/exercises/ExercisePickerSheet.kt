package com.enil.logez.feature.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.common.equipmentLabel
import com.enil.logez.core.common.muscleGroupLabel
import com.enil.logez.core.designsystem.EmptyState
import com.enil.logez.core.designsystem.LocalImage
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.muscleGroupIcon
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.repository.Exercise
import java.io.File
import androidx.compose.ui.res.pluralStringResource

enum class ExercisePickerMode { ADD, REPLACE }

/**
 * PHASE2_PLAN.md §5.1.9 — shared selection surface behind every "+ Add Exercise" and
 * "Replace Exercise" entry point. Full-height [ModalBottomSheet] wrapping the same
 * search/filter/sort as the Exercise Library (§5.2), one implementation, two modes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
    mode: ExercisePickerMode,
    onDismiss: () -> Unit,
    onAddCommitted: (List<Exercise>) -> Unit,
    onExercisePicked: (Exercise) -> Unit,
    onCreateExercise: (prefillName: String?) -> Unit,
    viewModel: ExercisePickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun dismiss() {
        viewModel.onSheetClosed()
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.md)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                placeholder = { Text(stringResource(R.string.exercise_library_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                PickerEquipmentFilterChip(selected = uiState.equipmentFilter, onSelect = viewModel::onEquipmentFilterChange)
                PickerMuscleFilterChip(selected = uiState.muscleFilter, onSelect = viewModel::onMuscleFilterChange)
            }

            HorizontalDivider()

            Box(modifier = Modifier.weight(1f)) {
                if (uiState.exercises.isEmpty() && !uiState.isLoading) {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = stringResource(R.string.exercise_library_no_results_title),
                        subtitle = stringResource(R.string.exercise_library_no_results_subtitle),
                        ctaLabel = if (uiState.searchQuery.isNotBlank()) {
                            stringResource(R.string.exercise_library_create_from_query, uiState.searchQuery)
                        } else {
                            null
                        },
                        onCtaClick = { onCreateExercise(uiState.searchQuery) },
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            ListItem(
                                modifier = Modifier.clickable { onCreateExercise(null) },
                                leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                                headlineContent = { Text(stringResource(R.string.exercise_picker_create_row)) },
                            )
                        }
                        items(items = uiState.exercises, key = { it.id }) { exercise ->
                            PickerRow(
                                exercise = exercise,
                                mode = mode,
                                isSelected = exercise.id in uiState.selectedIds,
                                onClick = {
                                    when (mode) {
                                        ExercisePickerMode.ADD -> viewModel.toggleSelected(exercise)
                                        ExercisePickerMode.REPLACE -> {
                                            onExercisePicked(exercise)
                                            dismiss()
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            if (mode == ExercisePickerMode.ADD) {
                Button(
                    onClick = {
                        onAddCommitted(viewModel.selectedExercises())
                        dismiss()
                    },
                    enabled = uiState.selectedCount > 0,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                ) {
                    Text(pluralStringResource(R.plurals.exercise_picker_add_n, uiState.selectedCount, uiState.selectedCount))
                }
            }
        }
    }
}

@Composable
private fun PickerRow(
    exercise: Exercise,
    mode: ExercisePickerMode,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    val mediaPath = exercise.mediaPath
                    if (mediaPath != null) {
                        LocalImage(
                            absolutePath = File(context.filesDir, mediaPath).absolutePath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Icon(
                            imageVector = muscleGroupIcon(exercise.primaryMuscleGroup),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            Text(muscleGroupLabel(exercise.primaryMuscleGroup) + if (exercise.isCustom) stringResource(R.string.exercise_library_custom_badge) else "")
        },
        trailingContent = if (mode == ExercisePickerMode.ADD) {
            {
                if (isSelected) {
                    Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                } else {
                    Checkbox(checked = false, onCheckedChange = { onClick() })
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun PickerEquipmentFilterChip(selected: Equipment?, onSelect: (Equipment?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = { Text(selected?.let { equipmentLabel(it) } ?: stringResource(R.string.exercise_library_filter_equipment)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.exercise_library_filter_all)) }, onClick = { expanded = false; onSelect(null) })
            Equipment.entries.forEach { eq ->
                DropdownMenuItem(text = { Text(equipmentLabel(eq)) }, onClick = { expanded = false; onSelect(eq) })
            }
        }
    }
}

@Composable
private fun PickerMuscleFilterChip(selected: MuscleGroup?, onSelect: (MuscleGroup?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = { Text(selected?.let { muscleGroupLabel(it) } ?: stringResource(R.string.exercise_library_filter_muscle)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.exercise_library_filter_all)) }, onClick = { expanded = false; onSelect(null) })
            MuscleGroup.entries.forEach { m ->
                DropdownMenuItem(text = { Text(muscleGroupLabel(m)) }, onClick = { expanded = false; onSelect(m) })
            }
        }
    }
}
