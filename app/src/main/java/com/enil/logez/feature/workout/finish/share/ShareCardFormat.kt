package com.enil.logez.feature.workout.finish.share

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The summary card's export shape: 9:16 story format only (Owner directive, 2026-09-01 — the
 * square variant was retired). Laid out in dp at the design size and composed for capture under a
 * *fixed* [ShareCardExportDensity], so the encoded PNG is exactly 1080×1920 px on every device —
 * output resolution must not depend on whatever screen density the phone happens to have.
 */
enum class ShareCardFormat(val width: Dp, val height: Dp) {
    STORY(360.dp, 640.dp),
}

/** 3× the 360dp design width → the 1080px-wide canvas social feeds natively expect. */
internal val ShareCardExportDensity = Density(density = 3f)
