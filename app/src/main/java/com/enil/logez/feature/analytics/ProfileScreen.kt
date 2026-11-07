package com.enil.logez.feature.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.BarChart
import com.enil.logez.core.designsystem.BarChartEntry
import com.enil.logez.core.designsystem.BodyDiagram
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzIcons
import com.enil.logez.core.designsystem.LogEzMono
import androidx.compose.foundation.layout.WindowInsets
import com.enil.logez.core.designsystem.RefreshOnResume
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.StatCell
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.calc.DashboardAggregator.TrainingMetric
import com.enil.logez.core.wellness.HealthConnectAvailability
import com.enil.logez.core.wellness.rememberRequestHealthConnectPermissions
import java.util.Locale
import kotlin.math.roundToLong
import com.enil.logez.core.wellness.canInstallOrUpdate
import com.enil.logez.core.wellness.openHealthConnectInPlayStore
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.OutlinedButton

/**
 * Profile tab (PHASE2_PLAN.md §5.2 "Profile tab"): headline stats (lifetime Workouts + Streak,
 * real zeros for a fresh install — honest, never faked), the last-7-days strip with a mini muscle
 * heat-map, the swipeable quick chart card (tap → Statistics with that chart focused), and
 * navigation rows. The Calendar entry stays a nav row rather than an inline grid — Owner-confirmed
 * permanent M5c trim. The temporary RPE toggle this screen carried since 2026-08-24 moved to the
 * real Settings screen in M16 — Settings is now a nav row here instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onExercisesClick: () -> Unit = {},
    onCalendarClick: () -> Unit = {},
    onStatisticsClick: (TrainingMetric?) -> Unit = {},
    onMeasurementsClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestWellnessPermissions = rememberRequestHealthConnectPermissions(
        source = viewModel.healthMetricsSource,
        onResult = viewModel::onWellnessPermissionResult,
    )

    RefreshOnResume(viewModel::refresh)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // Tab roots live inside LogEzApp's Scaffold, whose innerPadding already pushes this whole
        // NavHost below the status bar — TopAppBar's default windowInsets would re-apply the
        // status-bar inset and double the empty space above the header, so it is zeroed too.
        topBar = { TopAppBar(title = { ScreenTitle(stringResource(R.string.nav_profile)) }, windowInsets = WindowInsets(0, 0, 0, 0), colors = logEzTopAppBarColors()) },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            navItems(
                onExercisesClick = onExercisesClick,
                onCalendarClick = onCalendarClick,
                onStatisticsClick = onStatisticsClick,
                onMeasurementsClick = onMeasurementsClick,
                onSettingsClick = onSettingsClick,
            )
            profileStatsItems(
                uiState = uiState,
                onStatisticsClick = onStatisticsClick,
                onConnectWellness = requestWellnessPermissions,
                onInstallHealthConnect = { openHealthConnectInPlayStore(context) },
            )
        }
    }
}

/**
 * The three data-backed items (headline stats, 7-day strip, quick charts). Their *content* is
 * rendered only once the first load lands, so entering the tab never flashes "0 Workouts / No
 * active streak" at a user with real history (the M6a empty-state-flash lesson) — but the items
 * themselves always exist rather than being conditionally inserted, so a loading→loaded transition
 * never shifts scroll position underneath the user.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.profileStatsItems(
    uiState: ProfileUiState,
    onStatisticsClick: (TrainingMetric?) -> Unit,
    onConnectWellness: () -> Unit,
    onInstallHealthConnect: () -> Unit,
) {
        item(key = "headline") {
            if (uiState.isLoading) return@item
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                HeadlineStat(
                    label = stringResource(R.string.profile_stat_workouts),
                    value = uiState.workoutCount.toString(),
                    modifier = Modifier.weight(1f),
                )
                HeadlineStat(
                    label = stringResource(R.string.profile_stat_day_streak),
                    value = if (uiState.streakDays > 0) {
                        pluralStringResource(R.plurals.profile_day_streak_value, uiState.streakDays, uiState.streakDays)
                    } else {
                        stringResource(R.string.profile_no_streak)
                    },
                    modifier = Modifier.weight(1f),
                )
                HeadlineStat(
                    label = stringResource(R.string.profile_stat_streak),
                    value = if (uiState.streakWeeks > 0) {
                        pluralStringResource(R.plurals.profile_streak_weeks, uiState.streakWeeks, uiState.streakWeeks)
                    } else {
                        stringResource(R.string.profile_no_streak)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // M21e: graceful degrade -- no Health Connect on this device means no section at all,
        // never a nag (plan §0.2/decisions.md). Only two remaining states render anything.
        //
        // Owner-requested redesign pass (2026-09-12), two changes: (1) steps and calories used to
        // share one card as two cells in a Row -- now each is its own scorecard, matching the
        // Workouts/Streak headline-stat pair above, with the shared "Today" context moved to a
        // section label rather than repeated inside every card; (2) every stacked item on this
        // screen now carries the same `horizontal = Spacing.md, vertical = Spacing.sm` margin
        // (already the established pattern on the Workout tab's heatmap/steps/quick-track cards)
        // instead of the horizontal-only padding this screen used to have, which left zero gap
        // between consecutive cards.
        item(key = "wellness_install") {
            if (uiState.isLoading || !uiState.wellnessAvailability.canInstallOrUpdate()) return@item
            val updating = uiState.wellnessAvailability == HealthConnectAvailability.UpdateRequired
            LogEzCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(if (updating) R.string.wellness_update_title else R.string.wellness_install_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(if (updating) R.string.wellness_update_body else R.string.wellness_install_body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onInstallHealthConnect) { Text(stringResource(R.string.wellness_install_action)) }
                }
            }
        }

        item(key = "wellness_connect") {
            // Shown until the user grants at least one type. A partial grant is a working choice,
            // not an unfinished one; Settings > Data > Health Connect reaches the rest.
            if (uiState.isLoading || uiState.wellnessAvailability != HealthConnectAvailability.Available || uiState.wellnessGranted.isNotEmpty()) return@item
            LogEzCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(R.string.wellness_connect_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(stringResource(R.string.wellness_connect_body), style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = onConnectWellness) { Text(stringResource(R.string.wellness_connect_action)) }
                }
            }
        }

        item(key = "wellness_today_label") {
            if (uiState.isLoading || !uiState.showsTodayWellness()) return@item
            Text(
                stringResource(R.string.wellness_today_title).uppercase(currentLocale()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
            )
        }

        item(key = "wellness_stats") {
            if (uiState.isLoading || !uiState.showsTodayWellness()) return@item
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                if (uiState.todaySteps != null) {
                    HeadlineStat(
                        label = stringResource(R.string.wellness_steps_label),
                        value = formatSteps(uiState.todaySteps),
                        modifier = Modifier.weight(1f),
                    )
                }
                // M21g: absent when Health Connect has no calorie data for today yet (null, not
                // zero) -- this app never shows a stat it isn't actually tracking, same rule as the
                // Volume/Reps/Distance gating on Finish/History. Steps alone then fills the row.
                if (uiState.todayCaloriesBurned != null) {
                    HeadlineStat(
                        label = stringResource(R.string.wellness_calories_label),
                        value = formatCalories(uiState.todayCaloriesBurned),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item(key = "last7") {
            if (uiState.isLoading) return@item
            LogEzCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(R.string.profile_last7_title).uppercase(currentLocale()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (uiState.last7Count == 0) {
                        // §5.2 region 2: the strip hides behind an honest hint when the window is empty.
                        Text(
                            stringResource(R.string.profile_last7_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(
                            pluralStringResource(R.plurals.profile_last7_count, uiState.last7Count, uiState.last7Count),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        BodyDiagram(intensity = uiState.last7Heat, variant = uiState.muscleDiagramVariant)
                    }
                }
            }
        }

        item(key = "quick_charts") {
            if (uiState.isLoading) return@item
            LogEzCard(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm)) {
                Column(modifier = Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(R.string.profile_quick_charts_title).uppercase(currentLocale()),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    // §5.2 region 4's stated order, not the enum's declaration order.
                    val metrics = listOf(TrainingMetric.FREQUENCY, TrainingMetric.VOLUME, TrainingMetric.REPS, TrainingMetric.DURATION)
                    val pagerState = rememberPagerState(pageCount = { metrics.size })
                    HorizontalPager(state = pagerState) { page ->
                        val metric = metrics[page]
                        val bars = uiState.quickCharts[metric].orEmpty()
                        Column(
                            modifier = Modifier.fillMaxWidth().clickable { onStatisticsClick(metric) },
                            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                        ) {
                            Text(
                                trainingMetricLabel(metric),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            if (bars.isEmpty()) {
                                Text(stringResource(R.string.analytics_empty_period), style = MaterialTheme.typography.bodyMedium)
                            } else {
                                BarChart(
                                    entries = bars.map { BarChartEntry(weekLabel(it.weekStart), it.value) },
                                    yLabel = { AnalyticsFormatters.axisLabel(metric, it, uiState.weightUnit) },
                                    selectedIndex = null,
                                    onBarTap = { onStatisticsClick(metric) },
                                    chartHeight = 120.dp,
                                )
                            }
                        }
                    }
                }
            }
        }

}

private fun androidx.compose.foundation.lazy.LazyListScope.navItems(
    onExercisesClick: () -> Unit,
    onCalendarClick: () -> Unit,
    onStatisticsClick: (TrainingMetric?) -> Unit,
    onMeasurementsClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
        item(key = "nav_statistics") {
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable { onStatisticsClick(null) },
                leadingContent = { Icon(Icons.Outlined.BarChart, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.profile_nav_statistics)) },
                trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
            )
            HorizontalDivider()
        }
        item(key = "nav_calendar") {
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onCalendarClick),
                leadingContent = { Icon(Icons.Outlined.CalendarMonth, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.profile_calendar_row)) },
                trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
            )
            HorizontalDivider()
        }
        item(key = "nav_measurements") {
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onMeasurementsClick),
                leadingContent = { Icon(Icons.Outlined.Straighten, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.profile_measurements_row)) },
                trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
            )
            HorizontalDivider()
        }
        item(key = "nav_exercises") {
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onExercisesClick),
                leadingContent = { Icon(LogEzIcons.Workout, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.profile_nav_exercises)) },
                trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
            )
            HorizontalDivider()
        }
        item(key = "nav_settings") {
            ListItem(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSettingsClick),
                leadingContent = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                headlineContent = { Text(stringResource(R.string.settings_title)) },
                trailingContent = { Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null) },
            )
            HorizontalDivider()
        }
}

/**
 * dataMedium (not dataLarge): at larger system font scales/higher-density devices (Owner report,
 * S26 Ultra), a plural value ("3 weeks") wrapped onto a second line while the other two cards'
 * shorter values ("15", "2 days") still fit, so the three equal-weight cards in the row above
 * rendered at different heights, reading as misaligned. dataMedium is the same size every other
 * stat-cell value in the app already uses (History/Workout Detail's own [StatCell]), so this also
 * makes Profile's headline consistent with them, not just smaller. maxLines/ellipsis is a hard
 * backstop -- even the widest realistic value (a triple-digit streak) truncates instead of
 * wrapping again.
 */
@Composable
private fun HeadlineStat(label: String, value: String, modifier: Modifier = Modifier) {
    LogEzCard(modifier = modifier) {
        StatCell(
            value = value,
            label = label,
            modifier = Modifier.padding(Spacing.md),
            valueStyle = LogEzMono.dataMedium,
            valueMaxLines = 1,
            valueOverflow = TextOverflow.Ellipsis,
            labelStyle = MaterialTheme.typography.labelMedium,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            labelMaxLines = 1,
            labelOverflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatSteps(steps: Long): String = "%,d".format(Locale.ROOT, steps)

private fun formatCalories(calories: Double): String = "%,d".format(Locale.ROOT, calories.roundToLong())

/**
 * The Today section shows only when it has a stat to show: steps whenever that type is granted,
 * calories when granted and Health Connect has a figure for today. Heart rate alone has nothing to
 * show here (it appears during workouts and walk/run tracking instead), and a calories-only grant
 * on a day with no calorie data would otherwise leave an empty "Today" header.
 */
private fun ProfileUiState.showsTodayWellness(): Boolean =
    wellnessAvailability == HealthConnectAvailability.Available &&
        (todaySteps != null || todayCaloriesBurned != null)
