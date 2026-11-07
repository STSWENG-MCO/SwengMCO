package com.enil.logez.feature.activity

import com.enil.logez.core.wellness.HealthDataType
import com.enil.logez.core.wellness.openHealthConnectInPlayStore
import com.enil.logez.core.wellness.openHealthConnectSettings
import com.enil.logez.core.wellness.rememberRequestHealthPermissions
import com.enil.logez.core.wellness.HeartRateAccess
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.ConfirmDialog
import com.enil.logez.core.designsystem.LineChart
import com.enil.logez.core.designsystem.LineChartPoint
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.StatCell
import com.enil.logez.core.designsystem.formatElapsedClock
import com.enil.logez.core.designsystem.formatMmSs
import com.enil.logez.core.designsystem.formatPace
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.domain.calc.DistanceDisplay
import com.enil.logez.core.domain.calc.HeartRateZone
import com.enil.logez.core.domain.calc.HeartRateZoneCalculator
import com.enil.logez.core.domain.calc.PaceCalculator
import com.enil.logez.core.domain.model.DistanceUnit
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.enil.logez.core.wellness.HeartRateSample
import com.enil.logez.feature.activity.map.RouteMapView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.enil.logez.core.designsystem.rememberClockTimeFormatter

/**
 * M21 redesign (2026-09-11, decisions.md same date): Finish goes straight to the Save Workout
 * screen (`onFinished`), never through the strength Logger — a GPS-tracked walk/run has nothing
 * for the Logger's sets/reps table to show, and routing through it just added a screen and a
 * detour into `WorkoutSessionService` (the strength-session foreground service, which the
 * Logger-hand-off path used to start via `rememberStartWorkoutSession`) that this flow no longer
 * needs. `ActivityTrackingService` is the only foreground service this screen ever runs, stopped
 * right below on both Finish and Cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityTrackingScreen(
    onFinished: (workoutId: String) -> Unit,
    onCancelled: () -> Unit,
    viewModel: ActivityTrackingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val elapsedSeconds by viewModel.elapsedSecondsFlow.collectAsStateWithLifecycle(initialValue = 0)
    val heartRateAccess by viewModel.heartRateAccessFlow.collectAsStateWithLifecycle(initialValue = null)
    // Asks for heart rate alone. If Health Connect answers without heart rate (it was refused
    // before, so no dialog showed), the button switches to opening Health Connect's settings,
    // where it can still be turned on; otherwise the button could silently do nothing.
    val heartRatePermission = remember { viewModel.healthMetricsSource.permissionFor(HealthDataType.HEART_RATE) }
    var heartRateRequestRefused by rememberSaveable { mutableStateOf(false) }
    val requestHeartRate = rememberRequestHealthPermissions(setOf(heartRatePermission)) { granted ->
        val allowed = heartRatePermission in granted
        heartRateRequestRefused = !allowed
        viewModel.onHeartRatePermissionResult(allowed)
    }
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var showCancelConfirm by remember { mutableStateOf(false) }

    val paceSecondsPerUnit = PaceCalculator.paceSecondsPerUnit(state.distanceMeters, elapsedSeconds, settings.distanceUnit)

    // The vitals card's two historical charts. Heart-rate history is Health-Connect-sourced (re-
    // queried each poll, see liveHeartRateHistoryFlow's own doc comment); pace history is derived
    // from the controller's own periodic distance samples, one PaceCalculator call per consecutive
    // pair rather than the cumulative total -- a long run's lifetime-average pace barely moves, so
    // only a recent-window delta shows an actually useful trend on the chart.
    val startedAtMillis = state.startedAtMillis
    val heartRateHistory: List<HeartRateSample> by remember(startedAtMillis) {
        startedAtMillis?.let(viewModel::heartRateHistoryFlow) ?: flowOf(emptyList())
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val heartRateChartPoints = heartRateHistory.map { LineChartPoint(x = it.time.toEpochMilli(), y = it.bpm.toDouble()) }
    // The newest reading of this session, however old: a watch's heart rate reaches Health Connect
    // in batches, so the "as of" time under it says how far behind it is (2026-09-26).
    val liveBpm = heartRateHistory.lastOrNull()
    val heartRateZone = liveBpm?.let { HeartRateZoneCalculator.zoneFor(it.bpm, settings.maxHeartRateBpm) }
    val nowMillis = (startedAtMillis ?: 0L) + elapsedSeconds * 1000L
    val liveBpmIsStale = liveBpm != null && nowMillis - liveBpm.time.toEpochMilli() > STALE_READING_MILLIS
    val paceChartPoints = state.distanceHistory.zipWithNext { (t1, d1), (t2, d2) ->
        PaceCalculator.windowedPaceSecondsPerUnit(d2 - d1, ((t2 - t1) / 1000).toInt(), settings.distanceUnit)?.let { LineChartPoint(x = t2, y = it) }
    }.filterNotNull()
    // Keyed on the point list, not a bare `remember {}`: liveHeartRateHistoryFlow re-queries the
    // whole window every poll specifically so a delayed Health Connect sync can backfill an
    // earlier sample into the middle of the list, which shifts every later sample's index -- the
    // same stale-index-into-a-reshuffled-list hazard ExerciseDetailScreen's own selectedPointIndex
    // already guards against (`remember(summary.points) {...}`). A structurally-equal list on a
    // repeat poll (the common case) compares equal, so the selection only actually resets when the
    // content genuinely changes, not on every tick.
    var selectedHrIndex by rememberSaveable(heartRateChartPoints) { mutableStateOf<Int?>(null) }
    var selectedPaceIndex by rememberSaveable(paceChartPoints) { mutableStateOf<Int?>(null) }
    fun elapsedLabel(epochMillis: Long): String =
        formatMmSs((((epochMillis - (startedAtMillis ?: epochMillis)) / 1000).coerceAtLeast(0L)).toInt())

    fun finish() = scope.launch {
        val result = viewModel.finish()
        stopActivityTrackingService(context)
        if (result != null) onFinished(result.workoutId) else onCancelled()
    }

    fun cancel() = scope.launch {
        viewModel.cancel()
        stopActivityTrackingService(context)
        onCancelled()
    }

    BackHandler { showCancelConfirm = true }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { ScreenTitle(stringResource(R.string.activity_tracking_screen_title)) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // M21c: the real offline map, camera following the newest GPS fix as it arrives --
            // upgraded from the framework-free Canvas sketch (RouteSketchGeometry) once that spike
            // proved the concept, per the Owner's explicit choice (P-125/decisions.md 2026-09-10).
            // Originally filled all remaining vertical space (Owner request, 2026-09-11); revised
            // 2026-09-22 to a tall fixed height instead, since the vitals card below now needs
            // that space too. The Finish-summary and History Detail Route cards keep their own
            // fixed, smaller aspect-ratio sizing, untouched by this.
            //
            // Deliberately NOT wrapped in a shared verticalScroll with the card below: RouteMapView
            // is a native AndroidView with its own pan/zoom/scroll gestures fully enabled (a prior,
            // deliberate fix for a "can't move the map" report), and an embedded platform View's
            // touch handling claims the gesture stream ahead of an ancestor Compose scroll with no
            // nested-scroll bridge in this codebase to hand it back -- a drag starting on the map
            // would always pan it, never scroll the page, if the two shared one scrollable ancestor
            // (adversarial review, 2026-09-22). Giving the map a fixed, non-scrolling slot and the
            // card below it its own independent scroll region keeps both gestures unambiguous: a
            // drag on the map always pans the map, a drag on the card always scrolls the card.
            // BoxWithConstraints, not a fixed 280dp: everything else in this column is fixed-size
            // (app bar, stat row, buttons, paddings -- ~560dp together), so on a 640dp-tall budget
            // phone or in landscape a fixed map left the weighted card below it a hairline sliver
            // (adversarial review, 2026-09-23). The map takes up to 280dp but never more than
            // 45% of the space actually available to this block, so the card always keeps the
            // majority of what's left.
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val mapHeight = minOf(280.dp, maxHeight * 0.45f)
                Column(modifier = Modifier.fillMaxSize()) {
                RouteMapView(
                    routePoints = state.routePoints,
                    followLatest = true,
                    modifier = Modifier.fillMaxWidth().height(mapHeight).clip(RoundedCornerShape(Radius.sm)),
                )

                // Timer, distance and pace share one row (Owner request, 2026-09-11, extended
                // 2026-09-12 with pace), moved from above the map to directly below it on
                // 2026-09-23 (Owner request). Same session: the three used to be free-width
                // `displayMedium` columns under `SpaceEvenly`, and at that size the figures ran
                // into each other ("1:250.06 km23:38"); each is now an equal-width StatCell at
                // the recap screen's `dataLarge`, the same three-up shape the Finish summary uses.
                // Pace reads "—" rather than disappearing before enough distance has accumulated
                // (PaceCalculator.MIN_METERS_FOR_PACE), so the row never reflows mid-run. The
                // unit lives in the Distance *label* ("Distance (km)"), like Pace's "/km": a cell
                // is only 96dp on a 360dp phone, and "10.00 km" at dataLarge is exactly 96dp --
                // it wrapped at any larger system font size and reflowed the card below.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    LiveStat(
                        value = formatElapsed(elapsedSeconds),
                        label = stringResource(R.string.activity_tracking_elapsed_label),
                        modifier = Modifier.weight(1f),
                    )
                    LiveStat(
                        value = formatDistanceNumber(state.distanceMeters, settings.distanceUnit),
                        label = stringResource(
                            if (settings.distanceUnit == DistanceUnit.MILES) R.string.activity_tracking_distance_label_mi else R.string.activity_tracking_distance_label_km,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    LiveStat(
                        value = paceSecondsPerUnit?.let(::formatPace) ?: PLACEHOLDER,
                        label = stringResource(
                            if (settings.distanceUnit == DistanceUnit.MILES) R.string.activity_tracking_pace_label_mi else R.string.activity_tracking_pace_label_km,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }

                // BPM, its live zone, and their two historical charts, all one card below the
                // stats, scrolling within its own bounded space rather than sharing the map's
                // ancestor -- see the comment above. Every section is always present (Owner
                // request, 2026-09-23): this screen used to follow the app-wide honest-absence rule
                // (omit a stat with no data rather than show a dash), which here meant the whole
                // heart-rate half of the card was invisible until a watch was connected -- so a
                // user couldn't tell the feature existed. Each section instead shows a placeholder
                // that says what it's waiting on.
                LogEzCard(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = Spacing.md)) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(Spacing.md)) {
                        Text(stringResource(R.string.activity_tracking_vitals_header), style = MaterialTheme.typography.titleMedium)

                        // The zone needs its own further condition -- a max heart rate set in
                        // Settings -- so BPM alone (no zone) is a real, common state too, not a bug.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            // dataLarge, not displayMedium (45sp): each of these two columns is half
                            // a card, ~134dp on a 360dp phone, and "165 bpm" at 45sp is ~175dp --
                            // it wrapped to "165" / "bpm" on every common phone and left the two
                            // columns different heights (adversarial review, 2026-09-23).
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                // A display-sized em dash in the brand green reads as a solid bar,
                                // not an empty slot -- the placeholder takes the muted label color.
                                Text(
                                    liveBpm?.let { stringResource(R.string.workout_bpm_value, it.bpm) } ?: PLACEHOLDER,
                                    style = LogEzMono.dataLarge,
                                    color = if (liveBpm != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    stringResource(R.string.activity_tracking_bpm_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                // Owner-reported 2026-09-12: a wearable's readings reach Health
                                // Connect through a multi-hop sync (watch -> its companion app ->
                                // Health Connect), not in real time, so this can genuinely be
                                // several minutes old even while the watch face itself shows
                                // something fresher. Labeling it honestly beats implying
                                // live-instant accuracy it can't actually guarantee.
                                if (liveBpm != null) {
                                    Text(
                                        stringResource(R.string.activity_tracking_bpm_as_of, formatClockTime(liveBpm!!.time, rememberClockTimeFormatter())),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    heartRateZone?.let { stringResource(R.string.activity_tracking_zone_value, it.number) } ?: PLACEHOLDER,
                                    style = LogEzMono.dataLarge,
                                    color = if (heartRateZone != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    heartRateZone?.let { heartRateZoneLabel(it) } ?: stringResource(R.string.activity_tracking_zone_label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        // One message per real situation (2026-09-26). "Not allowed" and "nothing
                        // synced yet" used to share one message, so a missing permission looked
                        // exactly like a watch that hadn't synced.
                        val heartRateMessage = when {
                            heartRateAccess == HeartRateAccess.UNAVAILABLE -> R.string.activity_tracking_heart_rate_unavailable
                            heartRateAccess == HeartRateAccess.NEEDS_INSTALL_OR_UPDATE -> R.string.activity_tracking_heart_rate_needs_update
                            heartRateAccess == HeartRateAccess.NOT_GRANTED && heartRateRequestRefused -> R.string.activity_tracking_heart_rate_turn_on_in_settings
                            heartRateAccess == HeartRateAccess.NOT_GRANTED -> R.string.activity_tracking_heart_rate_not_allowed
                            liveBpm == null && heartRateAccess == HeartRateAccess.GRANTED -> R.string.activity_tracking_heart_rate_empty
                            liveBpmIsStale -> R.string.activity_tracking_heart_rate_stale
                            else -> null
                        }
                        if (heartRateMessage != null) {
                            Text(
                                stringResource(heartRateMessage),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                            )
                        }
                        when (heartRateAccess) {
                            HeartRateAccess.NOT_GRANTED -> TextButton(
                                onClick = { if (heartRateRequestRefused) openHealthConnectSettings(context) else requestHeartRate() },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(
                                    stringResource(
                                        if (heartRateRequestRefused) R.string.activity_tracking_heart_rate_open_settings else R.string.activity_tracking_heart_rate_allow,
                                    ),
                                )
                            }
                            HeartRateAccess.NEEDS_INSTALL_OR_UPDATE -> TextButton(
                                onClick = { openHealthConnectInPlayStore(context) },
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            ) {
                                Text(stringResource(R.string.activity_tracking_heart_rate_get_health_connect))
                            }
                            else -> Unit
                        }

                        Text(
                            stringResource(R.string.activity_tracking_heart_rate_chart_header),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = Spacing.md),
                        )
                        if (heartRateChartPoints.isNotEmpty()) {
                            LineChart(
                                points = heartRateChartPoints,
                                yLabel = { "${it.toInt()}" },
                                xLabel = ::elapsedLabel,
                                selectedIndex = selectedHrIndex,
                                onPointTap = { selectedHrIndex = it },
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        } else {
                            ChartPlaceholder(stringResource(R.string.activity_tracking_heart_rate_chart_empty))
                        }

                        Text(
                            stringResource(R.string.activity_tracking_pace_chart_header),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = Spacing.md),
                        )
                        if (paceChartPoints.isNotEmpty()) {
                            LineChart(
                                points = paceChartPoints,
                                yLabel = { formatPace(it) },
                                xLabel = ::elapsedLabel,
                                selectedIndex = selectedPaceIndex,
                                onPointTap = { selectedPaceIndex = it },
                                modifier = Modifier.padding(top = Spacing.sm),
                            )
                        } else {
                            ChartPlaceholder(stringResource(R.string.activity_tracking_pace_chart_empty))
                        }
                    }
                }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.xxl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                OutlinedButton(onClick = { showCancelConfirm = true }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.activity_tracking_cancel))
                }
                Button(onClick = ::finish, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.activity_tracking_finish))
                }
            }
        }
    }

    if (showCancelConfirm) {
        ConfirmDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = stringResource(R.string.activity_tracking_cancel_confirm_title),
            body = stringResource(R.string.activity_tracking_cancel_confirm_body),
            confirmLabel = stringResource(R.string.activity_tracking_cancel_confirm_action),
            onConfirm = ::cancel,
            dismissLabel = stringResource(R.string.action_cancel),
        )
    }
}

private fun formatElapsed(totalSeconds: Int): String = formatElapsedClock(totalSeconds)

/** What a stat reads while it has nothing to show yet -- the same dash every other stat surface uses. */
private const val PLACEHOLDER = "—"

/** A reading older than this gets the "your watch syncs in batches" note under it. */
private const val STALE_READING_MILLIS = 5 * 60_000L

/** One cell of the three-up live stat row: the recap screen's StatCell shape, centered. */
@Composable
private fun LiveStat(value: String, label: String, modifier: Modifier = Modifier) {
    StatCell(
        value = value,
        label = label,
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        valueStyle = LogEzMono.dataLarge,
        valueTextAlign = TextAlign.Center,
        // A numeric run has no break opportunity, so an over-wide "1:02:30" at a large system
        // font would otherwise be emergency-broken mid-number onto a second line.
        valueMaxLines = 1,
        valueOverflow = TextOverflow.Ellipsis,
        labelStyle = MaterialTheme.typography.bodyMedium,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        labelTextAlign = TextAlign.Center,
    )
}

@Composable
private fun ChartPlaceholder(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs, bottom = Spacing.xs),
    )
}

// Locale.ROOT: the default-locale overload renders "1,20" on comma-decimal devices. Returns just
// the number in the user's distance unit -- the "km"/"mi" lives in the cell's label, not here.
// Always two decimals while it ticks ("0.00", then "0.05"), matching the walk/run hundredths
// convention (Owner request, 2026-09-23); the recap's trimmed "0.##" would read as a jumpy
// "0" -> "0.1" -> "0.15" here.
private fun formatDistanceNumber(distanceMeters: Double, unit: DistanceUnit): String {
    val display = DistanceDisplay.toDisplay(distanceMeters, unit)
    return "%.2f".format(Locale.ROOT, (display * 100).roundToInt() / 100.0)
}

/** "7:44 PM" or "19:44", following the phone's 12/24-hour setting (see [rememberClockTimeFormatter]). */
private fun formatClockTime(instant: Instant, formatter: DateTimeFormatter): String =
    instant.atZone(ZoneId.systemDefault()).format(formatter)
