package com.enil.logez.feature.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.BodyDiagram
import com.enil.logez.core.designsystem.BodyDiagramRegions
import com.enil.logez.core.designsystem.ConfirmDialog
import com.enil.logez.core.designsystem.EmptyState
import com.enil.logez.core.designsystem.LineChart
import com.enil.logez.core.designsystem.LineChartPoint
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.formatTwoDecimals
import com.enil.logez.core.designsystem.formatWeight
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.domain.calc.ChartMetric
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.model.ExerciseHistoryEntry
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.feature.workout.finish.labelRes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch
import com.enil.logez.core.designsystem.InlineMarkdown

internal enum class DetailTab(@StringRes val labelRes: Int) {
    SUMMARY(R.string.exercise_detail_tab_summary),
    HISTORY(R.string.exercise_detail_tab_history),
    HOW_TO(R.string.exercise_detail_tab_how_to),
}

/**
 * An exercise with no instructions has no How-to tab at all, rather than a tab that only ever
 * renders an empty state — the same absence-beats-a-zeroed-state rule the rest of the app follows.
 * Every one of the 400 seeded exercises ships without instructions, so the tab was empty almost
 * everywhere; it comes back for any exercise the user has written steps for.
 *
 * Empty while loading, deliberately: the exercise is null before the first load lands, and reading
 * that as "no instructions" would build a two-tab row and then pop a third tab in a frame later.
 */
internal fun detailTabsFor(isLoading: Boolean, instructions: String?): List<DetailTab> = when {
    isLoading -> emptyList()
    instructions.isNullOrBlank() -> listOf(DetailTab.SUMMARY, DetailTab.HISTORY)
    else -> DetailTab.entries
}

/**
 * Derived rather than corrected after the fact: clearing an exercise's instructions elsewhere
 * shrinks the tab list under a HOW_TO selection on the next resume. Falling back here means there
 * is never a frame where TabRow is handed the -1 that indexOf returns for an absent tab.
 */
internal fun selectedDetailTab(requested: DetailTab, tabs: List<DetailTab>): DetailTab =
    if (requested in tabs) requested else DetailTab.SUMMARY

private data class HistorySession(val workoutId: String, val workoutTitle: String, val sets: List<ExerciseHistoryEntry>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    onDuplicated: (String) -> Unit,
    viewModel: ExerciseDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var requestedTab by remember { mutableStateOf(DetailTab.SUMMARY) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Re-resolves today/zone/settings and re-reads history (the CalendarViewModel convention) —
    // an edit made from a workout opened off this screen's History tab lands here on return.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { ScreenTitle(uiState.exercise?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val exercise = uiState.exercise
                    if (exercise != null) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_edit)) },
                                onClick = { menuExpanded = false; onEdit(exercise.id) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_duplicate)) },
                                onClick = {
                                    menuExpanded = false
                                    scope.launch { viewModel.duplicate()?.let(onDuplicated) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_delete)) },
                                onClick = { menuExpanded = false; showDeleteConfirm = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            val tabs = detailTabsFor(uiState.isLoading, uiState.exercise?.instructions)
            val selectedTab = selectedDetailTab(requestedTab, tabs)

            // Nothing renders until the first load lands, matching what this screen already does
            // with its title and overflow menu in that same frame.
            if (tabs.isNotEmpty()) {
                TabRow(selectedTabIndex = tabs.indexOf(selectedTab)) {
                    tabs.forEach { tab ->
                        Tab(
                            selected = tab == selectedTab,
                            onClick = { requestedTab = tab },
                            text = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }

                when (selectedTab) {
                    DetailTab.SUMMARY -> SummaryTab(
                        isLoading = uiState.isLoading,
                        exercise = uiState.exercise,
                        muscleIntensity = uiState.muscleIntensity,
                        muscleDiagramVariant = uiState.muscleDiagramVariant,
                        summary = uiState.summary,
                        onRangeSelected = viewModel::selectRange,
                        onMetricSelected = viewModel::selectMetric,
                    )
                    DetailTab.HISTORY -> HistoryTab(entries = uiState.history, weightUnit = uiState.summary.weightUnit)
                    DetailTab.HOW_TO -> HowToTab(instructions = uiState.exercise?.instructions.orEmpty())
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = stringResource(R.string.exercise_delete_confirm_title),
            body = stringResource(R.string.exercise_delete_confirm_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { viewModel.delete(onDeleted) },
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

/**
 * §5.2 Summary tab (M6a): range selector, metric graph with a metric switcher below it, the
 * Personal Records list from the `personal_records` cache, and the collapsible Set Records table
 * ([SetRecordCalculator][com.enil.logez.core.domain.calc.SetRecordCalculator] gates it to the
 * weight×reps types itself — an empty list simply renders no section). Empty state per the plan:
 * never-logged replaces the graph area and hides PRs/Set Records entirely; an empty *range* keeps
 * the selector active so the user can widen it.
 */
@Composable
private fun SummaryTab(
    isLoading: Boolean,
    exercise: Exercise?,
    muscleIntensity: Map<MuscleGroup, Float>,
    muscleDiagramVariant: MuscleDiagramVariant,
    summary: SummaryUiState,
    onRangeSelected: (ChartRange) -> Unit,
    onMetricSelected: (ChartMetric) -> Unit,
) {
    // Summary is the landing tab, so the pre-load default state would otherwise flash the
    // never-logged claim on every open of an exercise that HAS history. Render nothing until the
    // first load lands — the load is a few Room point-queries, so this is a single-frame blank.
    if (isLoading) return

    // Selection resets whenever the plotted series changes — a stale index into a new list would
    // read out the wrong point.
    var selectedPointIndex by remember(summary.points) { mutableStateOf<Int?>(summary.points.lastIndex.takeIf { it >= 0 }) }
    var setRecordsExpanded by remember { mutableStateOf(false) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy") }
    fun dateOf(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(dateFormatter)

    // Which muscles this exercise targets — static metadata, not a stat, so it can render above
    // the never-logged gate below: an exercise the user has never done is exactly when knowing
    // what it trains is most useful. Same MAPPABLE guard WorkoutSummaryScreen uses — CARDIO/
    // FULL_BODY/OTHER have no drawable region, so they'd render an all-grey body that reads as
    // broken rather than intentional.
    val showDiagram = exercise != null && exercise.primaryMuscleGroup in BodyDiagramRegions.MAPPABLE

    Column(modifier = Modifier.fillMaxSize()) {
        if (!summary.hasAnyLoggedSets) {
            if (showDiagram) {
                MuscleDiagramSection(
                    muscleIntensity = muscleIntensity,
                    muscleDiagramVariant = muscleDiagramVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm),
                )
            }
            EmptyState(
                icon = Icons.Outlined.BarChart,
                title = stringResource(R.string.exercise_detail_summary_empty_title),
                subtitle = stringResource(R.string.exercise_detail_summary_empty_subtitle),
                modifier = Modifier.weight(1f),
            )
            return@Column
        }

        // The diagram is the LazyColumn's own first item, not a sibling fixed above it — it used
        // to be a plain Column rendered before the LazyColumn started, which pinned it in place
        // while the chart/PRs/set records scrolled underneath (Owner-reported 2026-09-22).
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(Spacing.md)) {
            if (showDiagram) {
                item(key = "muscles") {
                    // Restores the pre-extraction gap to "range" below (this item's own bottom
                    // padding used to be paired with the LazyColumn's contentPadding.top, which
                    // only ever applies once at the very top of the list, not between items, now
                    // that the diagram itself occupies that top slot -- adversarial review, 2026-09-22).
                    MuscleDiagramSection(
                        muscleIntensity = muscleIntensity,
                        muscleDiagramVariant = muscleDiagramVariant,
                        modifier = Modifier.padding(bottom = Spacing.sm + Spacing.md),
                    )
                }
            }

            item(key = "range") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    ChartRange.entries.forEach { range ->
                        FilterChip(
                            selected = summary.selectedRange == range,
                            onClick = { onRangeSelected(range) },
                            label = { Text(stringResource(range.labelRes())) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }

            item(key = "chart") {
                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                    val selected = selectedPointIndex?.let { summary.points.getOrNull(it) }
                    if (selected != null && summary.selectedMetric != null) {
                        Text(
                            SummaryFormatters.formatMetricValue(summary.selectedMetric, selected.value, summary.weightUnit, summary.distanceUnit),
                            style = LogEzMono.dataLarge,
                        )
                        Text(
                            dateOf(selected.startedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (summary.points.isEmpty()) {
                        Text(
                            stringResource(R.string.exercise_detail_summary_no_data_in_range),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = Spacing.xl),
                        )
                    } else if (summary.selectedMetric != null) {
                        LineChart(
                            points = summary.points.map { LineChartPoint(it.startedAt, it.value) },
                            yLabel = { SummaryFormatters.axisLabel(summary.selectedMetric, it, summary.weightUnit, summary.distanceUnit) },
                            xLabel = { dateOf(it) },
                            selectedIndex = selectedPointIndex,
                            onPointTap = { selectedPointIndex = it },
                            modifier = Modifier.padding(top = Spacing.sm),
                        )
                    }
                }
            }

            item(key = "metrics") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = Spacing.sm),
                ) {
                    summary.metrics.forEach { metric ->
                        FilterChip(
                            selected = summary.selectedMetric == metric,
                            onClick = { onMetricSelected(metric) },
                            label = { Text(stringResource(metric.labelRes())) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                            ),
                        )
                    }
                }
            }

            if (summary.personalRecords.isNotEmpty()) {
                item(key = "prs-header") {
                    Text(
                        stringResource(R.string.exercise_detail_summary_prs_header),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = Spacing.lg, bottom = Spacing.xs),
                    )
                }
                items(items = summary.personalRecords, key = { "pr-${it.prType}" }) { pr ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(pr.prType.labelRes()), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                dateOf(pr.achievedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            SummaryFormatters.formatPrValue(pr.prType, pr.value, summary.weightUnit, summary.distanceUnit),
                            style = LogEzMono.dataMedium,
                        )
                    }
                }
            }

            if (summary.setRecords.isNotEmpty()) {
                item(key = "set-records-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { setRecordsExpanded = !setRecordsExpanded }
                            .padding(top = Spacing.lg, bottom = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.exercise_detail_summary_set_records_header),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (setRecordsExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                        )
                    }
                }
                if (setRecordsExpanded) {
                    items(items = summary.setRecords, key = { "sr-${it.reps}" }) { record ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                            Text(
                                pluralStringResource(R.plurals.exercise_detail_summary_set_records_reps, record.reps, record.reps),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                SummaryFormatters.formatPrValue(PrType.HEAVIEST_WEIGHT, record.weightKg, summary.weightUnit, summary.distanceUnit),
                                style = LogEzMono.dataMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MuscleDiagramSection(
    muscleIntensity: Map<MuscleGroup, Float>,
    muscleDiagramVariant: MuscleDiagramVariant,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            stringResource(R.string.exercise_detail_summary_muscles_worked_header),
            style = MaterialTheme.typography.titleMedium,
        )
        BodyDiagram(
            intensity = muscleIntensity,
            variant = muscleDiagramVariant,
            modifier = Modifier.padding(top = Spacing.xs),
        )
    }
}

private fun ChartRange.labelRes(): Int = when (this) {
    ChartRange.LAST_30_DAYS -> R.string.chart_range_30d
    ChartRange.LAST_3_MONTHS -> R.string.chart_range_3m
    ChartRange.LAST_YEAR -> R.string.chart_range_1y
    ChartRange.ALL_TIME -> R.string.chart_range_all
}

private fun ChartMetric.labelRes(): Int = when (this) {
    ChartMetric.HEAVIEST_WEIGHT -> R.string.chart_metric_heaviest_weight
    ChartMetric.ONE_REP_MAX -> R.string.chart_metric_one_rep_max
    ChartMetric.BEST_SET_VOLUME -> R.string.chart_metric_best_set_volume
    ChartMetric.SESSION_VOLUME -> R.string.chart_metric_session_volume
    ChartMetric.TOTAL_REPS -> R.string.chart_metric_total_reps
    ChartMetric.MOST_REPS_SET -> R.string.chart_metric_most_reps_set
    ChartMetric.SESSION_REPS -> R.string.chart_metric_session_reps
    ChartMetric.BEST_TIME -> R.string.chart_metric_best_time
    ChartMetric.LONGEST_TIME -> R.string.chart_metric_longest_time
    ChartMetric.LONGEST_DISTANCE -> R.string.chart_metric_longest_distance
    ChartMetric.BEST_PACE -> R.string.chart_metric_best_pace
}

@Composable
private fun HistoryTab(entries: List<ExerciseHistoryEntry>, weightUnit: WeightUnit) {
    if (entries.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.BarChart,
            title = stringResource(R.string.exercise_detail_history_empty_title),
            subtitle = stringResource(R.string.exercise_detail_history_empty_subtitle),
        )
        return
    }
    val sessions = entries
        .groupBy { it.workoutId }
        .map { (workoutId, sets) -> HistorySession(workoutId, sets.first().workoutTitle, sets.sortedBy { it.setOrderIndex }) }
        .sortedByDescending { it.sets.first().workoutStartedAt }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        items(items = sessions, key = { it.workoutId }) { session ->
            // Explicitly full-width, like every other card in the app: v4.0's top-edge accent spans
            // the card, so a content-hugging card would end the accent in a seam mid-row.
            LogEzCard(modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm)) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(
                        session.workoutTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    session.sets.forEach { set ->
                        Text(
                            formatHistorySet(set, weightUnit),
                            style = LogEzMono.dataMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                    }
                }
            }
        }
    }
}

private fun formatHistorySet(entry: ExerciseHistoryEntry, weightUnit: WeightUnit): String {
    val parts = mutableListOf<String>()
    entry.weightKg?.let { parts.add(formatWeight(it, weightUnit)) }
    entry.reps?.let { parts.add("${it} reps") }
    entry.durationSeconds?.let { parts.add("${it}s") }
    // This literal-interpolated the raw Double with zero formatting -- a GPS-accumulated
    // distance routinely carries a long floating-point tail (e.g. "3247.8921336m"). Capped at
    // two decimals to match the walk/run precision convention (Owner request, 2026-09-23).
    entry.distanceMeters?.let { parts.add("${formatTwoDecimals(it)}m") }
    entry.rpe?.let { parts.add("@$it") }
    return if (parts.isEmpty()) "—" else parts.joinToString(" · ")
}

@Composable
private fun HowToTab(instructions: String) {
    // No blank guard: the tab itself is absent when there is nothing to show (see detailTabsFor).
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.md)) {
        instructions.split("\n").filter { it.isNotBlank() && !InlineMarkdown.isFillerLine(it) }.forEachIndexed { index, step ->
            // M20g: the step's own text is parsed as Markdown for inline **bold**/*italic* --
            // the "N." number stays a plain Text outside that parse so a step starting with a
            // digit (e.g. "12 reps...") is never misread as CommonMark ordered-list syntax.
            Row(modifier = Modifier.padding(bottom = Spacing.sm)) {
                Text(
                    "${index + 1}.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(end = Spacing.xs),
                )
                // Parsed once per distinct step text (InlineMarkdown replaced the rich-editor
                // library on 2026-09-25; the stored **bold** / *italic* format is unchanged).
                val stepText = remember(step) { InlineMarkdown.toAnnotatedString(step) }
                Text(stepText, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
