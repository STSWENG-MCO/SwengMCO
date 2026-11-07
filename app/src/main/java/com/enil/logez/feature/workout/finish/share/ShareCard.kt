package com.enil.logez.feature.workout.finish.share

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import com.enil.logez.R
import com.enil.logez.core.designsystem.MuscleBalanceRadar
import com.enil.logez.core.domain.calc.RegionShare
import com.enil.logez.core.designsystem.BodyDiagram
import com.enil.logez.core.designsystem.BodyDiagramRegions
import com.enil.logez.core.designsystem.Gold500
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.StatCell
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.feature.workout.finish.PrMedal
import com.enil.logez.feature.workout.finish.labelRes

/**
 * Everything [ShareCard] renders, pre-formatted by the summary screen — the card itself is a pure
 * function of this so preview and export can never disagree with the on-screen summary.
 */
data class ShareCardData(
    val title: String,
    val dateLine: String,
    val durationText: String,
    /** Null when this workout never logged that field at all (e.g. a GPS-tracked walk has no
     * weight/reps concept) -- [ShareCard] omits the cell entirely rather than show a fake zero. */
    val volumeText: String?,
    val setsText: String,
    val repsText: String?,
    val distanceText: String? = null,
    val muscleIntensity: Map<MuscleGroup, Float>,
    val muscleDiagramVariant: MuscleDiagramVariant = MuscleDiagramVariant.MALE,
    val muscleBalance: List<RegionShare>,
    val prs: List<PrMedal>,
    /**
     * REGULAR: History's "3 × Bench Press (Barbell)" shape. CIRCUIT: bare names in sequence order
     * — the per-block set count is redundant with the card's own "CIRCUIT · N rounds" line.
     */
    val exerciseLines: List<String>,
    /** M11: CIRCUIT cards add the rounds line; REGULAR cards are visually unchanged. */
    val structure: WorkoutStructure,
    val rounds: Int,
    /** A GPS-tracked walk/run: when set, the card is the route layout and the fields above aren't drawn. */
    val gps: GpsShareCardData? = null,
)

/**
 * The walk/run card's content (2026-09-26), pre-formatted like everything else here. Every value
 * is one the walk/run summary screen also shows, keeping the stat-parity rule.
 *
 * @property distanceNumber null for an interrupted run, which saved no distance.
 * @property avgBpmText null without a watch; the cell is left out, not dashed.
 * @property prLines (record type, value) pairs.
 */
data class GpsShareCardData(
    val eyebrow: String,
    val routePoints: List<Pair<Double, Double>>,
    val distanceNumber: String?,
    val distanceUnitLabel: String,
    val timeText: String,
    val paceText: String?,
    val paceLabel: String,
    val avgBpmText: String?,
    val prLines: List<Pair<String, String>>,
)

/**
 * The exported summary card. Fixed-size ([Modifier.requiredSize], never the caller's constraints)
 * and self-themed, because it is composed under a fixed export density and captured to a PNG —
 * nothing about the host screen may leak into its layout. Story (9:16) is the only format
 * (Owner directive — square retired); the detail block clips inside a weighted column so a
 * worst-case card (long 2-line title + muscle diagram + PRs) degrades by dropping list rows, never by
 * pushing the wordmark off-canvas.
 */
@Composable
fun ShareCard(data: ShareCardData, format: ShareCardFormat, modifier: Modifier = Modifier) {
    data.gps?.let { gps ->
        GpsShareCard(data, gps, format, modifier)
        return
    }
    LogEzTheme {
        val sectionGap = Spacing.md
        Column(
            modifier = modifier
                .requiredSize(format.width, format.height)
                .background(MaterialTheme.colorScheme.background)
                .padding(Spacing.lg),
        ) {
            Text(
                stringResource(R.string.summary_title).uppercase(currentLocale()),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.14.em),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                data.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
            Text(
                data.dateLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
            if (data.structure == WorkoutStructure.CIRCUIT) {
                // M11: header metadata, not a stat — the workout's shape sits with title/date, and
                // the exercise list below drops its per-block set counts in favour of this line.
                Text(
                    (
                        stringResource(R.string.routine_structure_chip_circuit) + " · " +
                            pluralStringResource(R.plurals.routine_rounds_count, data.rounds, data.rounds)
                        ).uppercase(currentLocale()),
                    style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = Spacing.xxs),
                )
            }
            Hairline(Modifier.padding(vertical = sectionGap))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CardStat(R.string.summary_duration, data.durationText, Modifier.weight(1f))
                data.volumeText?.let { CardStat(R.string.summary_volume, it, Modifier.weight(1f)) }
                CardStat(R.string.summary_sets, data.setsText, Modifier.weight(1f))
                data.repsText?.let { CardStat(R.string.summary_reps, it, Modifier.weight(1f)) }
                data.distanceText?.let { CardStat(R.string.summary_distance, it, Modifier.weight(1f)) }
            }

            if (data.muscleIntensity.keys.any { it in BodyDiagramRegions.MAPPABLE }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = sectionGap),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SectionHeader(stringResource(R.string.analytics_body_title))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BodyDiagram(intensity = data.muscleIntensity, variant = data.muscleDiagramVariant, modifier = Modifier.weight(0.8f))
                        MuscleBalanceRadar(data.muscleBalance, Modifier.weight(1.2f), compact = true)
                    }
                }
            }

            Column(modifier = Modifier.weight(1f).clipToBounds()) {
                val detailStyle = MaterialTheme.typography.bodyMedium
                if (data.prs.isNotEmpty()) {
                    SectionHeader(
                        pluralStringResource(R.plurals.share_card_prs_header, data.prs.size),
                        modifier = Modifier.padding(top = sectionGap, bottom = Spacing.xxs),
                    )
                    val maxPrs = 3
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                        data.prs.take(maxPrs).forEach { pr -> PrLine(pr, detailStyle) }
                    }
                    val morePrs = data.prs.size - maxPrs
                    if (morePrs > 0) MoreLine(stringResource(R.string.share_card_more_prs, morePrs))
                }
                if (data.exerciseLines.isNotEmpty()) {
                    val maxExercises = 5
                    Column(
                        modifier = Modifier.padding(top = sectionGap),
                        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
                    ) {
                        data.exerciseLines.take(maxExercises).forEach { line ->
                            Text(line, style = detailStyle, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val more = data.exerciseLines.size - maxExercises
                        if (more > 0) MoreLine(pluralStringResource(R.plurals.share_card_more_exercises, more, more))
                    }
                }
            }

            Hairline(Modifier.padding(top = Spacing.xs, bottom = Spacing.xs))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.06.em),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

/**
 * The walk/run card: route drawn as a plain vector line, distance, time, pace, average heart rate
 * and records. No muscle charts and no set lines, which said nothing true about a run. The route
 * is drawn on a Canvas rather than with the map view: the card is captured through a graphics
 * layer, which cannot reliably capture a live MapView, and a card with no tiles needs no network.
 */
@Composable
private fun GpsShareCard(data: ShareCardData, gps: GpsShareCardData, format: ShareCardFormat, modifier: Modifier) {
    LogEzTheme {
        Column(
            modifier = modifier
                .requiredSize(format.width, format.height)
                .background(MaterialTheme.colorScheme.background)
                .padding(Spacing.lg),
        ) {
            Text(
                gps.eyebrow.uppercase(currentLocale()),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.14.em),
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                data.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.xxs),
            )
            Text(
                data.dateLine,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            Hairline(Modifier.padding(top = Spacing.md))

            if (gps.routePoints.size >= 2) {
                RouteDrawing(gps.routePoints, Modifier.fillMaxWidth().weight(1f).padding(vertical = Spacing.xs))
            } else {
                Spacer(Modifier.weight(1f))
            }

            gps.distanceNumber?.let { number ->
                Text(
                    stringResource(R.string.summary_gps_distance).uppercase(currentLocale()),
                    style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row {
                    Text(
                        number,
                        style = LogEzMono.dataLarge.copy(fontSize = 54.sp, lineHeight = 58.sp, letterSpacing = (-0.03).em),
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.alignByBaseline(),
                    )
                    Text(
                        gps.distanceUnitLabel,
                        style = LogEzMono.dataLarge.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline().padding(start = 6.dp),
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                GpsCardStat(gps.timeText, stringResource(R.string.share_card_time), Modifier.weight(1f))
                gps.paceText?.let { GpsCardStat(it, gps.paceLabel, Modifier.weight(1f)) }
                gps.avgBpmText?.let { GpsCardStat(it, stringResource(R.string.share_card_avg_bpm), Modifier.weight(1f)) }
            }
            gps.prLines.take(2).forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = Gold500, modifier = Modifier.size(16.dp))
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f).padding(start = Spacing.xs),
                    )
                    Text(value, style = LogEzMono.dataMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onBackground)
                }
            }

            Hairline(Modifier.padding(top = Spacing.md, bottom = Spacing.xs))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.06.em),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}

@Composable
private fun GpsCardStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = LogEzMono.dataLarge, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
        Text(
            label.uppercase(currentLocale()),
            style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

/**
 * The route fitted into its box, north up, longitude scaled by cos(latitude) so the shape isn't
 * stretched sideways. A soft wide stroke under the line, a green start dot and a light finish dot,
 * matching the map's route styling.
 */
@Composable
private fun RouteDrawing(points: List<Pair<Double, Double>>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val finish = MaterialTheme.colorScheme.onSurface
    val ring = MaterialTheme.colorScheme.background
    Canvas(modifier = modifier) {
        val cosLat = kotlin.math.cos(Math.toRadians(points.sumOf { it.first } / points.size))
        val xs = points.map { it.second * cosLat }
        val ys = points.map { -it.first }
        val minX = xs.min()
        val minY = ys.min()
        val spanX = (xs.max() - minX).takeIf { it > 0.0 } ?: 1e-9
        val spanY = (ys.max() - minY).takeIf { it > 0.0 } ?: 1e-9
        val pad = 18.dp.toPx()
        val scale = minOf((size.width - 2 * pad) / spanX, (size.height - 2 * pad) / spanY)
        val offsetX = (size.width - spanX * scale) / 2
        val offsetY = (size.height - spanY * scale) / 2
        fun at(i: Int) = Offset((offsetX + (xs[i] - minX) * scale).toFloat(), (offsetY + (ys[i] - minY) * scale).toFloat())
        val path = Path().apply {
            moveTo(at(0).x, at(0).y)
            for (i in 1 until points.size) lineTo(at(i).x, at(i).y)
        }
        drawPath(path, line.copy(alpha = 0.14f), style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, line, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val dot = 5.5.dp.toPx()
        val stroke = 2.5.dp.toPx()
        drawCircle(ring, radius = dot + stroke, center = at(points.lastIndex))
        drawCircle(finish, radius = dot, center = at(points.lastIndex))
        drawCircle(ring, radius = dot + stroke, center = at(0))
        drawCircle(line, radius = dot, center = at(0))
    }
}

@Composable
private fun Hairline(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
}

@Composable
private fun CardStat(@StringRes labelRes: Int, value: String, modifier: Modifier = Modifier) {
    StatCell(
        value = value,
        label = stringResource(labelRes).uppercase(currentLocale()),
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        valueStyle = LogEzMono.dataLarge,
        valueColor = MaterialTheme.colorScheme.onBackground,
        valueTextAlign = TextAlign.Center,
        labelStyle = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
        labelColor = MaterialTheme.colorScheme.onBackground,
        labelTextAlign = TextAlign.Center,
    )
}

@Composable
private fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(currentLocale()),
        style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    )
}

@Composable
private fun PrLine(pr: PrMedal, style: TextStyle) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.EmojiEvents,
            contentDescription = null,
            tint = Gold500,
            modifier = Modifier.size(16.dp),
        )
        Text(
            pr.exerciseName,
            style = style,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = Spacing.xs),
        )
        Text(
            stringResource(pr.prType.labelRes()),
            style = style,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

@Composable
private fun MoreLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground)
}
