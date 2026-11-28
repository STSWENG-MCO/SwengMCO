package com.enil.logez.feature.workout.finish.share

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.enil.logez.R
import com.enil.logez.core.common.muscleGroupLabel
import com.enil.logez.core.designsystem.BodyDiagram
import com.enil.logez.core.designsystem.BodyDiagramRegions
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.calc.WorkoutMuscleTargetCalculator
import com.enil.logez.core.domain.model.ShareDetail
import com.enil.logez.core.domain.model.ShareMetric
import com.enil.logez.core.domain.model.ShareTemplateChoice

@Composable
internal fun BodyMapLayout(
    data: ShareCardData,
    choice: ShareTemplateChoice,
    palette: ShareCardPalette,
) {
    val details = choice.customization.details
    val stats = data.shownStats(choice)
    val volume = stats.firstOrNull { it.metric == ShareMetric.VOLUME }
    val groups = WorkoutMuscleTargetCalculator.trainedGroups(data.muscleIntensity).take(3)
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.ink.copy(alpha = 0.08f), shape)
            .border(1.dp, palette.ink.copy(alpha = 0.25f), shape)
            .padding(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            BodyDiagram(
                intensity = data.muscleIntensity,
                variant = data.muscleDiagramVariant,
                modifier = Modifier.width(112.dp),
            )
            Column(Modifier.weight(1f).padding(start = Spacing.sm)) {
                Eyebrow(data, palette)
                Heading(data, ShareDetail.DATE in details, palette, titleStyle = MaterialTheme.typography.titleMedium)
                volume?.let {
                    Text(
                        it.label(data).uppercase(currentLocale()),
                        style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
                        color = palette.inkMuted,
                        modifier = Modifier.padding(top = Spacing.xs),
                    )
                    Text(
                        it.styled(palette, unitSize = 14.sp),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp, lineHeight = 32.sp),
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            stats.filter { it != volume }.map { it.inline(data) }.joinToString(" · "),
            style = LogEzMono.dataSmall.copy(fontSize = 12.sp),
            color = palette.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.sm),
        )
        Row(
            modifier = Modifier.padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (groups.isNotEmpty()) {
                Text(
                    groups.map { muscleGroupLabel(it) }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                    color = palette.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .background(palette.ink.copy(alpha = 0.12f), RoundedCornerShape(Radius.pill))
                            .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
            if (ShareDetail.RECORDS in details) RecordsPill(data, palette)
        }
        if (ShareDetail.LOGEZ_MARK in details) LogoRow(palette, Modifier.padding(top = Spacing.sm))
    }
}

@Composable
internal fun MusclesLayout(
    data: ShareCardData,
    choice: ShareTemplateChoice,
    palette: ShareCardPalette,
) {
    val details = choice.customization.details
    val groups =
        data.muscleSets
            .filterKeys { it in BodyDiagramRegions.MAPPABLE }
            .entries
            .sortedByDescending { it.value }
            .take(3)
    EyebrowLabel(stringResource(R.string.share_card_muscles_eyebrow), palette.accent)
    Heading(data, ShareDetail.DATE in details, palette)
    Text(
        data.shownStats(choice).map { it.inline(data) }.joinToString("   "),
        style = LogEzMono.dataSmall.copy(fontSize = 12.sp),
        color = palette.inkMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = Spacing.xs),
    )
    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), contentAlignment = Alignment.Center) {
        BodyDiagram(
            intensity = data.muscleIntensity,
            variant = data.muscleDiagramVariant,
            modifier = Modifier.width(240.dp),
        )
    }
    if (groups.isNotEmpty()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            groups.forEach { (group, sets) ->
                Column(Modifier.weight(1f)) {
                    Text(
                        muscleGroupLabel(group),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        pluralStringResource(R.plurals.share_card_muscle_sets, sets, sets),
                        style = LogEzMono.dataSmall,
                        color = palette.inkMuted,
                    )
                }
            }
        }
    }
    if (ShareDetail.LOGEZ_MARK in details) LogoRow(palette, Modifier.padding(top = Spacing.md))
}

/** Needs [ColumnScope] so the route can take whatever height the text block leaves. */
@Composable
internal fun ColumnScope.RouteLayout(
    data: ShareCardData,
    choice: ShareTemplateChoice,
    palette: ShareCardPalette,
) {
    val details = choice.customization.details
    val gps = data.gps ?: return
    val stats = data.shownStats(choice)
    val distance = stats.firstOrNull { it.metric == ShareMetric.DISTANCE }
    val rest = stats.filter { it != distance }
    if (gps.routePoints.size >= 2) {
        RouteGlow(
            points = gps.routePoints,
            palette = palette,
            showEnds = ShareDetail.HIDE_ROUTE_ENDS !in details,
            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 96.dp, bottom = Spacing.md),
        )
    }
    Eyebrow(data, palette)
    Heading(data, ShareDetail.DATE in details, palette)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = Spacing.md), verticalAlignment = Alignment.Bottom) {
        distance?.let {
            Text(
                it.styled(palette, unitSize = 18.sp),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.03).em),
                color = palette.ink,
                maxLines = 1,
                modifier = Modifier.padding(end = Spacing.md),
            )
            Box(Modifier.width(1.dp).fillMaxHeight().background(palette.ink.copy(alpha = 0.3f)))
        }
        Column(Modifier.weight(1f).padding(start = if (distance != null) Spacing.md else 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            rest.forEach { stat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stat.label(data).uppercase(currentLocale()),
                        style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
                        color = palette.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        stat.styled(palette, unitSize = 11.sp),
                        style = LogEzMono.dataMedium,
                        color = palette.ink,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = Spacing.xs),
                    )
                }
            }
        }
    }
    if (ShareDetail.LOGEZ_MARK in details) LogoRow(palette, Modifier.padding(top = Spacing.md))
}

/**
 * The route fitted into its box, north up, longitude scaled by cos(latitude) so the shape isn't
 * stretched sideways, with a soft glow under the line. A Canvas rather than the map view: the card
 * is captured through a graphics layer, which cannot reliably capture a live MapView.
 */
@Composable
private fun RouteGlow(
    points: List<Pair<Double, Double>>,
    palette: ShareCardPalette,
    showEnds: Boolean,
    modifier: Modifier = Modifier,
) {
    val line = palette.accent
    val finish = palette.ink
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
        val path =
            Path().apply {
                moveTo(at(0).x, at(0).y)
                for (i in 1 until points.size) lineTo(at(i).x, at(i).y)
            }
        drawPath(path, line.copy(alpha = 0.14f), style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, line.copy(alpha = 0.3f), style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, line, style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (showEnds) {
            val dot = 5.5.dp.toPx()
            drawCircle(finish, radius = dot, center = at(points.lastIndex))
            drawCircle(line, radius = dot, center = at(0))
        }
    }
}

@Composable
internal fun SplitsLayout(
    data: ShareCardData,
    choice: ShareTemplateChoice,
    palette: ShareCardPalette,
) {
    val gps = data.gps ?: return
    val details = choice.customization.details
    val stats = data.shownStats(choice)
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.ink.copy(alpha = 0.08f), shape)
            .border(1.dp, palette.ink.copy(alpha = 0.25f), shape)
            .padding(Spacing.md),
    ) {
        Eyebrow(data, palette)
        Heading(data, ShareDetail.DATE in details, palette, titleStyle = MaterialTheme.typography.titleMedium)
        Row(Modifier.padding(top = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Bottom) {
            stats.firstOrNull { it.metric == ShareMetric.DISTANCE }?.let {
                Text(
                    it.styled(palette, unitSize = 14.sp),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp, lineHeight = 32.sp),
                    color = palette.ink,
                    maxLines = 1,
                )
            }
            stats.firstOrNull { it.metric == ShareMetric.TIME }?.let {
                Text(it.value, style = LogEzMono.dataLarge, color = palette.inkMuted, maxLines = 1)
            }
        }
        SplitBars(gps.splits.take(8), palette, Modifier.padding(top = Spacing.sm))
        if (ShareDetail.LOGEZ_MARK in details) LogoRow(palette, Modifier.padding(top = Spacing.sm))
    }
}

/** One bar per split, taller for a faster pace; the fastest is drawn in the accent colour. */
@Composable
private fun SplitBars(
    splits: List<ShareSplit>,
    palette: ShareCardPalette,
    modifier: Modifier = Modifier,
) {
    val fastest = splits.map { it.paceSeconds }.filter { it > 0.0 }.minOrNull() ?: return
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            splits.forEach { split ->
                val fraction = if (split.paceSeconds > 0.0) (fastest / split.paceSeconds).toFloat().coerceIn(0.2f, 1f) else 0.2f
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .background(
                            if (split.paceSeconds == fastest) palette.accent else palette.ink.copy(alpha = 0.35f),
                            RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                        ),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = Spacing.xxs), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            splits.forEach { split ->
                Text(
                    split.paceText,
                    style = LogEzMono.dataSmall.copy(fontSize = 9.sp),
                    color = palette.inkMuted,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
