package com.enil.logez.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.common.muscleGroupLabel
import com.enil.logez.core.designsystem.BarChart
import com.enil.logez.core.designsystem.BarChartEntry
import com.enil.logez.core.designsystem.BodyDiagram
import com.enil.logez.core.designsystem.EmptyState
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.MuscleBalanceRadar
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.RefreshOnResume
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.calc.BodyRegion
import com.enil.logez.core.domain.calc.ChartRange
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.domain.calc.MuscleStatsCalculator
import com.enil.logez.core.domain.calc.RegionShare
import com.enil.logez.core.domain.calc.StatBucket
import com.enil.logez.core.domain.model.MuscleGroup
import androidx.compose.material.icons.outlined.BarChart
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role

/**
 * PHASE2_PLAN.md §5.2 "Analytics dashboard" — scrollable column of stat cards. Every §8-owned
 * number comes from the calc engines via [AnalyticsViewModel]; empty periods say so honestly
 * with the range selector still active (never sample data, never zeroed charts).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    onExerciseClick: (String) -> Unit,
    onMuscleClick: (MuscleGroup) -> Unit,
    onMonthlyReportClick: () -> Unit,
    viewModel: AnalyticsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The screen's single load trigger (no init load in the ViewModel): first entry, back from a
    // child screen, tab return, and foregrounding each refresh exactly once — workouts logged
    // meanwhile and midnight/zone changes land fresh (the frozen-field convention).
    RefreshOnResume(viewModel::refresh)

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { ScreenTitle(stringResource(R.string.analytics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) return@Scaffold
        if (!uiState.hasAnyWorkouts) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = Icons.Outlined.BarChart,
                    title = stringResource(R.string.profile_empty_title),
                    subtitle = stringResource(R.string.profile_empty_subtitle),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item(key = "training") { TrainingCard(uiState, viewModel) }
            // M21: absent entirely (not a zeroed/empty card) whenever Health Connect has nothing to
            // show — same graceful-degrade rule as the Profile wellness card and Workout tab scorecard.
            if (uiState.steps.available) {
                item(key = "steps") { StepsCard(uiState, viewModel) }
            }
            item(key = "distribution") { DistributionCard(uiState, viewModel, onMuscleClick) }
            item(key = "muscle_balance") { MuscleBalanceCard(uiState, viewModel) }
            item(key = "body") { BodyCard(uiState, viewModel) }
            item(key = "set_counts") { SetCountCard(uiState, viewModel, onMuscleClick) }
            item(key = "main_exercises") { MainExercisesCard(uiState, viewModel, onExerciseClick) }
            item(key = "monthly_report") { MonthlyReportEntryCard(onMonthlyReportClick) }
        }
    }
}

// --- shared bits ---

@Composable
internal fun RangeChips(selected: ChartRange, onSelect: (ChartRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ChartRange.entries.forEach { range ->
            RangeChip(rangeLabel(range), selected = range == selected, onClick = { onSelect(range) })
        }
    }
}

/**
 * v4.0 chip vocabulary. Material3's `FilterChip` is gone from this screen: its container/label
 * colors, 8dp corner and 32dp height are all baked into `FilterChipDefaults`, so a chip that reads
 * like the mockup's — pill, mono caps, a *glowing* selected state — is less code hand-rolled than
 * fought for through overrides.
 *
 * Two weights, matching the mockup's own two jobs. [MetricChip] is the loud one (what am I
 * measuring), [RangeChip] the quiet one (over what window) — so a card never shows two equally
 * shouting rows of chips stacked on top of each other.
 */
@Composable
private fun MetricChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.pill)
    val primary = MaterialTheme.colorScheme.primary
    ChipTouchTarget(selected = selected, onClick = onClick) {
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) primary else Color.Transparent)
            .let { if (selected) it else it.border(1.dp, MaterialTheme.colorScheme.outline, shape) }
            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    ) {
        Text(
            label.uppercase(currentLocale()),
            style = LogEzMono.dataSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.08.em,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
    }
}

/** The quiet chip: rounded-rect (not a pill), smaller, selected = a green *tint* rather than a fill. */
@Composable
private fun RangeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.sm)
    val primary = MaterialTheme.colorScheme.primary
    ChipTouchTarget(selected = selected, onClick = onClick) {
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) primary.copy(alpha = 0.14f) else Color.Transparent)
            .border(
                1.dp,
                if (selected) primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant,
                shape,
            )
            .padding(horizontal = Spacing.xs, vertical = Spacing.xxs),
    ) {
        Text(
            label.uppercase(currentLocale()),
            style = LogEzMono.dataSmall.copy(
                fontSize = 10.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.06.em,
                color = if (selected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
    }
}

/**
 * The tappable area around a chip: at least 48dp tall (the Play core-quality touch-target floor;
 * the quiet range chip was about 22dp) while the chip itself keeps its compact look, and announced
 * to TalkBack as a selectable tab with its selected state (2026-09-25 accessibility pass).
 */
@Composable
private fun ChipTouchTarget(selected: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * The tapped-bar readout above a chart: a quiet mono caption over the value itself, sized like a
 * lab display. Split in two so the number can be big without the period label pushing it to wrap.
 */
@Composable
private fun ChartReadout(label: String, value: String) {
    Column {
        Text(
            label.uppercase(currentLocale()),
            style = LogEzMono.dataSmall.copy(
                letterSpacing = 0.08.em,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
        Text(value, style = LogEzMono.dataLarge.copy(color = MaterialTheme.colorScheme.tertiary))
    }
}

@Composable
internal fun rangeLabel(range: ChartRange): String = when (range) {
    ChartRange.LAST_30_DAYS -> stringResource(R.string.chart_range_30d)
    ChartRange.LAST_3_MONTHS -> stringResource(R.string.chart_range_3m)
    ChartRange.LAST_YEAR -> stringResource(R.string.chart_range_1y)
    ChartRange.ALL_TIME -> stringResource(R.string.chart_range_all)
}

/**
 * v4.0 card headings — display face, all-caps, letter-spaced, like the mockup's stencilled
 * "TRAINING LOG" plates. Every card on this screen goes through here, so the treatment stays a
 * single decision rather than six.
 */
@Composable
private fun CardTitle(text: String) {
    Text(
        text.uppercase(currentLocale()),
        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.05.em),
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private val weekLabelFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
internal fun weekLabel(week: LocalDate): String = weekLabelFormatter.format(week)

private val monthAxisFormatter = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())
private val monthReadoutFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

/** Axis label for one set-count bucket — a MONTH bucket is a month, never a "1 Jun" day label. */
internal fun bucketAxisLabel(bucketStart: LocalDate, bucket: StatBucket): String = when (bucket) {
    StatBucket.WEEK -> weekLabel(bucketStart)
    StatBucket.MONTH -> monthAxisFormatter.format(bucketStart)
}

/** Readout label: a WEEK bucket is a range ("Week of 10 Aug"), never a bare day date. */
@Composable
internal fun bucketReadoutLabel(bucketStart: LocalDate, bucket: StatBucket): String = when (bucket) {
    StatBucket.WEEK -> stringResource(R.string.analytics_body_week_prefix, weekLabel(bucketStart))
    StatBucket.MONTH -> monthReadoutFormatter.format(bucketStart)
}

/** A share row: name, proportional bar, count + percent — used by both distribution surfaces. */
@Composable
internal fun ShareRow(share: MuscleStatsCalculator.GroupShare, maxCount: Int, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            muscleGroupLabel(share.group),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(110.dp),
        )
        Box(
            modifier = Modifier.weight(1f).height(8.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = if (maxCount == 0) 0f else share.setCount.toFloat() / maxCount)
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
            )
        }
        Text(
            "${share.setCount} · ${share.sharePercent}%",
            style = LogEzMono.dataSmall,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

// --- card 1: training charts ---

@Composable
private fun TrainingCard(uiState: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    val card = uiState.training
    LogEzCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardTitle(stringResource(R.string.analytics_training_title))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(TrainingMetric.entries) { metric ->
                    MetricChip(
                        label = trainingMetricLabel(metric),
                        selected = metric == card.metric,
                        onClick = { viewModel.selectTrainingMetric(metric) },
                    )
                }
            }
            RangeChips(card.range, viewModel::selectTrainingRange)
            if (card.bars.isEmpty()) {
                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
            } else {
                card.selectedBar?.let { i ->
                    val bar = card.bars[i]
                    ChartReadout(
                        label = stringResource(R.string.analytics_body_week_prefix, weekLabel(bar.weekStart)),
                        value = AnalyticsFormatters.metricValue(card.metric, bar.value, uiState.weightUnit),
                    )
                }
                BarChart(
                    entries = card.bars.map { BarChartEntry(weekLabel(it.weekStart), it.value) },
                    yLabel = { AnalyticsFormatters.axisLabel(card.metric, it, uiState.weightUnit) },
                    selectedIndex = card.selectedBar,
                    onBarTap = { viewModel.selectTrainingBar(it) },
                )
            }
        }
    }
}

// --- card: steps (M21, Health Connect) ---

private val stepsDayFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/**
 * Fixed to the last 30 days, no range chips — Health Connect's own read grant only exposes that
 * much history (its own permission screen says so), so a longer selection would have nothing more
 * to show. Daily bars, not weekly: 30 is a perfectly reasonable bar count for [BarChart] (it only
 * labels the first/last bar and taps resolve to the nearest one regardless of count), and daily
 * granularity is more useful than folding 30 days into ~4 weekly bars would be.
 */
@Composable
private fun StepsCard(uiState: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    val card = uiState.steps
    LogEzCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardTitle(stringResource(R.string.analytics_steps_title))
            if (card.bars.isEmpty()) {
                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
            } else {
                card.selectedBar?.let { i ->
                    val bar = card.bars[i]
                    ChartReadout(
                        label = stepsDayFormatter.format(bar.date),
                        value = "%,d".format(Locale.ROOT, bar.steps),
                    )
                }
                BarChart(
                    entries = card.bars.map { BarChartEntry(stepsDayFormatter.format(it.date), it.steps.toDouble()) },
                    yLabel = { "%,d".format(Locale.ROOT, it.toLong()) },
                    selectedIndex = card.selectedBar,
                    onBarTap = viewModel::selectStepsBar,
                )
            }
        }
    }
}

@Composable
internal fun trainingMetricLabel(metric: TrainingMetric): String = when (metric) {
    TrainingMetric.VOLUME -> stringResource(R.string.analytics_metric_volume)
    TrainingMetric.REPS -> stringResource(R.string.analytics_metric_reps)
    TrainingMetric.DURATION -> stringResource(R.string.analytics_metric_duration)
    TrainingMetric.FREQUENCY -> stringResource(R.string.analytics_metric_frequency)
}

// --- card 2: muscle distribution (chart) ---

@Composable
private fun DistributionCard(
    uiState: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    onMuscleClick: (MuscleGroup) -> Unit,
) {
    val card = uiState.distribution
    LogEzCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardTitle(stringResource(R.string.analytics_distribution_title))
            RangeChips(card.range, viewModel::selectDistributionRange)
            PeriodTiles(card.totals, uiState)
            if (card.current.isEmpty()) {
                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
            } else {
                val maxCount = card.current.maxOf { it.setCount }
                card.current.forEach { share ->
                    ShareRow(share, maxCount, onClick = { onMuscleClick(share.group) })
                }
                card.previous?.let { previous ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.xs))
                    Text(
                        stringResource(R.string.analytics_distribution_previous),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    previous.forEach { share ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                            Text(
                                muscleGroupLabel(share.group),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(110.dp),
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
}

@Composable
private fun PeriodTiles(totals: com.enil.logez.core.domain.calc.DashboardAggregator.PeriodTotals, uiState: AnalyticsUiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
        Tile(stringResource(R.string.analytics_tile_workouts), totals.workouts.toString(), Modifier.weight(1f))
        Tile(stringResource(R.string.analytics_tile_duration), AnalyticsFormatters.durationHoursMinutes(totals.durationSeconds), Modifier.weight(1f))
        Tile(stringResource(R.string.analytics_tile_volume), AnalyticsFormatters.volume(totals.volumeKg, uiState.weightUnit), Modifier.weight(1f))
        Tile(stringResource(R.string.analytics_tile_sets), totals.sets.toString(), Modifier.weight(1f))
    }
}

@Composable
internal fun Tile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = LogEzMono.dataMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- card 2b: muscle balance radar (M20c, ADR-0009) ---

@Composable
private fun bodyRegionLabel(region: BodyRegion): String = when (region) {
    BodyRegion.CHEST -> stringResource(R.string.muscle_region_chest)
    BodyRegion.BACK -> stringResource(R.string.muscle_region_back)
    BodyRegion.SHOULDERS -> stringResource(R.string.muscle_region_shoulders)
    BodyRegion.ARMS -> stringResource(R.string.muscle_region_arms)
    BodyRegion.CORE -> stringResource(R.string.muscle_region_core)
    BodyRegion.QUADS -> stringResource(R.string.muscle_region_quads)
    BodyRegion.HAMSTRINGS_GLUTES -> stringResource(R.string.muscle_region_hamstrings_glutes)
    BodyRegion.LOWER_LEG -> stringResource(R.string.muscle_region_lower_leg)
}

/**
 * The short form that goes on a radar spoke. KoalaPlot's measure policy shrinks the wheel until the
 * widest angular label fits its constraints, so a long name here costs chart diameter directly —
 * spelling every region out in full measures down to roughly a third of the wheel. The legend below
 * the wheel carries the full name for each spoke, so these only have to be recognisable.
 */
/**
 * M20c (ADR-0009): a radar/spider plot of set share per [BodyRegion], sitting beside the existing
 * numeric distribution list above it -- same [DistributionCardState.range] (the range chips here
 * are the same selection, shown again so the wheel reads standalone while scrolling). Cardio/full
 * body/other/neck sets are counted in the list above but never appear on the wheel (see
 * [balanceAxes]'s KDoc); the footnote below says so. No previous-period overlay (Owner default,
 * 2026-09-08 structured question: off).
 */
@OptIn(io.github.koalaplot.core.util.ExperimentalKoalaPlotApi::class)
@Composable
private fun MuscleBalanceCard(uiState: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    val card = uiState.distribution
    LogEzCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardTitle(stringResource(R.string.analytics_balance_title))
            RangeChips(card.range, viewModel::selectDistributionRange)
            // Two distinct empty states, not one: card.balance excludes Cardio/Full-body/Other/
            // Neck (balanceAxes' contract), so a period logged entirely in those groups has a
            // non-empty distribution list above but an all-zero balance -- "No workouts in this
            // period" under a populated list contradicted itself (found in the M20a-h code audit,
            // 2026-09-08).
            if (card.current.isEmpty()) {
                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
            } else if (card.balance.all { it.setCount == 0 }) {
                Text(stringResource(R.string.analytics_balance_no_regions), style = MaterialTheme.typography.bodyMedium)
            } else {
                // Eight shares that sum to 100 average 12.5, so a fixed 0..100 axis would pin even
                // a wildly lopsided week inside the innermost quarter of the wheel — the shape,
                // which is the whole point of the card, would be invisible. Scale to the data
                // instead, using BarChart's own convention (max * 1.1, plus zero/mid/max
                // gridlines). The floor keeps a genuinely balanced week from filling the rim.
                MuscleBalanceRadar(card.balance)
                BalanceLegend(card.balance)
                Text(
                    stringResource(R.string.analytics_balance_footnote),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Names every spoke in full, with its share, in the wheel's own order (clockwise from the top).
 * The wheel itself can only carry short labels without shrinking, and its rings are unlabelled, so
 * this is where the chart's numbers actually live.
 *
 * Deliberately percent-only: the muscle-distribution list above already owns set counts per raw
 * [MuscleGroup]. What exists nowhere else is the per-[BodyRegion] share, whose denominator counts
 * only sets that map to a region (see `balanceAxes`) — which is also why these percentages can
 * legitimately differ from that list's.
 */
@Composable
private fun BalanceLegend(shares: List<RegionShare>) {
    // Paired by ROW, not by column. Two independent columns would each size their own rows, so as
    // soon as one name wraps -- "Hamstrings & Glutes" does, at a 1.3 font scale -- the two columns
    // drift out of alignment. Putting both entries in the same Row makes them share its height.
    val rows = (shares.size + 1) / 2
    Column(modifier = Modifier.fillMaxWidth()) {
        repeat(rows) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                BalanceLegendEntry(shares[row], Modifier.weight(1f))
                shares.getOrNull(row + rows)
                    ?.let { BalanceLegendEntry(it, Modifier.weight(1f)) }
                    ?: Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BalanceLegendEntry(share: RegionShare, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            bodyRegionLabel(share.region),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${share.sharePercent}%",
            style = LogEzMono.dataSmall,
            modifier = Modifier.padding(start = Spacing.xxs),
        )
    }
}

// --- card 3: muscle distribution (body) ---

@Composable
private fun BodyCard(uiState: AnalyticsUiState, viewModel: AnalyticsViewModel) {
    val card = uiState.body
    LogEzCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardTitle(stringResource(R.string.analytics_body_title))
            if (card.weeks.isEmpty()) {
                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            // Week pickers are a *window* selector, so they wear the quiet chip, same as RangeChips.
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                items(card.weeks) { week ->
                    RangeChip(
                        label = weekLabel(week),
                        selected = week == card.selectedWeek,
                        onClick = { viewModel.selectBodyWeek(week) },
                    )
                }
            }
            BodyDiagram(intensity = card.intensities, variant = uiState.muscleDiagramVariant)
            card.counts.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs)) {
                    Text(muscleGroupLabel(row.group), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        pluralStringResource(R.plurals.analytics_set_count, row.setCount, row.setCount),
                        style = LogEzMono.dataSmall,
                    )
                }
            }
        }
    }
}

// --- card 4: set count per muscle group ---

@Composable
private fun SetCountCard(
    uiState: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    onMuscleClick: (MuscleGroup) -> Unit,
) {
    val card = uiState.setCounts
    LogEzCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardTitle(stringResource(R.string.analytics_set_count_title))
            RangeChips(card.range, viewModel::selectSetCountRange)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                RangeChip(
                    label = stringResource(R.string.analytics_bucket_week),
                    selected = card.bucket == StatBucket.WEEK,
                    onClick = { viewModel.selectSetCountBucket(StatBucket.WEEK) },
                )
                RangeChip(
                    label = stringResource(R.string.analytics_bucket_month),
                    selected = card.bucket == StatBucket.MONTH,
                    onClick = { viewModel.selectSetCountBucket(StatBucket.MONTH) },
                )
            }
            if (card.muscles.isEmpty()) {
                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            if (card.bars.isNotEmpty()) {
                // §5.2 interactions: every bar taps through to its exact value + date range.
                var selectedBar by remember(card.bars) { mutableStateOf<Int?>(null) }
                selectedBar?.let { i ->
                    val bar = card.bars[i]
                    ChartReadout(
                        label = bucketReadoutLabel(bar.bucketStart, card.bucket),
                        value = pluralStringResource(R.plurals.analytics_set_count, bar.setCount, bar.setCount),
                    )
                }
                BarChart(
                    entries = card.bars.map { BarChartEntry(bucketAxisLabel(it.bucketStart, card.bucket), it.setCount.toDouble()) },
                    yLabel = { it.toInt().toString() },
                    selectedIndex = selectedBar,
                    onBarTap = { selectedBar = it },
                    chartHeight = 140.dp,
                )
            }
            // §5.2 card 4: select/deselect directly on the body diagram; rows toggle too, and a
            // long-press... keeping it simple: row tap toggles inclusion, the trailing arrow opens
            // the filtered library (the plan's tap-through lives on the arrow).
            BodyDiagram(
                intensity = card.diagramIntensities,
                variant = uiState.muscleDiagramVariant,
                selected = card.selectedMuscles,
                onRegionTap = { viewModel.toggleMuscle(it) },
            )
            card.muscles.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { viewModel.toggleMuscle(row.group) }
                        .padding(vertical = Spacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        muscleGroupLabel(row.group),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (row.included) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        pluralStringResource(R.plurals.analytics_set_count, row.setCount, row.setCount),
                        style = LogEzMono.dataSmall.copy(
                            color = if (row.included) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        ),
                    )
                    IconButton(onClick = { onMuscleClick(row.group) }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = muscleGroupLabel(row.group),
                            modifier = Modifier.width(16.dp),
                        )
                    }
                }
            }
        }
    }
}

// --- card 5: main exercises ---

@Composable
private fun MainExercisesCard(
    uiState: AnalyticsUiState,
    viewModel: AnalyticsViewModel,
    onExerciseClick: (String) -> Unit,
) {
    val card = uiState.mainExercises
    LogEzCard {
        Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            CardTitle(stringResource(R.string.analytics_main_exercises_title))
            RangeChips(card.range, viewModel::selectMainRange)
            if (card.rows.isEmpty()) {
                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
            } else {
                card.rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { onExerciseClick(row.exerciseId) }
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.exerciseName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(
                            pluralStringResource(R.plurals.analytics_exercise_times, row.workoutCount, row.workoutCount),
                            style = LogEzMono.dataSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        )
                    }
                }
            }
        }
    }
}

// --- card 6: monthly report entry ---

@Composable
private fun MonthlyReportEntryCard(onClick: () -> Unit) {
    LogEzCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                CardTitle(stringResource(R.string.analytics_monthly_report_title))
                Text(
                    stringResource(R.string.analytics_monthly_report_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
        }
    }
}
