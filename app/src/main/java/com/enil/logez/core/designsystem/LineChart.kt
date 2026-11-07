package com.enil.logez.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.enil.logez.R
import androidx.compose.ui.res.pluralStringResource

/** One chart point: [x] is an epoch-millis timestamp, [y] the raw metric value. */
data class LineChartPoint(val x: Long, val y: Double)

/**
 * Dependency-free Compose Canvas line chart (M6a decision: no third-party chart library — the
 * toolchain quadruple is pinned, and §5.2's exercise graphs are a single series with tap-to-read,
 * well within Canvas reach). Y-axis renders min/mid/max gridline labels via [yLabel]; the x-axis
 * renders the first and last point's [xLabel]. Tapping selects the nearest point by x.
 *
 * [showPoints] false draws the line without a dot on every point (2026-09-26): a heart-rate or
 * pace series has a point every few seconds, and a dot on each one turns the line into beads.
 * A tapped point still gets its highlight.
 */
@Composable
fun LineChart(
    points: List<LineChartPoint>,
    yLabel: (Double) -> String,
    xLabel: (Long) -> String,
    selectedIndex: Int?,
    onPointTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    showPoints: Boolean = true,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectedColor = MaterialTheme.colorScheme.tertiary
    val labelStyle: TextStyle = LogEzMono.dataSmall.copy(color = labelColor)
    val textMeasurer = rememberTextMeasurer()
    // See BarChart: a spoken summary, because a Canvas is invisible to TalkBack.
    val summary = if (points.isEmpty()) {
        stringResource(R.string.chart_empty_summary)
    } else {
        stringResource(
            R.string.chart_line_summary,
            pluralStringResource(R.plurals.chart_point_count, points.size, points.size), xLabel(points.first().x), xLabel(points.last().x),
            yLabel(points.maxOf { it.y }), yLabel(points.minOf { it.y }), yLabel(points.last().y),
        )
    }

    // Geometry is recomputed identically in draw and tap scopes from the same inputs, so the two
    // lambdas can't disagree about where a point sits.
    val minY = points.minOf { it.y }
    val maxY = points.maxOf { it.y }
    val ySpan = (maxY - minY).takeIf { it > 0.0 } ?: (if (maxY == 0.0) 1.0 else maxY * 0.2)
    val yLow = minY - ySpan * 0.1
    val yHigh = maxY + ySpan * 0.1
    val minX = points.minOf { it.x }
    val maxX = points.maxOf { it.x }
    val xSpan = (maxX - minX).takeIf { it > 0L } ?: 1L

    val yLabels = listOf(yLabel(maxY), yLabel((minY + maxY) / 2.0), yLabel(minY))
    val leftPadding = yLabels.maxOf { textMeasurer.measure(it, labelStyle).size.width } + 12
    val bottomPadding = textMeasurer.measure("0", labelStyle).size.height + 8

    fun plotOffset(point: LineChartPoint, width: Float, height: Float): Offset {
        val plotWidth = width - leftPadding
        val plotHeight = height - bottomPadding
        val fx = if (points.size == 1) 0.5f else (point.x - minX).toFloat() / xSpan.toFloat()
        val fy = ((point.y - yLow) / (yHigh - yLow)).toFloat()
        return Offset(leftPadding + fx * plotWidth, plotHeight * (1f - fy))
    }

    Canvas(
        modifier = modifier
            .semantics { contentDescription = summary }
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(points) {
                detectTapGestures { tap ->
                    val nearest = points.indices.minByOrNull { i ->
                        val p = plotOffset(points[i], size.width.toFloat(), size.height.toFloat())
                        kotlin.math.abs(p.x - tap.x)
                    }
                    nearest?.let(onPointTap)
                }
            },
    ) {
        val plotHeight = size.height - bottomPadding

        // Gridlines at max / mid / min with their labels.
        listOf(maxY, (minY + maxY) / 2.0, minY).forEachIndexed { i, value ->
            val y = plotOffset(LineChartPoint(minX, value), size.width, size.height).y
            drawLine(gridColor, Offset(leftPadding.toFloat(), y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            val layout = textMeasurer.measure(yLabels[i], labelStyle)
            drawText(layout, topLeft = Offset(0f, (y - layout.size.height / 2f).coerceIn(0f, plotHeight - layout.size.height)))
        }

        // X-axis labels: first and last point dates.
        val firstLabel = textMeasurer.measure(xLabel(points.first().x), labelStyle)
        drawText(firstLabel, topLeft = Offset(leftPadding.toFloat(), size.height - firstLabel.size.height))
        if (points.size > 1) {
            val lastLabel = textMeasurer.measure(xLabel(points.last().x), labelStyle)
            drawText(lastLabel, topLeft = Offset(size.width - lastLabel.size.width, size.height - lastLabel.size.height))
        }

        // The series itself.
        if (points.size > 1) {
            val path = Path()
            points.forEachIndexed { i, point ->
                val p = plotOffset(point, size.width, size.height)
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(path, lineColor, style = Stroke(width = 2.dp.toPx()))
        }
        points.forEachIndexed { i, point ->
            val p = plotOffset(point, size.width, size.height)
            if (i == selectedIndex) {
                drawCircle(selectedColor, radius = 6.dp.toPx(), center = p)
            }
            if (showPoints || i == selectedIndex || points.size == 1) {
                drawCircle(lineColor, radius = 3.dp.toPx(), center = p)
            }
        }
    }
}
