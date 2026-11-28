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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.enil.logez.R
import com.enil.logez.core.designsystem.Gold500
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.model.ShareBackground
import com.enil.logez.core.domain.model.ShareCustomization
import com.enil.logez.core.domain.model.ShareDetail
import com.enil.logez.core.domain.model.ShareMetric
import com.enil.logez.core.domain.model.ShareTemplateChoice
import com.enil.logez.core.domain.model.WorkoutStructure

private val ContentSideInset = 24.dp
private val ContentBottomInset = 117.dp

internal data class ShareCardPalette(
    val background: Color,
    val ink: Color,
    val inkMuted: Color,
    val accent: Color,
    val gold: Color,
)

@Composable
internal fun sharePalette(customization: ShareCustomization): ShareCardPalette {
    val scheme = MaterialTheme.colorScheme
    val lightInk = inkIsLight(customization.background, customization.textTone)
    val ink = if (lightInk) scheme.onBackground else scheme.background
    return ShareCardPalette(
        background =
            when (customization.background) {
                ShareBackground.DARK -> scheme.background
                ShareBackground.LIME -> scheme.primary
                ShareBackground.LIGHT -> scheme.onBackground
                ShareBackground.CLEAR -> Color.Transparent
            },
        ink = ink,
        inkMuted = ink.copy(alpha = 0.7f),
        accent =
            when {
                customization.background == ShareBackground.LIME -> scheme.background
                lightInk -> scheme.primary
                else -> scheme.secondary
            },
        gold = if (lightInk) Gold500 else lerp(Gold500, Color.Black, 0.55f),
    )
}

@Composable
internal fun TemplateFrame(
    format: ShareCardFormat,
    palette: ShareCardPalette,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .requiredSize(format.width, format.height)
                .background(palette.background)
                .padding(start = ContentSideInset, end = ContentSideInset, bottom = ContentBottomInset),
        verticalArrangement = Arrangement.Bottom,
        content = content,
    )
}

@Composable
internal fun StackLayout(
    data: ShareCardData,
    choice: ShareTemplateChoice,
    palette: ShareCardPalette,
    modifier: Modifier = Modifier,
) {
    val details = choice.customization.details
    Column(modifier) {
        Eyebrow(data, palette)
        Heading(data, ShareDetail.DATE in details, palette)
        StatGrid(data.shownStats(choice), data, palette, Modifier.padding(top = Spacing.md))
        if (ShareDetail.RECORDS in details) RecordsPill(data, palette, Modifier.padding(top = Spacing.md))
        if (ShareDetail.LOGEZ_MARK in details) LogoRow(palette, Modifier.padding(top = Spacing.md))
    }
}

@Composable
internal fun GlassLayout(
    data: ShareCardData,
    choice: ShareTemplateChoice,
    palette: ShareCardPalette,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.ink.copy(alpha = 0.08f), shape)
            .border(1.dp, palette.ink.copy(alpha = 0.25f), shape)
            .padding(Spacing.md),
    ) {
        StackLayout(data, choice, palette)
    }
}

@Composable
internal fun NumberLayout(
    data: ShareCardData,
    choice: ShareTemplateChoice,
    palette: ShareCardPalette,
) {
    val details = choice.customization.details
    val stat = data.numberStat(choice)
    if (stat != null) {
        val label =
            if (stat.metric == ShareMetric.VOLUME) stringResource(R.string.share_card_total_volume) else stat.label(data)
        Text(
            label.uppercase(currentLocale()),
            style = LogEzMono.dataSmall.copy(fontSize = 12.sp, letterSpacing = 0.14.em),
            color = palette.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stat.styled(palette, unitSize = 18.sp),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-0.03).em),
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Heading(data, ShareDetail.DATE in details, palette, titleStyle = MaterialTheme.typography.titleMedium)
    if (ShareDetail.LOGEZ_MARK in details) LogoRow(palette, Modifier.padding(top = Spacing.md))
}

@Composable
internal fun StripLayout(
    data: ShareCardData,
    choice: ShareTemplateChoice,
    palette: ShareCardPalette,
) {
    val details = choice.customization.details
    val shown = data.shownStats(choice)
    val primary = shown.firstOrNull { it.metric == ShareMetric.VOLUME || it.metric == ShareMetric.DISTANCE }
    val secondary = shown.filter { it != primary }.map { it.inline(data) }
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.ink.copy(alpha = 0.08f), shape)
            .border(1.dp, palette.ink.copy(alpha = 0.25f), shape)
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The title takes whatever the figure leaves, so a long title ends in an ellipsis
            // instead of running under the number.
            Text(
                data.title,
                style = MaterialTheme.typography.titleMedium,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            primary?.let {
                Text(
                    it.styled(palette, unitSize = 17.sp),
                    style = LogEzMono.dataLarge.copy(fontSize = 17.sp, lineHeight = 22.sp),
                    color = palette.ink,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = Spacing.sm),
                )
            }
        }
        Row(modifier = Modifier.padding(top = Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                secondary.joinToString(" · "),
                style = LogEzMono.dataSmall.copy(fontSize = 12.sp),
                color = palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (ShareDetail.LOGEZ_MARK in details) LogoRow(palette, Modifier.padding(start = Spacing.sm))
        }
    }
}

@Composable
internal fun Eyebrow(
    data: ShareCardData,
    palette: ShareCardPalette,
) {
    Text(
        (data.gps?.eyebrow ?: stringResource(R.string.summary_title)).uppercase(currentLocale()),
        style = LogEzMono.dataSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.14.em),
        color = palette.accent,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun Heading(
    data: ShareCardData,
    showDate: Boolean,
    palette: ShareCardPalette,
    titleStyle: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleLarge,
) {
    Text(
        data.title,
        style = titleStyle,
        color = palette.ink,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = Spacing.xxs),
    )
    if (showDate) {
        Text(
            data.dateLine,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = palette.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
    if (data.structure == WorkoutStructure.CIRCUIT) {
        Text(
            (
                stringResource(R.string.routine_structure_chip_circuit) + " · " +
                    pluralStringResource(R.plurals.routine_rounds_count, data.rounds, data.rounds)
            ).uppercase(currentLocale()),
            style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
            color = palette.accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.xxs),
        )
    }
}

/** Up to three figures in a row, or four as two rows of two with a divider between the columns. */
@Composable
internal fun StatGrid(
    stats: List<ShareStat>,
    data: ShareCardData,
    palette: ShareCardPalette,
    modifier: Modifier = Modifier,
) {
    if (stats.isEmpty()) return
    val rows = if (stats.size == 4) stats.chunked(2) else listOf(stats)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                row.forEachIndexed { index, stat ->
                    if (index > 0) {
                        Box(Modifier.width(1.dp).fillMaxHeight().background(palette.ink.copy(alpha = 0.3f)))
                    }
                    Column(Modifier.weight(1f).padding(start = if (index > 0) Spacing.md else 0.dp)) {
                        Text(
                            stat.styled(palette, unitSize = 12.sp),
                            style = LogEzMono.dataLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stat.label(data).uppercase(currentLocale()),
                            style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
                            color = palette.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RecordsPill(
    data: ShareCardData,
    palette: ShareCardPalette,
    modifier: Modifier = Modifier,
) {
    val count = data.recordCount()
    if (count == 0) return
    val text =
        data.gps
            ?.prLines
            ?.firstOrNull()
            ?.let { (label, value) -> "$label $value" }
            ?: pluralStringResource(R.plurals.share_card_records_pill, count, count)
    val shape = RoundedCornerShape(Radius.pill)
    Row(
        modifier =
            modifier
                .background(palette.gold.copy(alpha = 0.15f), shape)
                .border(1.dp, palette.gold.copy(alpha = 0.7f), shape)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = palette.gold, modifier = Modifier.size(16.dp))
        Text(
            text,
            style = LogEzMono.dataMedium,
            color = palette.gold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

@Composable
internal fun LogoRow(
    palette: ShareCardPalette,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        LogEzMark(palette.accent, Modifier.size(18.dp))
        Text(
            stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.02.em),
            color = palette.ink,
            modifier = Modifier.padding(start = Spacing.xs),
        )
    }
}

/** The brand mark: a hexagon with an L, drawn as vectors so it exports crisply at any density. */
@Composable
private fun LogEzMark(
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val hexagon =
            Path().apply {
                moveTo(w * 0.5f, 0f)
                lineTo(w, h * 0.25f)
                lineTo(w, h * 0.75f)
                lineTo(w * 0.5f, h)
                lineTo(0f, h * 0.75f)
                lineTo(0f, h * 0.25f)
                close()
            }
        drawPath(hexagon, color, style = Stroke(width = w * 0.12f, join = StrokeJoin.Round))
        val letter =
            Path().apply {
                moveTo(w * 0.38f, h * 0.28f)
                lineTo(w * 0.52f, h * 0.28f)
                lineTo(w * 0.52f, h * 0.58f)
                lineTo(w * 0.68f, h * 0.58f)
                lineTo(w * 0.68f, h * 0.72f)
                lineTo(w * 0.38f, h * 0.72f)
                close()
            }
        drawPath(letter, color)
    }
}

/** The figure, with its unit drawn smaller and muted beside it. */
internal fun ShareStat.styled(
    palette: ShareCardPalette,
    unitSize: TextUnit,
): AnnotatedString =
    buildAnnotatedString {
        append(value)
        unit?.let {
            withStyle(SpanStyle(fontSize = unitSize, color = palette.inkMuted, fontWeight = FontWeight.Medium)) {
                append(" ")
                append(it)
            }
        }
    }

@Composable
internal fun ShareStat.label(data: ShareCardData): String =
    when (metric) {
        ShareMetric.VOLUME -> stringResource(R.string.summary_volume)
        ShareMetric.TIME -> stringResource(R.string.share_card_time)
        ShareMetric.SETS -> stringResource(R.string.summary_sets)
        ShareMetric.REPS -> stringResource(R.string.summary_reps)
        ShareMetric.DISTANCE -> stringResource(R.string.summary_gps_distance)
        ShareMetric.PACE -> data.gps?.paceLabel.orEmpty()
        ShareMetric.AVG_BPM -> stringResource(R.string.share_card_avg_bpm)
    }

@Composable
internal fun ShareStat.inline(data: ShareCardData): String =
    when (metric) {
        ShareMetric.SETS, ShareMetric.REPS, ShareMetric.AVG_BPM -> "$value ${label(data).lowercase(currentLocale())}"
        ShareMetric.PACE -> "$value /${data.gps?.distanceUnitLabel.orEmpty()}"
        else -> if (unit != null) "$value $unit" else value
    }
