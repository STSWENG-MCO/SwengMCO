package com.enil.logez.core.designsystem

import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.enil.logez.R
import com.enil.logez.core.domain.calc.BodyRegion
import com.enil.logez.core.domain.calc.RegionShare
import io.github.koalaplot.core.polar.PolarGraph
import io.github.koalaplot.core.polar.PolarGraphDefaults
import io.github.koalaplot.core.polar.PolarPlotSeries
import io.github.koalaplot.core.polar.PolarPoint
import io.github.koalaplot.core.polar.RadialGridType
import io.github.koalaplot.core.polar.rememberCategoryAngularAxisModel
import io.github.koalaplot.core.polar.rememberFloatRadialAxisModel
import io.github.koalaplot.core.style.AreaStyle
import io.github.koalaplot.core.style.LineStyle

/** Shared eight-region radar used by Statistics and the post-workout recap. */
@OptIn(io.github.koalaplot.core.util.ExperimentalKoalaPlotApi::class)
@Composable
fun MuscleBalanceRadar(shares: List<RegionShare>, modifier: Modifier = Modifier, compact: Boolean = false) {
    val radialMax = (shares.maxOfOrNull { it.sharePercent }?.times(1.1f) ?: 0f).coerceAtLeast(25f)
    val radialAxisModel = rememberFloatRadialAxisModel(listOf(0f, radialMax / 2f, radialMax))
    val angularAxisModel = rememberCategoryAngularAxisModel(BodyRegion.entries.toList())
    val primary = MaterialTheme.colorScheme.primary
    val axisLineStyle = LineStyle(
        brush = SolidColor(MaterialTheme.colorScheme.outlineVariant),
        strokeWidth = 1.dp,
    )
    PolarGraph(
        radialAxisModel = radialAxisModel,
        angularAxisModel = angularAxisModel,
        radialAxisLabels = {},
        angularAxisLabels = { region ->
            Text(
                bodyRegionAxisLabel(region).uppercase(currentLocale()),
                style = LogEzMono.dataSmall.copy(
                    fontSize = if (compact) 9.sp else LogEzMono.dataSmall.fontSize,
                    lineHeight = if (compact) 11.sp else LogEzMono.dataSmall.lineHeight,
                    letterSpacing = if (compact) 0.em else 0.06.em,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                maxLines = 1,
                softWrap = false,
            )
        },
        modifier = modifier.fillMaxWidth().aspectRatio(1f),
        polarGraphProperties = PolarGraphDefaults.polarGraphPropertyDefaults().copy(
            radialGridType = RadialGridType.LINES,
            radialAxisGridLineStyle = axisLineStyle,
            angularAxisGridLineStyle = axisLineStyle,
            angularLabelGap = if (compact) 1.dp else Spacing.xs,
        ),
    ) {
        PolarPlotSeries(
            data = shares.map { PolarPoint(it.sharePercent.toFloat(), it.region) },
            lineStyle = LineStyle(brush = SolidColor(primary), strokeWidth = 2.dp),
            areaStyle = AreaStyle(brush = SolidColor(primary), alpha = 0.2f),
            animationSpec = snap(),
        )
    }
}

@Composable
private fun bodyRegionAxisLabel(region: BodyRegion): String = stringResource(
    when (region) {
        BodyRegion.CHEST -> R.string.muscle_region_chest_axis
        BodyRegion.BACK -> R.string.muscle_region_back_axis
        BodyRegion.SHOULDERS -> R.string.muscle_region_shoulders_axis
        BodyRegion.ARMS -> R.string.muscle_region_arms_axis
        BodyRegion.CORE -> R.string.muscle_region_core_axis
        BodyRegion.QUADS -> R.string.muscle_region_quads_axis
        BodyRegion.HAMSTRINGS_GLUTES -> R.string.muscle_region_hamstrings_glutes_axis
        BodyRegion.LOWER_LEG -> R.string.muscle_region_lower_leg_axis
    },
)
