package com.enil.logez.feature.workout.finish

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.enil.logez.R
import com.enil.logez.core.designsystem.Gold500
import com.enil.logez.core.designsystem.LineChart
import com.enil.logez.core.designsystem.LineChartPoint
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.StatCell
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.designsystem.formatElapsedClock
import com.enil.logez.core.designsystem.formatPace
import com.enil.logez.core.designsystem.rememberClockTimeFormatter
import com.enil.logez.core.domain.calc.DistanceDisplay
import com.enil.logez.core.domain.calc.HeartRateSummary
import com.enil.logez.core.domain.calc.HeartRateZone
import com.enil.logez.core.domain.calc.RouteSplit
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.GpsActivity
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.feature.activity.heartRateZoneLabel
import com.enil.logez.feature.activity.map.RouteMapView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Map height below the status bar; the map itself runs up behind the status bar too. */
private val HERO_MAP_HEIGHT = 280.dp

/** The bottom fade that blends the map into the page, and the gap the credit line sits above. */
private val HERO_FADE_HEIGHT = 56.dp

/**
 * The finish summary for a GPS-tracked walk/run (Owner-approved mockup, 2026-09-26): the recorded
 * route first, then distance, time, pace, splits and heart rate. No muscle diagram, balance radar
 * or set count; those describe a gym session and said nothing true about a run.
 *
 * Every card is gated on data actually existing. A run without a watch simply has no heart-rate
 * card, and a run tracked before route times were saved has no pace card, rather than showing
 * empty placeholders (the app's honest-absence rule for summaries).
 */
@Composable
internal fun GpsWorkoutSummary(
    uiState: WorkoutSummaryUiState,
    onOpenMap: () -> Unit,
    onShare: () -> Unit,
    onDone: () -> Unit,
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val scroll = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
            if (uiState.routePoints.isNotEmpty()) {
                HeroRouteMap(routePoints = uiState.routePoints, statusBarTop = statusBarTop, onOpenMap = onOpenMap)
            } else {
                Spacer(Modifier.height(statusBarTop + Spacing.lg))
            }

            Column(modifier = Modifier.padding(horizontal = Spacing.md).padding(bottom = Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActivityChip(uiState.gpsActivity)
                    Text(
                        formatFullDateTime(uiState.startedAtMillis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Spacing.xs + 2.dp),
                    )
                }
                Text(
                    uiState.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 10.dp),
                )

                if (uiState.routePoints.isEmpty()) {
                    Text(
                        stringResource(if (uiState.hasTrack) R.string.summary_gps_route_empty else R.string.summary_gps_route_interrupted),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.sm),
                    )
                }

                if (uiState.hasDistance) {
                    HeroDistance(uiState.totalDistanceMeters, uiState.distanceUnit, Modifier.padding(top = 22.dp))
                }

                StatsCard(uiState, Modifier.padding(top = Spacing.md))

                if (uiState.splits.isNotEmpty() || uiState.paceSeries.isNotEmpty()) {
                    PaceCard(uiState, Modifier.padding(top = Spacing.md))
                }

                uiState.heartRateSummary?.let { summary ->
                    HeartRateCard(summary, uiState.startedAtMillis, Modifier.padding(top = Spacing.md))
                }

                if (uiState.prMedals.isNotEmpty()) {
                    CardHeading(stringResource(R.string.summary_prs_header), Modifier.padding(top = Spacing.lg, bottom = Spacing.sm))
                    uiState.prMedals.forEach { medal -> GpsPrMedalCard(medal, uiState.distanceUnit) }
                }

                OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.summary_share), modifier = Modifier.padding(start = Spacing.xs))
                }
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                    Text(stringResource(R.string.summary_done))
                }
            }
        }
        StatusBarBackdrop(scroll, statusBarTop)
    }
}

/**
 * The page scrolls up behind the status bar, so scrolled text would run into the clock. At the top
 * the map's own scrim keeps the icons readable and the map shows through; this solid strip fades
 * in over the first stretch of scrolling instead, so it never hides the map at rest.
 */
@Composable
private fun StatusBarBackdrop(scroll: ScrollState, statusBarTop: Dp) {
    val rampPx = with(LocalDensity.current) { 96.dp.toPx() }
    val alpha by remember { derivedStateOf { (scroll.value / rampPx).coerceIn(0f, 1f) } }
    Box(
        Modifier.fillMaxWidth().height(statusBarTop)
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.background),
    )
}

/**
 * The route, edge to edge and up behind the status bar. It doesn't pan or zoom here: inside a
 * scrolling page a draggable map takes the drag and the page stops scrolling. A tap, or the
 * corner button, opens [FullScreenRouteMap], where it does.
 */
@Composable
private fun HeroRouteMap(routePoints: List<Pair<Double, Double>>, statusBarTop: Dp, onOpenMap: () -> Unit) {
    val background = MaterialTheme.colorScheme.background
    Box(modifier = Modifier.fillMaxWidth().height(statusBarTop + HERO_MAP_HEIGHT)) {
        RouteMapView(
            routePoints = routePoints,
            followLatest = false,
            interactive = false,
            onMapClick = onOpenMap,
            fitPadding = PaddingValues(start = 28.dp, top = statusBarTop + 48.dp, end = 28.dp, bottom = HERO_FADE_HEIGHT + 20.dp),
            attributionPadding = PaddingValues(end = Spacing.xs, bottom = HERO_FADE_HEIGHT + 2.dp),
            modifier = Modifier.fillMaxSize(),
        )
        // Keeps the status bar's light icons readable over bright map areas.
        Box(
            Modifier.fillMaxWidth().height(statusBarTop + 56.dp)
                .background(Brush.verticalGradient(listOf(background.copy(alpha = 0.82f), background.copy(alpha = 0f)))),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(HERO_FADE_HEIGHT)
                .background(Brush.verticalGradient(listOf(background.copy(alpha = 0f), background))),
        )
        MapRoundButton(
            icon = { Icon(Icons.Outlined.OpenInFull, contentDescription = stringResource(R.string.summary_gps_open_map), modifier = Modifier.size(18.dp)) },
            onClick = onOpenMap,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = statusBarTop + Spacing.sm, end = Spacing.md),
        )
    }
}

/** The same route, full screen, with every gesture on. Back or the close button returns. */
@Composable
internal fun FullScreenRouteMap(routePoints: List<Pair<Double, Double>>, onClose: () -> Unit) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        RouteMapView(
            routePoints = routePoints,
            followLatest = false,
            interactive = true,
            fitPadding = PaddingValues(start = 40.dp, top = statusBarTop + 72.dp, end = 40.dp, bottom = 72.dp),
            attributionPadding = PaddingValues(end = Spacing.xs, bottom = Spacing.xs),
            modifier = Modifier.fillMaxSize(),
        )
        MapRoundButton(
            icon = { Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.summary_gps_close_map), modifier = Modifier.size(20.dp)) },
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopStart).padding(top = statusBarTop + Spacing.sm, start = Spacing.md),
        )
    }
}

@Composable
private fun MapRoundButton(icon: @Composable () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(contentAlignment = Alignment.Center) { icon() }
    }
}

@Composable
private fun ActivityChip(activity: GpsActivity) {
    val label = stringResource(
        when (activity) {
            GpsActivity.RUN -> R.string.summary_gps_chip_run
            GpsActivity.WALK -> R.string.summary_gps_chip_walk
            GpsActivity.OTHER -> R.string.summary_gps_chip_other
        },
    )
    Text(
        label.uppercase(currentLocale()),
        style = LogEzMono.dataSmall.copy(fontWeight = FontWeight.Medium, letterSpacing = 0.08.em),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = Spacing.xxs),
    )
}

@Composable
private fun HeroDistance(meters: Double, unit: DistanceUnit, modifier: Modifier = Modifier) {
    val unitLabel = if (unit == DistanceUnit.KM) "km" else "mi"
    val number = formatDistanceNumber(meters, unit)
    Column(modifier = modifier.semantics(mergeDescendants = true) {}) {
        CapsLabel(stringResource(R.string.summary_gps_distance))
        Row(modifier = Modifier.padding(top = 2.dp)) {
            Text(
                number,
                style = LogEzMono.dataLarge.copy(fontSize = 60.sp, lineHeight = 64.sp, letterSpacing = (-0.03).em),
                modifier = Modifier.alignByBaseline(),
            )
            Text(
                unitLabel,
                style = LogEzMono.dataLarge.copy(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline().padding(start = Spacing.xs),
            )
        }
    }
}

/** Time, average pace, average speed: the live tracking screen's three-up row, kept after the run. */
@Composable
private fun StatsCard(uiState: WorkoutSummaryUiState, modifier: Modifier = Modifier) {
    val km = uiState.distanceUnit == DistanceUnit.KM
    val cells = buildList {
        add(formatElapsedClock(uiState.durationSeconds) to stringResource(R.string.summary_gps_time))
        uiState.averagePaceSecondsPerUnit?.let {
            add(formatPace(it) to stringResource(if (km) R.string.summary_gps_avg_pace_km else R.string.summary_gps_avg_pace_mi))
        }
        uiState.averageSpeedPerHour?.let {
            add(formatOneDecimal(it) to stringResource(if (km) R.string.summary_gps_avg_speed_km else R.string.summary_gps_avg_speed_mi))
        }
    }
    LogEzCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = Spacing.xs, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            cells.forEachIndexed { i, (value, label) ->
                if (i > 0) {
                    VerticalDivider(modifier = Modifier.fillMaxHeight().padding(vertical = Spacing.xxs), color = MaterialTheme.colorScheme.outlineVariant)
                }
                StatCell(
                    value = value,
                    label = label,
                    // Time gets a little more room and an hour-plus time ("1:02:30") a step smaller,
                    // so it fits a 360dp phone at large font sizes instead of being cut to "1:02…".
                    modifier = Modifier.weight(if (i == 0) 1.2f else 1f).padding(horizontal = Spacing.xxs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    valueStyle = if (value.length > 6) LogEzMono.dataLarge.copy(fontSize = 17.sp) else LogEzMono.dataLarge,
                    valueTextAlign = TextAlign.Center,
                    valueMaxLines = 1,
                    valueOverflow = TextOverflow.Ellipsis,
                    labelStyle = MaterialTheme.typography.bodyMedium,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    labelTextAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun PaceCard(uiState: WorkoutSummaryUiState, modifier: Modifier = Modifier) {
    val km = uiState.distanceUnit == DistanceUnit.KM
    val fullSplits = uiState.splits.filterNot { it.isPartial }
    val fastest = fullSplits.minByOrNull { it.paceSecondsPerUnit }
    LogEzCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            CardHeadingRow(
                title = stringResource(R.string.summary_gps_pace_header),
                meta = fastest?.let { stringResource(if (km) R.string.summary_gps_fastest_km else R.string.summary_gps_fastest_mi) + " " + formatPace(it.paceSecondsPerUnit) },
            )
            if (uiState.paceSeries.isNotEmpty()) {
                var selected by rememberSaveable(uiState.paceSeries) { mutableStateOf<Int?>(null) }
                LineChart(
                    points = uiState.paceSeries.map { (at, pace) -> LineChartPoint(x = at, y = pace) },
                    yLabel = { formatPace(it) },
                    xLabel = { formatElapsedClock(((it - uiState.startedAtMillis) / 1000).toInt()) },
                    selectedIndex = selected,
                    onPointTap = { selected = it },
                    showPoints = false,
                    modifier = Modifier.padding(top = Spacing.sm),
                )
            }
            if (uiState.splits.isNotEmpty()) {
                SplitsTable(uiState.splits, fastest, km, Modifier.padding(top = 14.dp))
            }
        }
    }
}

@Composable
private fun SplitsTable(splits: List<RouteSplit>, fastest: RouteSplit?, km: Boolean, modifier: Modifier = Modifier) {
    val showBpm = splits.any { it.averageBpm != null }
    val speeds = splits.map { 1.0 / it.paceSecondsPerUnit }
    val fastestSpeed = speeds.max()
    val slowestSpeed = speeds.min()
    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            CapsLabel(stringResource(if (km) R.string.summary_gps_split_col_km else R.string.summary_gps_split_col_mi), Modifier.width(44.dp))
            CapsLabel(stringResource(R.string.summary_gps_split_col_pace), Modifier.width(56.dp))
            Spacer(Modifier.weight(1f))
            if (showBpm) CapsLabel(stringResource(R.string.summary_gps_split_col_bpm), Modifier.width(44.dp), textAlign = TextAlign.End)
        }
        splits.forEachIndexed { i, split ->
            val label = if (split.isPartial) formatDistanceNumber(split.distanceMeters, if (km) DistanceUnit.KM else DistanceUnit.MILES) else split.number.toString()
            val pace = formatPace(split.paceSecondsPerUnit)
            val description = split.averageBpm?.let { stringResource(R.string.summary_gps_split_bpm_a11y, label, pace, it.toString()) }
                ?: stringResource(R.string.summary_gps_split_a11y, label, pace)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // Minimum sizes, not fixed ones: at a large font scale the row and its columns grow and
            // the bar gives up the width.
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 34.dp).clearAndSetSemantics { contentDescription = description },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = LogEzMono.dataMedium,
                    color = if (split.isPartial) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.widthIn(min = 44.dp),
                )
                Text(
                    pace,
                    style = LogEzMono.dataMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.widthIn(min = 56.dp).padding(start = Spacing.xxs),
                )
                // Relative, not absolute: the slowest split gets a little over half the width and the
                // fastest all of it, so a few seconds' difference is visible at all.
                val fraction = if (fastestSpeed > slowestSpeed) {
                    0.55f + 0.45f * ((speeds[i] - slowestSpeed) / (fastestSpeed - slowestSpeed)).toFloat()
                } else {
                    1f
                }
                Box(modifier = Modifier.weight(1f).padding(end = Spacing.xs)) {
                    Box(
                        Modifier.fillMaxWidth(fraction).height(10.dp).clip(RoundedCornerShape(Radius.pill))
                            .background(
                                if (split == fastest) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            ),
                    )
                }
                if (showBpm) {
                    Text(
                        split.averageBpm?.toString() ?: "",
                        style = LogEzMono.dataMedium.copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.widthIn(min = 44.dp),
                    )
                }
            }
        }
    }
}

/** Also shown on History's workout detail, so a run's heart rate looks the same in both places. */
@Composable
internal fun HeartRateCard(summary: HeartRateSummary, startedAtMillis: Long, modifier: Modifier = Modifier) {
    LogEzCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            CardHeadingRow(
                title = stringResource(R.string.summary_gps_heart_rate_header),
                meta = stringResource(R.string.summary_gps_heart_rate_source),
            )
            Row(modifier = Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                BpmStat(summary.averageBpm, stringResource(R.string.summary_gps_avg_bpm))
                BpmStat(summary.maxBpm, stringResource(R.string.summary_gps_max_bpm))
            }
            var selected by rememberSaveable(summary.samples) { mutableStateOf<Int?>(null) }
            LineChart(
                points = summary.samples.map { (at, bpm) -> LineChartPoint(x = at, y = bpm.toDouble()) },
                yLabel = { it.roundToInt().toString() },
                xLabel = { formatElapsedClock(((it - startedAtMillis) / 1000).toInt()) },
                selectedIndex = selected,
                onPointTap = { selected = it },
                showPoints = false,
                modifier = Modifier.padding(top = Spacing.sm),
            )
            Text(
                stringResource(R.string.summary_gps_zones_header),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 18.dp),
            )
            val zones = summary.zoneSeconds
            if (zones != null) {
                val longest = zones.values.maxOrNull()?.takeIf { it > 0 } ?: 1
                Column(modifier = Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    HeartRateZone.entries.reversed().forEach { zone ->
                        ZoneRow(zone, zones[zone] ?: 0, longest)
                    }
                }
            }
            Text(
                if (zones != null && summary.maxHeartRateSetting != null) {
                    stringResource(R.string.summary_gps_zones_basis, summary.maxHeartRateSetting)
                } else {
                    stringResource(R.string.summary_gps_zones_missing)
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.sm),
            )
        }
    }
}

@Composable
private fun BpmStat(bpm: Long, label: String) {
    StatCell(
        value = bpm.toString(),
        label = label,
        valueStyle = LogEzMono.dataLarge,
        labelStyle = MaterialTheme.typography.bodyMedium,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** One zone: its number and name, a bar against the longest zone, and the time. Lighter green for easier zones. */
@Composable
private fun ZoneRow(zone: HeartRateZone, seconds: Int, longestSeconds: Int) {
    val name = heartRateZoneLabel(zone)
    val time = formatElapsedClock(seconds)
    val description = stringResource(R.string.summary_gps_zone_a11y, zone.number, name, time)
    val alpha = when (zone) {
        HeartRateZone.ZONE_1 -> 0.28f
        HeartRateZone.ZONE_2 -> 0.42f
        HeartRateZone.ZONE_3 -> 0.58f
        HeartRateZone.ZONE_4 -> 0.78f
        HeartRateZone.ZONE_5 -> 1f
    }
    Row(
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.width(100.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Z${zone.number}", style = LogEzMono.dataMedium.copy(fontSize = 12.sp))
            Text(
                name,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        Box(
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.xs).height(8.dp)
                .clip(RoundedCornerShape(Radius.pill)).background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (seconds > 0) {
                Box(
                    Modifier.fillMaxWidth((seconds.toFloat() / longestSeconds).coerceIn(0.03f, 1f)).fillMaxHeight()
                        .clip(RoundedCornerShape(Radius.pill)).background(MaterialTheme.colorScheme.primary.copy(alpha = alpha)),
                )
            }
        }
        // At least 48dp, growing for an hour-plus zone ("1:05:00" is wider) rather than wrapping.
        Text(
            time,
            style = LogEzMono.dataMedium.copy(fontSize = 12.sp),
            textAlign = TextAlign.End,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 48.dp),
        )
    }
}

/** A record on a walk/run summary: the record type leads, since the exercise is the run itself. */
@Composable
private fun GpsPrMedalCard(medal: PrMedal, distanceUnit: DistanceUnit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.sm),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(1.5.dp, Gold500),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Gold500)
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.sm)) {
                Text(stringResource(medal.prType.labelRes()), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    medal.exerciseName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(formatGpsPrValue(medal, distanceUnit), style = LogEzMono.dataLarge)
        }
    }
}

@Composable
private fun CardHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(currentLocale()),
        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.05.em),
        modifier = modifier,
    )
}

@Composable
private fun CardHeadingRow(title: String, meta: String?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        CardHeading(title, Modifier.weight(1f))
        if (meta != null) {
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CapsLabel(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text.uppercase(currentLocale()),
        style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/**
 * "Sat, 26 Sep 2026 · 6:12 AM", clock following the phone's 12/24-hour setting. The walk/run
 * summary and its share image use the full date, as the approved mockup does; a shared image in
 * particular outlives a relative "Today".
 */
@Composable
internal fun formatFullDateTime(millis: Long): String {
    val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val date = zoned.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy", currentLocale()))
    return date + " · " + zoned.format(rememberClockTimeFormatter())
}

/**
 * Two decimals, always ("4.62", "0.54", "10.00"), `Locale.ROOT`: the walk/run hundredths rule
 * (Owner, 2026-09-23), and the same shape the live tracking screen ticks in.
 */
internal fun formatDistanceNumber(meters: Double, unit: DistanceUnit): String =
    "%.2f".format(Locale.ROOT, (DistanceDisplay.toDisplay(meters, unit) * 100).roundToInt() / 100.0)

internal fun formatOneDecimal(value: Double): String = "%.1f".format(Locale.ROOT, value)

/**
 * A walk/run record in the user's unit. The generic summary used to print LONGEST_DISTANCE as raw
 * meters ("537.41m") whatever the km/mi setting, and LONGEST_TIME without hours ("75:03").
 */
internal fun formatGpsPrValue(medal: PrMedal, distanceUnit: DistanceUnit): String = when (medal.prType) {
    PrType.LONGEST_DISTANCE -> formatDistanceNumber(medal.value, distanceUnit) + if (distanceUnit == DistanceUnit.KM) " km" else " mi"
    PrType.LONGEST_TIME, PrType.BEST_TIME -> formatElapsedClock(medal.value.toInt())
    else -> formatSummaryNumber(medal.value)
}
