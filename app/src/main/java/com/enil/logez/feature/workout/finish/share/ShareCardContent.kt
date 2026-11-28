package com.enil.logez.feature.workout.finish.share

import com.enil.logez.core.designsystem.BodyDiagramRegions
import com.enil.logez.core.domain.model.ShareBackground
import com.enil.logez.core.domain.model.ShareMetric
import com.enil.logez.core.domain.model.ShareTemplate
import com.enil.logez.core.domain.model.ShareTemplateChoice
import com.enil.logez.core.domain.model.ShareTemplateRegistry
import com.enil.logez.core.domain.model.ShareTextTone
import com.enil.logez.core.domain.model.WorkoutKind

internal data class ShareStat(
    val metric: ShareMetric,
    val value: String,
    val unit: String?,
)

internal fun ShareCardData.workoutKind(): WorkoutKind = if (gps != null) WorkoutKind.GPS_TRACKED else WorkoutKind.STRENGTH

internal fun ShareCardData.recordCount(): Int = gps?.prLines?.size ?: maxOf(prs.size, records.size)

internal fun splitValueUnit(text: String): Pair<String, String?> {
    val firstLetter = text.indexOfFirst { it.isLetter() }
    if (firstLetter <= 0) return text to null
    return text.substring(0, firstLetter).trim() to text.substring(firstLetter).trim()
}

internal fun ShareCardData.availableStats(): List<ShareStat> =
    buildList {
        val g = gps
        if (g == null) {
            volumeText?.let { text ->
                val (value, unit) = splitValueUnit(text)
                add(ShareStat(ShareMetric.VOLUME, value, unit))
            }
            add(ShareStat(ShareMetric.TIME, durationText, null))
            add(ShareStat(ShareMetric.SETS, setsText, null))
            repsText?.let { add(ShareStat(ShareMetric.REPS, it, null)) }
        } else {
            g.distanceNumber?.let { add(ShareStat(ShareMetric.DISTANCE, it, g.distanceUnitLabel)) }
            add(ShareStat(ShareMetric.TIME, g.timeText, null))
            g.paceText?.let { add(ShareStat(ShareMetric.PACE, it, null)) }
            g.avgBpmText?.let { add(ShareStat(ShareMetric.AVG_BPM, it, null)) }
        }
    }

internal fun ShareCardData.shownStats(choice: ShareTemplateChoice): List<ShareStat> = availableStats().filter { it.metric in choice.customization.metrics }

internal fun ShareCardData.numberStat(choice: ShareTemplateChoice): ShareStat? {
    val available = availableStats()
    return available.firstOrNull { it.metric == choice.customization.numberMetric } ?: available.firstOrNull()
}

/** Whether this workout has what the template needs (a record, a route, muscles, exercises...). */
internal fun ShareTemplate.isAvailableFor(data: ShareCardData): Boolean =
    when (this) {
        ShareTemplate.RECORDS -> data.recordCount() > 0
        ShareTemplate.ROUTE -> (data.gps?.routePoints?.size ?: 0) >= 2
        ShareTemplate.SPLITS -> (data.gps?.splits?.size ?: 0) >= 2
        ShareTemplate.BODY_MAP, ShareTemplate.MUSCLES -> data.muscleIntensity.keys.any { it in BodyDiagramRegions.MAPPABLE }
        ShareTemplate.SUMMARY, ShareTemplate.LOG -> data.exercises.isNotEmpty()
        ShareTemplate.STREAK -> data.streak != null
        else -> true
    }

/** What the picker offers: supported by this kind of workout and with the data it needs. */
internal fun ShareCardData.selectableTemplates(): List<ShareTemplate> =
    ShareTemplateRegistry
        .forKind(workoutKind())
        .filter { it.isAvailableFor(this) }

internal fun ShareCardData.resolvedChoice(): ShareTemplateChoice {
    val base = choice ?: ShareTemplateRegistry.defaultChoice(workoutKind())
    return if (base.template in selectableTemplates()) base else base.copy(template = ShareTemplateRegistry.defaultTemplate)
}

internal fun inkIsLight(
    background: ShareBackground,
    tone: ShareTextTone,
): Boolean =
    when (background) {
        ShareBackground.DARK -> true
        ShareBackground.LIME, ShareBackground.LIGHT -> false
        ShareBackground.CLEAR -> tone == ShareTextTone.LIGHT
    }
