package com.enil.logez.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.WeightUnit
import com.enil.logez.R
import com.enil.logez.core.designsystem.CircuitChip
import com.enil.logez.core.designsystem.EmptyState
import com.enil.logez.core.designsystem.Gold500
import com.enil.logez.core.designsystem.LogEzCard
import com.enil.logez.core.designsystem.LogEzIcons
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.StatCell
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.domain.model.WorkoutStructure
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.enil.logez.core.designsystem.rememberClockTimeFormatter
import com.enil.logez.core.designsystem.currentLocale

/**
 * PHASE2_PLAN.md §5.2 History tab — the landing destination, a reverse-chronological feed of
 * completed workouts. A personal log, not a social feed: no Discover toggle, no likes, no
 * comments, none of which exist here.
 *
 * The plan specifies a long-press-on-card shortcut into the same overflow menu Workout Detail
 * carries; that menu's three-dot button is a more discoverable path to the same actions than an
 * undiscoverable gesture, and every action stays reachable in two taps (card -> Detail -> menu),
 * so this ships without a redundant, harder-to-find long-press affordance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onWorkoutClick: (workoutId: String) -> Unit,
    onStartWorkout: () -> Unit,
) {
    val viewModel: HistoryViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        // Tab roots live inside LogEzApp's Scaffold, whose innerPadding already pushes this whole
        // NavHost below the status bar — TopAppBar's default windowInsets would re-apply the
        // status-bar inset and double the empty space above the header, so it is zeroed too.
        topBar = { TopAppBar(title = { ScreenTitle(stringResource(R.string.nav_history)) }, windowInsets = WindowInsets(0, 0, 0, 0), colors = logEzTopAppBarColors()) },
    ) { padding ->
        if (uiState.isLoading) return@Scaffold

        if (uiState.cards.isEmpty()) {
            EmptyState(
                icon = LogEzIcons.History,
                title = stringResource(R.string.history_empty_title),
                subtitle = stringResource(R.string.history_empty_subtitle),
                ctaLabel = stringResource(R.string.history_empty_cta),
                onCtaClick = onStartWorkout,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = Spacing.md)) {
            items(items = uiState.cards, key = { it.workoutId }) { card ->
                WorkoutHistoryCard(card, weightUnit = uiState.weightUnit, distanceUnit = uiState.distanceUnit, onClick = { onWorkoutClick(card.workoutId) })
            }
        }
    }
}

@Composable
private fun WorkoutHistoryCard(card: WorkoutCardModel, weightUnit: WeightUnit, distanceUnit: DistanceUnit, onClick: () -> Unit) {
    LogEzCard(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm).clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                // Weighted and clipped to one line: a Row measures unweighted children in order and
                // hands the first one the whole width, so a long title (routine names feed straight
                // into this field, unbounded) squeezed the Records chip to zero and a workout that
                // set a PR showed no chip at all.
                // Owner: wide and bold, matching the mockup's display-face treatment ("BENCH
                // PRESS") — the one piece of card text that keeps its own identity rather than
                // turning plain white like the metrics/exercise lines below it.
                Text(
                    card.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.02.em),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // M11: circuit workouts identify themselves on the card.
                if (card.structure == WorkoutStructure.CIRCUIT) CircuitChip(modifier = Modifier.padding(end = Spacing.xs))
                if (card.hasRecords) RecordsChip()
            }
            Text(
                formatCardDateTime(card.startedAtMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xxs),
            )

            Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                StatCell(label = stringResource(R.string.summary_duration), value = formatCardDuration(card.durationSeconds), valueColor = MaterialTheme.colorScheme.onSurface, labelColor = MaterialTheme.colorScheme.onSurface)
                // A GPS-tracked walk/run never logged weight -- "0kg Volume" would be noise next to
                // its real distance, so the cell is gated on whether it was actually tracked.
                if (card.hasVolume) StatCell(label = stringResource(R.string.summary_volume), value = formatCardVolume(card.volumeKg, weightUnit), valueColor = MaterialTheme.colorScheme.onSurface, labelColor = MaterialTheme.colorScheme.onSurface)
                StatCell(label = stringResource(R.string.summary_sets), value = card.setCount.toString(), valueColor = MaterialTheme.colorScheme.onSurface, labelColor = MaterialTheme.colorScheme.onSurface)
                if (card.hasDistance) StatCell(label = stringResource(R.string.summary_distance), value = formatCardDistance(card.distanceMeters, distanceUnit), valueColor = MaterialTheme.colorScheme.onSurface, labelColor = MaterialTheme.colorScheme.onSurface)
                // M11: circuit cards report their round count alongside the shared stats.
                if (card.structure == WorkoutStructure.CIRCUIT) {
                    StatCell(label = stringResource(R.string.routine_rounds_label), value = card.rounds.toString(), valueColor = MaterialTheme.colorScheme.onSurface, labelColor = MaterialTheme.colorScheme.onSurface)
                }
            }

            if (card.exerciseSummaries.isNotEmpty()) {
                Column(modifier = Modifier.padding(top = Spacing.sm)) {
                    card.exerciseSummaries.take(3).forEach { line ->
                        Text(
                            "${line.setCount} × ${line.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    val remaining = card.exerciseSummaries.size - 3
                    if (remaining > 0) {
                        Text(
                            pluralStringResource(R.plurals.history_card_more_exercises, remaining, remaining),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = Spacing.xxs),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsChip() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.EmojiEvents,
            contentDescription = stringResource(R.string.history_card_records_chip),
            tint = Gold500,
            modifier = Modifier.padding(end = Spacing.xxs),
        )
        Text(stringResource(R.string.history_card_records_chip), style = MaterialTheme.typography.labelMedium, color = Gold500)
    }
}

/**
 * "Today, 2:32 PM" / "Yesterday, 14:32" / "12 Aug, 2:32 PM" / "12 Aug 2025, 2:32 PM" once the year
 * rolls over. The clock follows the phone's 12/24-hour setting, and Today/Yesterday are resources.
 */
@Composable
internal fun formatCardDateTime(millis: Long, today: LocalDate = LocalDate.now(ZoneId.systemDefault())): String {
    val zoned = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    val date = zoned.toLocalDate()
    val locale = currentLocale()
    val time = zoned.format(rememberClockTimeFormatter())
    val dayPart = when {
        date == today -> stringResource(R.string.history_card_today)
        date == today.minusDays(1) -> stringResource(R.string.history_card_yesterday)
        date.year == today.year -> zoned.format(DateTimeFormatter.ofPattern("d MMM", locale))
        else -> zoned.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
    }
    return stringResource(R.string.history_card_date_time, dayPart, time)
}

private fun formatCardDuration(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun formatCardVolume(kg: Double, unit: WeightUnit): String = com.enil.logez.core.designsystem.formatWeight(kg, unit)

private fun formatCardDistance(meters: Double, unit: DistanceUnit): String = com.enil.logez.core.designsystem.formatDistance(meters, unit)
