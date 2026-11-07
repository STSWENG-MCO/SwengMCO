package com.enil.logez.core.designsystem

import android.graphics.Region
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup

/**
 * Real anatomical front + back body diagram, drawn from [MALE_MUSCLE_REGIONS]/[FEMALE_MUSCLE_REGIONS]
 * (traced SVG data adapted from vue-human-muscle-anatomy — see `MuscleBodyData.kt`'s KDoc). Front
 * and back are already laid out side-by-side in ONE shared coordinate space per gender, so unlike
 * this diagram's prior vulovix-based data, there is only one [Path] per [MuscleGroup] to begin
 * with — no per-side merge step needed. Regions with no [MuscleGroup] (the base body outline)
 * render as unlabeled silhouette context only.
 */
object BodyDiagramRegions {
    /** Groups the diagram can render — every [MuscleGroup] except the three non-anatomical ones. */
    val MAPPABLE: Set<MuscleGroup> = MuscleGroup.entries.toSet() - setOf(MuscleGroup.CARDIO, MuscleGroup.FULL_BODY, MuscleGroup.OTHER)
}

/**
 * Region.setPath rasterizes at integer-coordinate resolution — hit-testing directly in the
 * source's own 0..1024 space would be blocky at region boundaries. Scaling the parsed paths up
 * before building the Region (and scaling tap coordinates by the same factor) trades that for
 * sub-unit precision; independent of the render transform, which uses its own scale.
 */
private const val HIT_TEST_SCALE = 1f

/**
 * Grid search radius, in hit-test-scaled units, for bridging inter-region seams — e.g. a tap
 * landing exactly on the drawn edge between two adjoining muscles. Deliberately modest: some
 * points inside a MuscleGroup's bounding box are correctly unfilled (real anatomy, not a seam),
 * and a wider search would start bridging genuine gaps between DIFFERENT groups at their true
 * boundary.
 */
private const val SEAM_TOLERANCE = 10

private data class ParsedRegions(
    val silhouette: Path,
    val muscles: Map<MuscleGroup, Path>,
    val hitRegions: Map<MuscleGroup, Region>,
)

private fun parseRegions(regions: List<MuscleBodyRegion>): ParsedRegions {
    val silhouette = Path()
    val muscles = mutableMapOf<MuscleGroup, Path>()
    for (region in regions) {
        val path = PathParser().parsePathString(region.pathData).toPath()
        silhouette.addPath(path)
        if (region.group != null) {
            muscles.getOrPut(region.group) { Path() }.addPath(path)
        }
    }

    val hitScale = Matrix().apply { scale(HIT_TEST_SCALE, HIT_TEST_SCALE) }
    val hitRegions = muscles.mapValues { (_, path) ->
        val scaledAndroidPath = Path().apply { addPath(path); transform(hitScale) }.asAndroidPath()
        val rectF = android.graphics.RectF()
        scaledAndroidPath.computeBounds(rectF, true)
        val bounds = android.graphics.Rect()
        rectF.roundOut(bounds)
        Region().apply { setPath(scaledAndroidPath, Region(bounds)) }
    }
    return ParsedRegions(silhouette, muscles, hitRegions)
}

private data class FigureTransform(val originX: Float, val originY: Float, val scale: Float)

/** Uniform "contain" fit of the [MUSCLE_BODY_VIEW_BOX] square figure into the canvas, centered. */
private fun fitTransform(canvasWidth: Float, canvasHeight: Float): FigureTransform {
    val scale = minOf(canvasWidth / MUSCLE_BODY_VIEW_BOX, canvasHeight / MUSCLE_BODY_VIEW_BOX)
    val drawnWidth = MUSCLE_BODY_VIEW_BOX * scale
    val drawnHeight = MUSCLE_BODY_VIEW_BOX * scale
    return FigureTransform(
        originX = (canvasWidth - drawnWidth) / 2f,
        originY = (canvasHeight - drawnHeight) / 2f,
        scale = scale,
    )
}

@Composable
fun BodyDiagram(
    intensity: Map<MuscleGroup, Float>,
    variant: MuscleDiagramVariant,
    modifier: Modifier = Modifier,
    selected: Set<MuscleGroup>? = null,
    onRegionTap: ((MuscleGroup) -> Unit)? = null,
) {
    // `surfaceVariant` (the old base tone) sits almost on top of a Card's own background in this
    // dark theme — an unworked region at 45% alpha over a near-identical bg was barely legible.
    // `outline` is a genuinely lighter neutral, and every region also gets a thin `onSurfaceVariant`
    // stroke so the anatomy reads as line art (like a real muscle chart) rather than relying on
    // fill-vs-background contrast alone.
    val restColor = MaterialTheme.colorScheme.outline
    val lineColor = MaterialTheme.colorScheme.onSurfaceVariant
    val fillColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.tertiary

    val parsed = remember(variant) {
        parseRegions(if (variant == MuscleDiagramVariant.MALE) MALE_MUSCLE_REGIONS else FEMALE_MUSCLE_REGIONS)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f) // MUSCLE_BODY_VIEW_BOX is a square viewBox
            .let { m ->
                if (onRegionTap == null) m
                else m.pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val transform = fitTransform(size.width.toFloat(), size.height.toFloat())
                        val nativeX = (tap.x - transform.originX) / transform.scale
                        val nativeY = (tap.y - transform.originY) / transform.scale
                        val hitX = (nativeX * HIT_TEST_SCALE).toInt()
                        val hitY = (nativeY * HIT_TEST_SCALE).toInt()
                        fun bestMatchAt(dx: Int, dy: Int) = parsed.hitRegions
                            .filter { (_, region) -> region.contains(hitX + dx, hitY + dy) }
                            .minByOrNull { (_, region) -> region.bounds.let { it.width().toLong() * it.height() } }

                        // The exact tap point is authoritative whenever it matches anything — a
                        // genuinely unambiguous tap must never be overridden by a neighboring
                        // group's region just because a probe offset happens to land there first.
                        // A tap landing exactly ON the seam between two adjoining muscles can miss
                        // both by a pixel or two in hit-test space — the grid search (diagonals
                        // included, since seams can run diagonally) exists only to bridge that
                        // exact-miss case, not to second-guess an exact hit.
                        val exact = bestMatchAt(0, 0)
                        val hit = exact ?: (-1..1).asSequence()
                            .flatMap { gx -> (-1..1).asSequence().map { gy -> gx to gy } }
                            .filterNot { (gx, gy) -> gx == 0 && gy == 0 }
                            .firstNotNullOfOrNull { (gx, gy) -> bestMatchAt(gx * SEAM_TOLERANCE, gy * SEAM_TOLERANCE) }
                        hit?.let { (group, _) -> onRegionTap(group) }
                    }
                }
            },
    ) {
        val transform = fitTransform(size.width, size.height)
        translate(left = transform.originX, top = transform.originY) {
            scale(scaleX = transform.scale, scaleY = transform.scale, pivot = Offset.Zero) {
                val lineWidth = 0.75.dp.toPx() / transform.scale
                drawPath(parsed.silhouette, restColor.copy(alpha = 0.55f))
                drawPath(parsed.silhouette, lineColor.copy(alpha = 0.5f), style = Stroke(width = lineWidth))
                parsed.muscles.forEach { (group, path) ->
                    val t = (intensity[group] ?: 0f).coerceIn(0f, 1f)
                    drawPath(path, lerp(restColor, fillColor, t))
                    drawPath(path, lineColor.copy(alpha = 0.6f), style = Stroke(width = lineWidth))
                    if (selected != null && group in selected) {
                        drawPath(path, outlineColor, style = Stroke(width = 1.5.dp.toPx() / transform.scale))
                    }
                }
            }
        }
    }
}
