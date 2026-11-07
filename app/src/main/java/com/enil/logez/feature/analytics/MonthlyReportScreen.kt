package com.enil.logez.feature.analytics

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.common.muscleGroupLabel
import com.enil.logez.core.designsystem.BarChart
import com.enil.logez.core.designsystem.RefreshOnResume
import com.enil.logez.core.designsystem.BarChartEntry
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.feature.analytics.MonthlyReportViewModel.ComparisonMetric
import com.enil.logez.feature.exercises.SummaryFormatters
import com.enil.logez.core.domain.model.DistanceUnit
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle

/**
 * PHASE2_PLAN.md §5.2 card 6 — a full-screen report for one calendar month: 6-month comparison,
 * month totals, PR list, mini calendar, muscle distribution vs previous month, top-5 exercises.
 * No share images (social removed). Month navigation by arrows over the span the picker allows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportScreen(
    onBack: () -> Unit,
    viewModel: MonthlyReportViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    RefreshOnResume(viewModel::refresh)

    val monthTitle = uiState.month.format(DateTimeFormatter.ofPattern("MMMM yyyy", currentLocale()))

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { ScreenTitle(stringResource(R.string.analytics_monthly_report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) return@Scaffold
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "picker") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = { viewModel.stepMonth(-1) },
                        enabled = uiState.months.any { it < uiState.month },
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = stringResource(R.string.analytics_month_previous))
                    }
                    Text(
                        monthTitle,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { viewModel.stepMonth(1) },
                        enabled = uiState.months.any { it > uiState.month },
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = stringResource(R.string.analytics_month_next))
                    }
                }
            }

            if (uiState.totals.workouts == 0) {
                item(key = "empty") {
                    Text(
                        stringResource(R.string.analytics_month_empty, monthTitle),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xl),
                    )
                }
            } else {
                item(key = "totals") { MonthTotalsCard(uiState) }
                item(key = "comparison") { ComparisonCard(uiState, viewModel) }
                item(key = "calendar") { MiniCalendarCard(uiState) }
                if (uiState.personalRecords.isNotEmpty()) {
                    item(key = "prs") { MonthPrCard(uiState) }
                }
                if (uiState.distributionCurrent.isNotEmpty()) {
                    item(key = "distribution") { MonthDistributionCard(uiState) }
                }
                if (uiState.topExercises.isNotEmpty()) {
                    item(key = "top") { TopExercisesCard(uiState) }
                }
            }
        }
    }
}

@Composable
private fun MonthTotalsCard(uiState: MonthlyReportUiState) {
    LogEzCard {
        Row(modifier = Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Tile(stringResource(R.string.analytics_tile_workouts), uiState.totals.workouts.toString(), Modifier.weight(1f))
            Tile(stringResource(R.string.analytics_tile_duration), AnalyticsFormatters.durationHoursMinutes(uiState.totals.durationSeconds), Modifier.weight(1f))
            Tile(stringResource(R.string.analytics_tile_volume), AnalyticsFormatters.volume(uiState.totals.volumeKg, uiState.weightUnit), Modifier.weight(1f))
            Tile(stringResource(R.string.analytics_tile_sets), uiState.totals.sets.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun ComparisonCard(uiState: MonthlyReportUiState, viewModel: MonthlyReportViewModel) {
    LogEzCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                stringResource(R.string.analytics_month_comparison_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                ComparisonMetric.entries.forEach { metric ->
                    FilterChip(
                        selected = metric == uiState.comparisonMetric,
                        onClick = { viewModel.selectComparisonMetric(metric) },
                        label = { Text(comparisonMetricLabel(metric)) },
                    )
                }
            }
            val monthFormatter = DateTimeFormatter.ofPattern("MMM", currentLocale())
            val monthReadoutFormatter = DateTimeFormatter.ofPattern("MMM yyyy", currentLocale())
            // §5.2 interactions: every bar taps through to its exact value + date range.
            var selectedBar by remember(uiState.comparison, uiState.comparisonMetric) { mutableStateOf<Int?>(null) }
            selectedBar?.let { i ->
                val row = uiState.comparison[i]
                val value = when (uiState.comparisonMetric) {
                    ComparisonMetric.WORKOUTS -> row.totals.workouts.toString()
                    ComparisonMetric.DURATION -> AnalyticsFormatters.durationHoursMinutes(row.totals.durationSeconds)
                    ComparisonMetric.VOLUME -> AnalyticsFormatters.volume(row.totals.volumeKg, uiState.weightUnit)
                    ComparisonMetric.SETS -> row.totals.sets.toString()
                }
                Text(
                    monthReadoutFormatter.format(row.month) + " — " + value,
                    style = LogEzMono.dataMedium.copy(color = MaterialTheme.colorScheme.tertiary),
                )
            }
            BarChart(
                entries = uiState.comparison.map { row ->
                    BarChartEntry(
                        label = monthFormatter.format(row.month),
                        value = when (uiState.comparisonMetric) {
                            ComparisonMetric.WORKOUTS -> row.totals.workouts.toDouble()
                            ComparisonMetric.DURATION -> row.totals.durationSeconds.toDouble()
                            ComparisonMetric.VOLUME -> row.totals.volumeKg
                            ComparisonMetric.SETS -> row.totals.sets.toDouble()
                        },
                    )
                },
                yLabel = { raw ->
                    when (uiState.comparisonMetric) {
                        ComparisonMetric.DURATION -> (raw.toLong() / 3600).toString()
                        ComparisonMetric.VOLUME -> AnalyticsFormatters.axisLabel(
                            com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric.VOLUME, raw, uiState.weightUnit,
                        )
                        else -> Math.round(raw).toString()
                    }
                },
                selectedIndex = selectedBar,
                onBarTap = { selectedBar = it },
                chartHeight = 160.dp,
            )
        }
    }
}

@Composable
private fun comparisonMetricLabel(metric: ComparisonMetric): String = when (metric) {
    ComparisonMetric.WORKOUTS -> stringResource(R.string.analytics_tile_workouts)
    ComparisonMetric.DURATION -> stringResource(R.string.analytics_tile_duration)
    ComparisonMetric.VOLUME -> stringResource(R.string.analytics_tile_volume)
    ComparisonMetric.SETS -> stringResource(R.string.analytics_tile_sets)
}

/** Mini month grid — dots only, no interaction; the full Calendar screen owns the tap-a-day flow. */
@Composable
private fun MiniCalendarCard(uiState: MonthlyReportUiState) {
    LogEzCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(
                stringResource(R.string.analytics_month_calendar_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            MiniMonthGrid(uiState.month, uiState.workoutDates)
        }
    }
}

@Composable
private fun MiniMonthGrid(month: YearMonth, workoutDates: Set<LocalDate>) {
    val firstDay = month.atDay(1)
    // Monday-first regardless of the calendar setting: this grid is a report thumbnail, not the
    // interactive calendar — the full screen honors firstDayOfWeek.
    val leadingBlanks = (firstDay.dayOfWeek.value - 1) % 7
    val cells: List<LocalDate?> = List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            java.time.DayOfWeek.entries.forEach { dow ->
                Text(
                    dow.getDisplayName(JavaTextStyle.NARROW, currentLocale()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (day != null) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall)
                                Box(
                                    modifier = Modifier.size(4.dp).background(
                                        if (day in workoutDates) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
                                        CircleShape,
                                    ),
                                )
                            }
                        }
                    }
                }
                repeat(7 - week.size) { Box(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MonthPrCard(uiState: MonthlyReportUiState) {
    LogEzCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.analytics_month_prs_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            uiState.personalRecords.forEach { pr ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(pr.exerciseName, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            prTypeLabel(pr.prType),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        SummaryFormatters.formatPrValue(pr.prType, pr.value, uiState.weightUnit, DistanceUnit.KM),
                        style = LogEzMono.dataMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun prTypeLabel(prType: com.enil.logez.core.domain.model.PrType): String = when (prType) {
    com.enil.logez.core.domain.model.PrType.HEAVIEST_WEIGHT -> stringResource(R.string.pr_type_heaviest_weight)
    com.enil.logez.core.domain.model.PrType.BEST_1RM -> stringResource(R.string.pr_type_best_1rm)
    com.enil.logez.core.domain.model.PrType.BEST_SET_VOLUME -> stringResource(R.string.pr_type_best_set_volume)
    com.enil.logez.core.domain.model.PrType.BEST_SESSION_VOLUME -> stringResource(R.string.pr_type_best_session_volume)
    com.enil.logez.core.domain.model.PrType.MOST_REPS_SET -> stringResource(R.string.pr_type_most_reps_set)
    com.enil.logez.core.domain.model.PrType.MOST_SESSION_REPS -> stringResource(R.string.pr_type_most_session_reps)
    com.enil.logez.core.domain.model.PrType.LONGEST_DISTANCE -> stringResource(R.string.pr_type_longest_distance)
    com.enil.logez.core.domain.model.PrType.LONGEST_TIME -> stringResource(R.string.pr_type_longest_time)
    com.enil.logez.core.domain.model.PrType.BEST_TIME -> stringResource(R.string.pr_type_best_time)
}

@Composable
private fun MonthDistributionCard(uiState: MonthlyReportUiState) {
    LogEzCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.analytics_distribution_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val maxCount = uiState.distributionCurrent.maxOf { it.setCount }
            uiState.distributionCurrent.forEach { share -> ShareRow(share, maxCount) }
            uiState.distributionPrevious?.let { previous ->
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                Text(
                    stringResource(R.string.analytics_month_previous_distribution),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                previous.forEach { share ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                        Text(
                            muscleGroupLabel(share.group),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${share.setCount} · ${share.sharePercent}%",
                            style = LogEzMono.dataSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopExercisesCard(uiState: MonthlyReportUiState) {
    LogEzCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                stringResource(R.string.analytics_month_top_exercises),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            uiState.topExercises.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                    Text(row.exerciseName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        pluralStringResource(R.plurals.analytics_exercise_times, row.workoutCount, row.workoutCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
