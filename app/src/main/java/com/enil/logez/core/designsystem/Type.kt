package com.enil.logez.core.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.enil.logez.R

/**
 * Neon Lab rebrand's type system (docs/adr/0003-neon-lab-rebrand.md) — three fonts, each with one
 * job, mirroring the approved design-canvas mockup: Chakra Petch for anything that should feel like
 * a hazard-label headline, IBM Plex Sans for everything else UI reads as prose or chrome, IBM Plex
 * Mono for numeric data (weights, reps, timers, chart axis labels) so it reads like a lab readout.
 * All three are SIL Open Font License (attribution: docs/licenses/OFL_*.txt) and bundled as
 * `res/font/` resources -- no network font fetch, matching this app's offline-first posture.
 *
 * Only [LogEzTypography]'s existing roles (title/body/label) change family+weight here; sizes and
 * line-heights are untouched to avoid layout regressions in this pass. [LogEzMono] is new
 * infrastructure for the numeric-data treatment -- adopting it at individual call sites (chart
 * axis labels, set-row weights, timers) is deliberately a separate, later pass, not bundled into
 * this foundational token change.
 */
private val chakraPetch = FontFamily(
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

/** IBM Plex Sans ships as a single variable font (no more static per-weight files) -- one resource, three named instances via [FontVariation]. */
@OptIn(ExperimentalTextApi::class)
private val plexSans = FontFamily(
    Font(R.font.ibm_plex_sans_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.ibm_plex_sans_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.ibm_plex_sans_variable, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
)

private val plexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

val LogEzTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = chakraPetch,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = chakraPetch,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = plexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = plexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = plexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

/** Numeric-data treatment (weights, reps, timers, chart axis/value labels) -- not yet adopted at call sites; see this file's KDoc. */
object LogEzMono {
    val dataLarge: TextStyle = TextStyle(fontFamily = plexMono, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp)
    val dataMedium: TextStyle = TextStyle(fontFamily = plexMono, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
    val dataSmall: TextStyle = TextStyle(fontFamily = plexMono, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 16.sp)
}
