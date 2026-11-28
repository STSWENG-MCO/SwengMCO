package com.enil.logez.feature.workout.finish.share

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.designsystem.currentLocale
import com.enil.logez.core.domain.model.ShareDetail
import com.enil.logez.core.domain.model.ShareMetric
import com.enil.logez.core.domain.model.ShareTemplateChoice

@Composable
internal fun EyebrowLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text.uppercase(currentLocale()),
        style = LogEzMono.dataSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.14.em),
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
internal fun Hairline(
    palette: ShareCardPalette,
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = palette.ink.copy(alpha = 0.3f))
}

@Composable
internal fun RecordsLayout(
    data: ShareCardData,
    choice: ShareTemplateChoice,
    palette: ShareCardPalette,
) {
    val top = data.records.firstOrNull() ?: return
    val rest = data.records.drop(1)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = palette.gold, modifier = Modifier.size(16.dp))
        EyebrowLabel(stringResource(R.string.share_card_new_record), palette.gold, Modifier.padding(start = Spacing.xs))
    }
    Text(
        top.name,
        style = MaterialTheme.typography.titleLarge,
        color = palette.ink,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = Spacing.xxs),
    )
    Text(
        top.typeText,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
        color = palette.inkMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    val (value, unit) = splitValueUnit(top.valueText)
    Text(
        ShareStat(ShareMetric.VOLUME, value, unit).styled(palette, unitSize = 18.sp),
        style = MaterialTheme.typography.titleLarge.copy(fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-0.03).em),
        color = palette.ink,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = Spacing.sm),
    )
    if (rest.isNotEmpty()) {
        Hairline(palette, Modifier.padding(vertical = Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            rest.take(2).forEach { RecordRow(it, palette) }
        }
        val more = rest.size - 2
        if (more > 0) {
            Text(
                stringResource(R.string.share_card_more_prs, more),
                style = LogEzMono.dataSmall,
                color = palette.inkMuted,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
    if (ShareDetail.LOGEZ_MARK in choice.customization.details) LogoRow(palette, Modifier.padding(top = Spacing.md))
}

@Composable
private fun RecordRow(
    record: ShareRecord,
    palette: ShareCardPalette,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = palette.gold, modifier = Modifier.size(16.dp))
        Column(Modifier.weight(1f).padding(start = Spacing.xs)) {
            Text(
                record.name,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                record.typeText,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            record.valueText,
            style = LogEzMono.dataMedium,
            color = palette.ink,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = Spacing.sm),
        )
    }
}

@Composable
internal fun RailLayout(
    data: ShareCardData,
    choice: ShareTemplateChoice,
    palette: ShareCardPalette,
) {
    val details = choice.customization.details
    Column(Modifier.width(140.dp)) {
        Eyebrow(data, palette)
        Heading(data, ShareDetail.DATE in details, palette)
        data.shownStats(choice).forEach { stat ->
            Hairline(palette, Modifier.padding(vertical = Spacing.sm))
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
        if (ShareDetail.LOGEZ_MARK in details) LogoRow(palette, Modifier.padding(top = Spacing.md))
    }
}

@Composable
internal fun SummaryLayout(
    data: ShareCardData,
    choice: ShareTemplateChoice,
    palette: ShareCardPalette,
) {
    val details = choice.customization.details
    val stats = data.shownStats(choice)
    val volume = stats.firstOrNull { it.metric == ShareMetric.VOLUME }
    Eyebrow(data, palette)
    Heading(data, ShareDetail.DATE in details, palette)
    volume?.let {
        Text(
            it.label(data).uppercase(currentLocale()),
            style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
            color = palette.inkMuted,
            modifier = Modifier.padding(top = Spacing.md),
        )
        Text(
            it.styled(palette, unitSize = 18.sp),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.03).em),
            color = palette.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (ShareDetail.RECORDS in details) RecordsPill(data, palette, Modifier.padding(top = Spacing.sm))
    StatGrid(stats.filter { it != volume }, data, palette, Modifier.padding(top = Spacing.md))
    if (ShareDetail.EXERCISES in details && data.exercises.isNotEmpty()) {
        Hairline(palette, Modifier.padding(vertical = Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            data.exercises.take(4).forEach { exercise ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        exercise.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (exercise.hasRecord) {
                        Icon(
                            Icons.Outlined.EmojiEvents,
                            contentDescription = null,
                            tint = palette.gold,
                            modifier = Modifier.padding(start = Spacing.xs).size(14.dp),
                        )
                    }
                    exercise.topSetText?.let {
                        Text(
                            it,
                            style = LogEzMono.dataMedium,
                            color = palette.inkMuted,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = Spacing.xs),
                        )
                    }
                }
            }
        }
        val more = data.exercises.size - 4
        if (more > 0) {
            Text(
                pluralStringResource(R.plurals.share_card_more_exercises, more, more),
                style = LogEzMono.dataSmall,
                color = palette.inkMuted,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
    if (ShareDetail.LOGEZ_MARK in details) LogoRow(palette, Modifier.padding(top = Spacing.md))
}

@Composable
internal fun LogLayout(
    data: ShareCardData,
    choice: ShareTemplateChoice,
    palette: ShareCardPalette,
) {
    val details = choice.customization.details
    EyebrowLabel(stringResource(R.string.share_card_log_eyebrow), palette.accent)
    Heading(data, ShareDetail.DATE in details, palette)
    Text(
        data.shownStats(choice).map { it.inline(data) }.joinToString("   "),
        style = LogEzMono.dataSmall.copy(fontSize = 12.sp),
        color = palette.inkMuted,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = Spacing.sm),
    )
    if (ShareDetail.EXERCISES in details && data.exercises.isNotEmpty()) {
        Hairline(palette, Modifier.padding(vertical = Spacing.sm))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            data.exercises.take(4).forEach { exercise ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            exercise.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (exercise.hasRecord) {
                            Icon(
                                Icons.Outlined.EmojiEvents,
                                contentDescription = null,
                                tint = palette.gold,
                                modifier = Modifier.padding(start = Spacing.xs).size(14.dp),
                            )
                        }
                        exercise.volumeText?.let {
                            Text(
                                it,
                                style = LogEzMono.dataMedium,
                                color = palette.ink,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(start = Spacing.xs),
                            )
                        }
                    }
                    if (exercise.setTexts.isNotEmpty()) {
                        Text(
                            exercise.setTexts.joinToString("   "),
                            style = LogEzMono.dataSmall,
                            color = palette.inkMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        val more = data.exercises.size - 4
        if (more > 0) {
            Text(
                pluralStringResource(R.plurals.share_card_more_exercises, more, more),
                style = LogEzMono.dataSmall,
                color = palette.inkMuted,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
    if (ShareDetail.LOGEZ_MARK in details) LogoRow(palette, Modifier.padding(top = Spacing.md))
}

@Composable
internal fun StreakLayout(
    data: ShareCardData,
    choice: ShareTemplateChoice,
    palette: ShareCardPalette,
) {
    val streak = data.streak ?: return
    EyebrowLabel(stringResource(R.string.share_card_week_streak), palette.accent)
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = Spacing.xxs)) {
        Text(
            streak.weekStreak.toString(),
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 56.sp, lineHeight = 60.sp),
            color = palette.ink,
        )
        Text(
            pluralStringResource(R.plurals.share_card_weeks, streak.weekStreak),
            style = MaterialTheme.typography.bodyLarge,
            color = palette.inkMuted,
            modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs),
        )
    }
    Text(
        pluralStringResource(R.plurals.share_card_streak_caption, streak.weekStreak, streak.weekStreak),
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
        color = palette.inkMuted,
    )
    WeekGrid(streak, palette, Modifier.padding(top = Spacing.md))
    Text(
        stringResource(R.string.share_card_weeks_legend).uppercase(currentLocale()),
        style = LogEzMono.dataSmall,
        color = palette.inkMuted,
        modifier = Modifier.padding(top = Spacing.xs),
    )
    Hairline(palette, Modifier.padding(vertical = Spacing.md))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(
                streak.dayStreak.toString(),
                style = LogEzMono.dataLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
                color = palette.ink,
            )
            Text(
                stringResource(R.string.share_card_day_streak).uppercase(currentLocale()),
                style = LogEzMono.dataSmall.copy(letterSpacing = 0.08.em),
                color = palette.inkMuted,
            )
        }
        Column(Modifier.weight(1f).padding(start = Spacing.md)) {
            Text(
                data.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.share_card_nth_workout, streak.nthThisMonth, streak.monthName),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = palette.inkMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (ShareDetail.LOGEZ_MARK in choice.customization.details) LogoRow(palette, Modifier.padding(top = Spacing.md))
}

/** The last 12 weeks as two rows of six, each showing its workout days; filled when the target was met. */
@Composable
private fun WeekGrid(
    streak: ShareStreak,
    palette: ShareCardPalette,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    // Text on a filled cell: dark on the light accent, light on the dark one.
    val onFilled = if (palette.accent.luminance() > 0.5f) scheme.background else scheme.onBackground
    val shape = RoundedCornerShape(6.dp)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        streak.weekDayCounts.chunked(6).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEachIndexed { column, count ->
                    val isCurrent = rowIndex * 6 + column == streak.weekDayCounts.lastIndex
                    val met = count >= streak.weeklyTarget
                    Box(
                        modifier =
                            Modifier
                                .size(36.dp)
                                .background(if (met) palette.accent else Color.Transparent, shape)
                                .border(
                                    width = if (isCurrent) 2.dp else 1.dp,
                                    color = if (met) palette.accent else palette.ink.copy(alpha = if (isCurrent) 0.9f else 0.3f),
                                    shape = shape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            count.toString(),
                            style = LogEzMono.dataMedium,
                            color = if (met) onFilled else palette.ink,
                        )
                    }
                }
            }
        }
    }
}
