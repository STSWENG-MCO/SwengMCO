package com.enil.logez.feature.workout.finish.share

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.domain.calc.RegionShare
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.ShareTemplate
import com.enil.logez.core.domain.model.ShareTemplateChoice
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.feature.workout.finish.PrMedal

internal const val SHARE_CARD_TAG = "share_card"

/**
 * Everything [ShareCard] renders, pre-formatted by the summary screen -- the card itself is a pure
 * function of this so preview and export can never disagree with the on-screen summary.
 */
data class ShareCardData(
    val title: String,
    val dateLine: String,
    val durationText: String,
    /** Null when this workout never logged that field at all -- the figure is omitted, not zeroed. */
    val volumeText: String?,
    val setsText: String,
    val repsText: String?,
    val distanceText: String? = null,
    val muscleIntensity: Map<MuscleGroup, Float>,
    val muscleDiagramVariant: MuscleDiagramVariant = MuscleDiagramVariant.MALE,
    val muscleBalance: List<RegionShare>,
    val prs: List<PrMedal>,
    val exerciseLines: List<String>,
    /** M11: a circuit adds a "CIRCUIT · N rounds" line under the date. */
    val structure: WorkoutStructure,
    val rounds: Int,
    /** A GPS-tracked walk/run: when set, the figures come from here. */
    val gps: GpsShareCardData? = null,
    /** The user's saved template choice. Null means the default for this kind of workout. */
    val choice: ShareTemplateChoice? = null,
    val records: List<ShareRecord> = emptyList(),
    val exercises: List<ShareExercise> = emptyList(),
    /** Completed sets per primary muscle group, for the Muscles template. */
    val muscleSets: Map<MuscleGroup, Int> = emptyMap(),
    val streak: ShareStreak? = null,
)

/**
 * The walk/run card's content, pre-formatted like everything else here. Every value is one the
 * walk/run summary screen also shows, keeping the stat-parity rule.
 *
 * @property distanceNumber null for an interrupted run, which saved no distance.
 * @property avgBpmText null without a watch; the figure is left out, not dashed.
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
    /** Per-km or per-mile splits; empty for a run tracked before route times were saved. */
    val splits: List<ShareSplit> = emptyList(),
)

/** One personal record: "Bench Press (Barbell)", "Heaviest weight", "72.5 kg". */
data class ShareRecord(
    val name: String,
    val typeText: String,
    val valueText: String,
)

/** One exercise of a strength workout, for the Summary and Log templates. */
data class ShareExercise(
    val name: String,
    /** "72.5 × 8", the heaviest set; null when no set logged both weight and reps. */
    val topSetText: String?,
    val volumeText: String?,
    /** Every counted set as "72.5 × 8". */
    val setTexts: List<String>,
    val hasRecord: Boolean,
)

data class ShareSplit(
    val number: Int,
    val paceSeconds: Double,
    val paceText: String,
)

data class ShareStreak(
    val weekStreak: Int,
    val dayStreak: Int,
    val weeklyTarget: Int,
    /** Workout days in each of the last 12 weeks, oldest first. */
    val weekDayCounts: List<Int>,
    val nthThisMonth: Int,
    val monthName: String,
)

/**
 * The exported summary card. Fixed-size ([TemplateFrame] uses requiredSize, never the caller's
 * constraints) and self-themed, because it is composed under a fixed export density and captured
 * to a PNG -- nothing about the host screen may leak into its layout. Story (9:16) is the only
 * format (Owner directive). The preview, the pager's overview slide and the exported PNG all draw
 * this one composable, which is what keeps them identical (ADR-0002).
 */
@Composable
fun ShareCard(
    data: ShareCardData,
    format: ShareCardFormat,
    modifier: Modifier = Modifier,
) {
    val choice = data.resolvedChoice()
    LogEzTheme {
        val palette = sharePalette(choice.customization)
        TemplateFrame(format, palette, modifier) {
            when (choice.template) {
                ShareTemplate.STACK -> StackLayout(data, choice, palette)
                ShareTemplate.NUMBER -> NumberLayout(data, choice, palette)
                ShareTemplate.RECORDS -> RecordsLayout(data, choice, palette)
                ShareTemplate.GLASS -> GlassLayout(data, choice, palette)
                ShareTemplate.RAIL -> RailLayout(data, choice, palette)
                ShareTemplate.STRIP -> StripLayout(data, choice, palette)
                ShareTemplate.BODY_MAP -> BodyMapLayout(data, choice, palette)
                ShareTemplate.ROUTE -> RouteLayout(data, choice, palette)
                ShareTemplate.SPLITS -> SplitsLayout(data, choice, palette)
                ShareTemplate.SUMMARY -> SummaryLayout(data, choice, palette)
                ShareTemplate.MUSCLES -> MusclesLayout(data, choice, palette)
                ShareTemplate.LOG -> LogLayout(data, choice, palette)
                ShareTemplate.STREAK -> StreakLayout(data, choice, palette)
            }
        }
    }
}
