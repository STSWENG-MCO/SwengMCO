package com.enil.logez.feature.workout.finish.share

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer

/**
 * The same [ShareCard] the dialog previews and exports, drawn at the page's width. It is scaled,
 * never re-laid-out, so the slide shows exactly what a shared image will. Taller than the page on
 * most phones, so the slide scrolls it.
 */
@Composable
internal fun ShareCardOverview(
    data: ShareCardData,
    modifier: Modifier = Modifier,
) {
    val format = ShareCardFormat.STORY
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val scale = maxWidth / format.width
        Box(Modifier.fillMaxWidth().height(format.height * scale).clipToBounds()) {
            Box(
                Modifier
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }.wrapContentSize(align = Alignment.TopStart, unbounded = true),
            ) {
                ShareCard(data = data, format = format)
            }
        }
    }
}
