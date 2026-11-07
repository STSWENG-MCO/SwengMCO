package com.enil.logez.feature.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import com.enil.logez.core.common.muscleHeadLabel
import com.enil.logez.core.designsystem.ConfirmDialog
import com.enil.logez.core.designsystem.EmptyState
import com.enil.logez.core.designsystem.LocalImage
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.designsystem.muscleGroupIcon
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.repository.Exercise
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseLibraryScreen(
    onBack: () -> Unit,
    onExerciseClick: (String) -> Unit,
    onCreateExercise: (prefillName: String?) -> Unit,
    onEditExercise: (String) -> Unit,
    viewModel: ExerciseLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { Text(stringResource(R.string.exercise_library_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { onCreateExercise(null) }) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.exercise_library_create))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                placeholder = { Text(stringResource(R.string.exercise_library_search_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                EquipmentFilterChip(
                    selected = uiState.equipmentFilter,
                    onSelect = viewModel::onEquipmentFilterChange,
                )
                MuscleFilterChip(
                    selected = uiState.muscleFilter,
                    onSelect = viewModel::onMuscleFilterChange,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(top = Spacing.sm))

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
                    items(items = uiState.exercises, key = { it.id }) { exercise ->
                        ExerciseRow(
                            exercise = exercise,
                            onClick = { onExerciseClick(exercise.id) },
                            onDuplicate = { viewModel.duplicateExercise(exercise) },
                            onDelete = { viewModel.deleteExercise(exercise) },
                            onEdit = { onEditExercise(exercise.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseRow(
    exercise: Exercise,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current

    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(40.dp),
            ) {
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
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        headlineContent = { Text(exercise.name) },
        supportingContent = {
            Text(
                muscleGroupLabel(exercise.primaryMuscleGroup) +
                    exercise.muscleHeads.takeIf { it.isNotEmpty() }?.let { heads ->
                        " • " + heads.joinToString(", ") { muscleHeadLabel(it) }
                    }.orEmpty() +
                    if (exercise.isCustom) stringResource(R.string.exercise_library_custom_badge) else "",
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_edit)) }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_duplicate)) }, onClick = { menuExpanded = false; onDuplicate() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.action_delete)) }, onClick = { menuExpanded = false; showDeleteConfirm = true })
                }
            }
        },
    )

    if (showDeleteConfirm) {
        ConfirmDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = stringResource(R.string.exercise_delete_confirm_title),
            body = stringResource(R.string.exercise_delete_confirm_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = onDelete,
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

@Composable
private fun EquipmentFilterChip(selected: Equipment?, onSelect: (Equipment?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = { Text(selected?.let { equipmentLabel(it) } ?: stringResource(R.string.exercise_library_filter_equipment)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
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
private fun MuscleFilterChip(selected: MuscleGroup?, onSelect: (MuscleGroup?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = selected != null,
            onClick = { expanded = true },
            label = { Text(selected?.let { muscleGroupLabel(it) } ?: stringResource(R.string.exercise_library_filter_muscle)) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.exercise_library_filter_all)) }, onClick = { expanded = false; onSelect(null) })
            MuscleGroup.entries.forEach { m ->
                DropdownMenuItem(text = { Text(muscleGroupLabel(m)) }, onClick = { expanded = false; onSelect(m) })
            }
        }
    }
}
