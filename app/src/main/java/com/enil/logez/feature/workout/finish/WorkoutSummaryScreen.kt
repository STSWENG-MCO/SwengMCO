package com.enil.logez.feature.workout.finish

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.Gold500
import com.enil.logez.core.designsystem.LineChart
import com.enil.logez.core.designsystem.LineChartPoint
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.MuscleBalanceRadar
import com.enil.logez.core.designsystem.MuscleSilhouette
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.StatCell
import com.enil.logez.core.domain.calc.WorkoutMuscleTargetCalculator
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.GpsActivity
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.feature.activity.map.RouteMapView
import com.enil.logez.feature.history.formatCardDateTime
import com.enil.logez.feature.workout.finish.share.GpsShareCardData
import com.enil.logez.feature.workout.finish.share.ShareCardData
import com.enil.logez.feature.workout.finish.share.ShareSummaryDialog

/**
 * PHASE2_PLAN.md §5.1.8(c) post-save summary, plus the local-only summary-card export: the share
 * button renders the summary as a branded PNG and hands it to the system share sheet — no network,
 * no social SDKs; the image leaves the device only through the target the user picks there. Back
 * is intercepted to mean Done: the workout is already saved, so re-entering the finish screen
 * behind it would offer to save something that no longer exists.
 *
 * A GPS-tracked walk/run uses the walk/run slides ([GpsOverviewSlide]) instead (2026-09-26). This route draws
 * behind the status bar (LogEzApp leaves out the top inset for it) so that summary's map can run
 * up to the top edge; the strength layout pads itself back down below the status bar. The inner
 * Scaffold adds no insets of its own: it used to add the status bar a second time on top of the
 * app Scaffold's, leaving a ~95dp empty band above the heading.
 */
@Composable
fun WorkoutSummaryScreen(
    onDone: () -> Unit,
    viewModel: WorkoutSummaryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Saveable so a rotation with the dialog open re-opens it instead of silently swallowing the tap.
    var showShareDialog by rememberSaveable { mutableStateOf(false) }
    var showFullMap by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onDone)
    // Heart rate often reaches Health Connect after Save; coming back here (for instance after
    // opening Samsung Health to sync the watch) looks again and adds whatever arrived.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshHeartRate() }

    Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
        if (uiState.isLoading) return@Scaffold

        // W3-02a: every summary (strength or walk/run) is a pager of slides, with Share and Done
        // fixed underneath so they're reachable from any slide.
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                SummaryPager(slides = uiState.summarySlides(), modifier = Modifier.weight(1f)) { slide ->
                    if (uiState.isGpsTracked) {
                        GpsSummarySlide(slide, uiState, onOpenMap = { showFullMap = true })
                    } else {
                        StrengthSummarySlide(slide, uiState)
                    }
                }
                SummaryActions(onShare = { showShareDialog = true }, onDone = onDone)
            }
            // Drawn over the summary rather than in place of it, so closing the map returns to the
            // same slide and scroll position with the hero map still loaded.
            if (uiState.isGpsTracked && showFullMap && uiState.routePoints.isNotEmpty()) {
                // Composed after the screen's own BackHandler, so Back closes the map first.
                BackHandler { showFullMap = false }
                FullScreenRouteMap(routePoints = uiState.routePoints, onClose = { showFullMap = false })
            }
        }

        if (showShareDialog) {
            val gpsShare =
                if (uiState.isGpsTracked) {
                    val km = uiState.distanceUnit == DistanceUnit.KM
                    GpsShareCardData(
                        eyebrow =
                            stringResource(
                                when (uiState.gpsActivity) {
                                    GpsActivity.RUN -> R.string.share_card_run_complete
                                    GpsActivity.WALK -> R.string.share_card_walk_complete
                                    GpsActivity.OTHER -> R.string.summary_title
                                },
                            ),
                        routePoints = uiState.routePoints,
                        distanceNumber = if (uiState.hasDistance) formatDistanceNumber(uiState.totalDistanceMeters, uiState.distanceUnit) else null,
                        distanceUnitLabel = if (km) "km" else "mi",
                        timeText =
                            com.enil.logez.core.designsystem
                                .formatElapsedClock(uiState.durationSeconds),
                        paceText =
                            uiState.averagePaceSecondsPerUnit?.let {
                                com.enil.logez.core.designsystem
                                    .formatPace(it)
                            },
                        paceLabel = stringResource(if (km) R.string.share_card_pace_km else R.string.share_card_pace_mi),
                        avgBpmText = uiState.heartRateSummary?.averageBpm?.toString(),
                        prLines = uiState.prMedals.map { medal -> stringResource(medal.prType.labelRes()) to formatGpsPrValue(medal, uiState.distanceUnit) },
                    )
                } else {
                    null
                }
            ShareSummaryDialog(
                data =
                    ShareCardData(
                        title = uiState.title,
                        // A shared image outlives "Today", so a walk/run card carries the full date.
                        dateLine = if (uiState.isGpsTracked) formatFullDateTime(uiState.startedAtMillis) else formatCardDateTime(uiState.startedAtMillis),
                        durationText = formatDuration(uiState.durationSeconds),
                        volumeText = if (uiState.hasVolume) formatVolume(uiState.totalVolumeKg, uiState.weightUnit) else null,
                        setsText = uiState.completedSetCount.toString(),
                        repsText = if (uiState.hasReps) uiState.totalReps.toString() else null,
                        distanceText = if (uiState.hasDistance) formatDistance(uiState.totalDistanceMeters, uiState.distanceUnit) else null,
                        muscleIntensity = uiState.muscleIntensity,
                        muscleDiagramVariant = uiState.muscleDiagramVariant,
                        muscleBalance = uiState.muscleBalance,
                        prs = uiState.prMedals,
                        // CIRCUIT drops the "N × " prefix — the card's own rounds line already says
                        // how many times the sequence ran; REGULAR keeps History's shape.
                        exerciseLines =
                            uiState.exerciseLines.map { line ->
                                val base = if (uiState.structure == WorkoutStructure.CIRCUIT) line.name else "${line.setCount} × ${line.name}"
                                line.avgReps?.let { base + " · " + stringResource(R.string.share_card_avg_reps, formatAvgReps(it)) } ?: base
                            },
                        structure = uiState.structure,
                        rounds = uiState.rounds,
                        gps = gpsShare,
                    ),
                onDismiss = { showShareDialog = false },
            )
        }
    }
}

/** Which slides this workout has data for; see [summarySlides]. */
internal fun WorkoutSummaryUiState.summarySlides(): List<SummarySlide> =
    summarySlides(
        hasMuscles = WorkoutMuscleTargetCalculator.trainedGroups(muscleIntensity).isNotEmpty(),
        hasHeartRate = if (isGpsTracked) heartRateSummary != null else heartRateSamples.isNotEmpty(),
        hasRecords = prMedals.isNotEmpty(),
    )

/** One strength-workout slide. Scrolls on its own when its content is taller than the page. */
@Composable
internal fun StrengthSummarySlide(
    slide: SummarySlide,
    uiState: WorkoutSummaryUiState,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (slide) {
            SummarySlide.OVERVIEW -> StrengthOverview(uiState)
            SummarySlide.MUSCLES -> {
                SlideHeading(stringResource(R.string.analytics_body_title))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MuscleSilhouette(
                        intensity = uiState.muscleIntensity,
                        variant = uiState.muscleDiagramVariant,
                        modifier = Modifier.weight(0.8f),
                    )
                    MuscleBalanceRadar(
                        shares = uiState.muscleBalance,
                        modifier = Modifier.weight(1.2f),
                        compact = true,
                    )
                }
            }
            SummarySlide.HEART_RATE -> {
                var selectedBpmIndex by rememberSaveable { mutableStateOf<Int?>(null) }
                SlideHeading(stringResource(R.string.summary_heart_rate_title))
                LineChart(
                    points = uiState.heartRateSamples.map { (recordedAt, bpm) -> LineChartPoint(x = recordedAt, y = bpm.toDouble()) },
                    yLabel = { "${it.toInt()}" },
                    xLabel = { formatChartElapsed(((it - uiState.startedAtMillis) / 1000).coerceAtLeast(0L)) },
                    selectedIndex = selectedBpmIndex,
                    onPointTap = { selectedBpmIndex = it },
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            SummarySlide.RECORDS -> {
                SlideHeading(stringResource(R.string.summary_prs_header))
                uiState.prMedals.forEach { medal -> PrMedalCard(medal, uiState.weightUnit, uiState.distanceUnit) }
            }
        }
    }
}

@Composable
private fun StrengthOverview(uiState: WorkoutSummaryUiState) {
    Text(
        stringResource(R.string.summary_title),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = Spacing.lg),
    )
    Text(uiState.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = Spacing.xs))
    if (uiState.structure == WorkoutStructure.CIRCUIT) {
        // M11: the same "CIRCUIT · N rounds" line the share card carries.
        Text(
            stringResource(R.string.routine_structure_chip_circuit) + " · " +
                pluralStringResource(R.plurals.routine_rounds_count, uiState.rounds, uiState.rounds),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        // Only a metric this workout actually logged gets a cell (each cell is independently gated).
        if (uiState.hasVolume) StatCell(value = formatVolume(uiState.totalVolumeKg, uiState.weightUnit), label = stringResource(R.string.summary_volume), modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, valueStyle = LogEzMono.dataLarge, labelColor = MaterialTheme.colorScheme.onSurfaceVariant, labelTextAlign = TextAlign.Center)
        StatCell(value = uiState.completedSetCount.toString(), label = stringResource(R.string.summary_sets), modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, valueStyle = LogEzMono.dataLarge, labelColor = MaterialTheme.colorScheme.onSurfaceVariant, labelTextAlign = TextAlign.Center)
        if (uiState.hasReps) StatCell(value = uiState.totalReps.toString(), label = stringResource(R.string.summary_reps), modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, valueStyle = LogEzMono.dataLarge, labelColor = MaterialTheme.colorScheme.onSurfaceVariant, labelTextAlign = TextAlign.Center)
        if (uiState.hasDistance) StatCell(value = formatDistance(uiState.totalDistanceMeters, uiState.distanceUnit), label = stringResource(R.string.summary_distance), modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, valueStyle = LogEzMono.dataLarge, labelColor = MaterialTheme.colorScheme.onSurfaceVariant, labelTextAlign = TextAlign.Center)
        StatCell(value = formatDuration(uiState.durationSeconds), label = stringResource(R.string.summary_duration), modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, valueStyle = LogEzMono.dataLarge, labelColor = MaterialTheme.colorScheme.onSurfaceVariant, labelTextAlign = TextAlign.Center)
    }

    if (uiState.routePoints.isNotEmpty()) {
        // M21c: the real offline map (decisions.md 2026-09-10).
        RouteMapView(
            routePoints = uiState.routePoints,
            followLatest = false,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .padding(top = Spacing.lg)
                    .clip(RoundedCornerShape(Radius.sm)),
        )
    }
}

/**
 * One walk/run slide. The overview keeps its edge-to-edge map behind the status bar; the other
 * slides pad below it like the strength slides do.
 */
@Composable
internal fun GpsSummarySlide(
    slide: SummarySlide,
    uiState: WorkoutSummaryUiState,
    onOpenMap: () -> Unit,
) {
    if (slide == SummarySlide.OVERVIEW) {
        GpsOverviewSlide(uiState = uiState, onOpenMap = onOpenMap)
        return
    }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.md),
    ) {
        when (slide) {
            SummarySlide.HEART_RATE ->
                uiState.heartRateSummary?.let { summary ->
                    HeartRateCard(summary, uiState.startedAtMillis, Modifier.padding(top = Spacing.md))
                }
            SummarySlide.RECORDS -> {
                CardHeading(stringResource(R.string.summary_prs_header), Modifier.padding(top = Spacing.lg, bottom = Spacing.sm))
                uiState.prMedals.forEach { medal -> GpsPrMedalCard(medal, uiState.distanceUnit) }
            }
            // A walk/run has no muscle data, so summarySlides() never yields MUSCLES for it.
            SummarySlide.OVERVIEW, SummarySlide.MUSCLES -> Unit
        }
    }
}

@Composable
private fun SlideHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.lg, bottom = Spacing.sm),
    )
}

/** Share and Done, fixed under the pager. */
@Composable
private fun SummaryActions(
    onShare: () -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(bottom = Spacing.md)) {
        OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
            Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.summary_share), modifier = Modifier.padding(start = Spacing.xs))
        }
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
            Text(stringResource(R.string.summary_done))
        }
    }
}

/**
 * Highlights a personal record on the post-workout summary. Scoped to exactly this one
 * celebratory moment, not applied anywhere else a PR could appear (the live in-session PR
 * banner is untouched).
 */
@Composable
private fun PrMedalCard(
    medal: PrMedal,
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        border = BorderStroke(1.5.dp, Gold500),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Gold500)
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
                Text(medal.exerciseName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(stringResource(medal.prType.labelRes()), style = MaterialTheme.typography.bodySmall)
            }
            Text(formatPrValue(medal, weightUnit, distanceUnit), style = LogEzMono.dataLarge)
        }
    }
}

/** Whole numbers stay whole ("8"); fractional averages keep one honest decimal ("6.5"). */
private fun formatAvgReps(value: Double): String = formatSummaryNumber(value)

private fun formatVolume(
    kg: Double,
    unit: WeightUnit,
): String = formatSummaryVolume(kg, unit)

private fun formatDistance(
    meters: Double,
    unit: DistanceUnit,
): String = formatSummaryDistance(meters, unit)

private fun formatDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** M21f: mm:ss elapsed-since-start, for the heart-rate chart's x-axis -- finer-grained than [formatDuration]'s hour/minute rounding, since a workout can be a few minutes long. */
private fun formatChartElapsed(totalSeconds: Long): String {
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%d:%02d".format(java.util.Locale.ROOT, m, s)
}

/**
 * Reps-based records are whole numbers; everything else carries a unit. Time uses the clock format
 * the walk/run summary uses, which keeps hours ("1:15:03", not "75:03"). Distance stays in meters
 * here: on a strength summary it is a carry or a sled push, where "20m" reads better than
 * "0.02 km". A walk/run's distance record renders on the walk/run summary in km/mi instead.
 */
private fun formatPrValue(
    medal: PrMedal,
    weightUnit: WeightUnit,
    distanceUnit: DistanceUnit,
): String =
    when (medal.prType) {
        com.enil.logez.core.domain.model.PrType.MOST_REPS_SET,
        com.enil.logez.core.domain.model.PrType.MOST_SESSION_REPS,
        -> medal.value.toInt().toString()
        com.enil.logez.core.domain.model.PrType.BEST_TIME,
        com.enil.logez.core.domain.model.PrType.LONGEST_TIME,
        -> formatGpsPrValue(medal, distanceUnit)
        com.enil.logez.core.domain.model.PrType.LONGEST_DISTANCE -> "${formatSummaryNumber(medal.value)}m"
        else -> formatVolume(medal.value, weightUnit)
    }
