package com.enil.logez.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em

/**
 * v4.0's shared TopAppBar title (Owner: every tab root's header should read the same way) —
 * uppercase, bold, letterspaced Chakra Petch (`titleLarge` already carries the display font from
 * Type.kt). Plain type, no decoration: the glow primitives this comment used to point at were
 * deleted with `Glow.kt` in `ef2230f` (2026-08-27) and the app has none anywhere now.
 */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(currentLocale()),
        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.06.em),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * Owner-requested redesign pass (2026-09-12): every `TopAppBar` in the app left `colors` at the
 * Material3 default, which fills the container with [MaterialTheme.colorScheme.surface] —
 * `Neutral900`, one step lighter than the `Neutral950` [MaterialTheme.colorScheme.background]
 * every screen body sits on. Confirmed on-device (pixel-sampled a real screenshot, not eyeballed):
 * header `#12161A` vs. body `#0A0D0F`, a real, visible seam at the header/body boundary on every
 * single screen. `TopAppBar(colors = logEzTopAppBarColors())` at every call site closes that gap
 * so the header reads as part of the same surface as the rest of the screen, not a separate bar
 * sitting on top of it.
 */
@Composable
fun logEzTopAppBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
