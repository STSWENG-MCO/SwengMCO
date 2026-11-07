package com.enil.logez.feature.exercises

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.common.equipmentLabel
import com.enil.logez.core.common.exerciseTypeLabel
import com.enil.logez.core.common.muscleGroupLabel
import com.enil.logez.core.common.muscleHeadLabel
import com.enil.logez.core.designsystem.LocalImage
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.availableHeads
import com.enil.logez.core.domain.model.userSelectable
import java.io.File
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import com.enil.logez.core.designsystem.InlineMarkdown
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.runtime.saveable.rememberSaveable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomExerciseEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: CustomExerciseEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.onImagePicked(uri)
    }

    // M20g: storage stays "one step per line" -- but NOT plain text: a step can carry inline
    // **bold**/*italic* markers on disk, and ExerciseDetailScreen's HowToTab parses them back out.
    // Since 2026-09-25 the field edits that Markdown directly (InlineMarkdown styles it in place
    // and dims the markers) instead of going through the rich-editor library, so what is typed is
    // exactly what is stored. In edit mode the ViewModel loads the exercise asynchronously
    // (isLoading starts true), so the field is seeded once, after that load; re-seeding on every
    // uiState.instructions change would fight the cursor, since edits write back into that field.
    var instructions by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var instructionsSeeded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        snapshotFlow { uiState.isLoading }.first { !it }
        if (instructionsSeeded) {
            // Restored after process death: the field kept the user's text but the new ViewModel
            // did not, and Save writes the ViewModel's copy (2026-09-25 review). Push it back.
            viewModel.onInstructionsChange(instructions.text)
        } else {
            // Drops the old rich-text editor's "<br>" filler lines from instructions it wrote.
            val seeded = uiState.instructions.lines().filterNot(InlineMarkdown::isFillerLine).joinToString("\n")
            instructions = TextFieldValue(seeded)
            if (seeded != uiState.instructions) viewModel.onInstructionsChange(seeded)
            instructionsSeeded = true
        }
    }
    fun updateInstructions(value: TextFieldValue) {
        instructions = value
        viewModel.onInstructionsChange(value.text)
    }
    val (instructionsBold, instructionsItalic) = InlineMarkdown.stylesAt(instructions.text, instructions.selection.start)

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = {
                    Text(
                        stringResource(
                            if (uiState.isEditMode) R.string.exercise_editor_title_edit else R.string.exercise_editor_title_create,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.save(onSaved) }) {
                        Text(stringResource(R.string.action_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
        ) {
            Surface(
                shape = RoundedCornerShape(Radius.md),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(96.dp)
                    .clickable { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    val mediaPath = uiState.mediaPath
                    if (mediaPath != null) {
                        LocalImage(
                            absolutePath = File(context.filesDir, mediaPath).absolutePath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Radius.md)),
                        )
                    } else {
                        Icon(Icons.Outlined.AddAPhoto, contentDescription = stringResource(R.string.exercise_editor_add_photo))
                    }
                }
            }

            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.exercise_editor_name)) },
                isError = uiState.nameError,
                supportingText = if (uiState.nameError) {
                    { Text(stringResource(R.string.exercise_editor_name_required)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
            )

            EnumDropdown(
                label = stringResource(R.string.exercise_editor_type),
                selectedLabel = exerciseTypeLabel(uiState.exerciseType),
                options = ExerciseType.Companion.userSelectable,
                optionLabel = ::exerciseTypeLabel,
                onSelect = viewModel::onExerciseTypeChange,
                enabled = !uiState.isEditMode,
                modifier = Modifier.padding(top = Spacing.md),
            )
            if (uiState.isEditMode) {
                Text(
                    stringResource(R.string.exercise_editor_type_immutable_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }

            EnumDropdown(
                label = stringResource(R.string.exercise_editor_equipment),
                selectedLabel = equipmentLabel(uiState.equipment),
                options = Equipment.entries,
                optionLabel = ::equipmentLabel,
                onSelect = viewModel::onEquipmentChange,
                modifier = Modifier.padding(top = Spacing.lg),
            )

            EnumDropdown(
                label = stringResource(R.string.exercise_editor_primary_muscle),
                selectedLabel = muscleGroupLabel(uiState.primaryMuscleGroup),
                options = MuscleGroup.entries,
                optionLabel = ::muscleGroupLabel,
                onSelect = viewModel::onPrimaryMuscleChange,
                modifier = Modifier.padding(top = Spacing.md),
            )

            // M8e: only groups with a real, commonly-trained head split show this — e.g. Shoulders
            // (anterior/lateral/posterior delt), not e.g. Abdominals. A checklist, not a single
            // pick (Owner feedback) — an exercise can genuinely work more than one head of the
            // same group, e.g. a compound press hitting both the anterior and lateral delt.
            val headOptions = uiState.primaryMuscleGroup.availableHeads
            if (headOptions.isNotEmpty()) {
                Text(
                    stringResource(R.string.exercise_editor_muscle_head),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = Spacing.lg),
                )
                Column {
                    headOptions.forEach { head ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onMuscleHeadToggle(head) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = head in uiState.muscleHeads, onCheckedChange = { viewModel.onMuscleHeadToggle(head) })
                            Text(muscleHeadLabel(head))
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.exercise_editor_secondary_muscles),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = Spacing.lg),
            )
            Column {
                MuscleGroup.entries.filter { it != uiState.primaryMuscleGroup }.forEach { m ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onSecondaryMuscleToggle(m) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = m in uiState.secondaryMuscleGroups, onCheckedChange = { viewModel.onSecondaryMuscleToggle(m) })
                        Text(muscleGroupLabel(m))
                    }
                }
            }

            Row(modifier = Modifier.padding(top = Spacing.lg)) {
                StyleToggle(
                    checked = { instructionsBold },
                    onToggle = { updateInstructions(InlineMarkdown.toggleBold(instructions)) },
                    icon = Icons.Outlined.FormatBold,
                    contentDescription = stringResource(R.string.exercise_editor_bold),
                )
                StyleToggle(
                    checked = { instructionsItalic },
                    onToggle = { updateInstructions(InlineMarkdown.toggleItalic(instructions)) },
                    icon = Icons.Outlined.FormatItalic,
                    contentDescription = stringResource(R.string.exercise_editor_italic),
                )
            }
            val markerColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            val markdownStyling = remember(markerColor) { InlineMarkdown.editorTransformation(markerColor) }
            OutlinedTextField(
                value = instructions,
                onValueChange = ::updateInstructions,
                label = { Text(stringResource(R.string.exercise_editor_instructions)) },
                supportingText = { Text(stringResource(R.string.exercise_editor_instructions_hint)) },
                minLines = 4,
                visualTransformation = markdownStyling,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(Spacing.xxl))
        }
    }
}

/**
 * A Bold/Italic toolbar button whose pressed state shows whether the caret sits inside that style.
 * Tapping wraps the selection in the Markdown marker, or unwraps it when it is already wrapped
 * ([InlineMarkdown.toggle]). `IconToggleButton` rather than `IconButton`: a visibly different
 * content colour per state and a `Role.Checkbox` node TalkBack reads as "checked"/"not checked",
 * so the value half of name/role/value is never missing.
 */
@Composable
private fun StyleToggle(checked: () -> Boolean, onToggle: () -> Unit, icon: ImageVector, contentDescription: String) {
    IconToggleButton(checked = checked(), onCheckedChange = { onToggle() }) {
        Icon(icon, contentDescription = contentDescription)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        DropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { expanded = false; onSelect(option) })
            }
        }
    }
}
