package com.enil.logez.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Neutral950,
    primaryContainer = Neutral900,
    onPrimaryContainer = NeonGreen,
    // Dark text on every green fill: light-on-DeepGreen measures 3.30:1 (fails), dark-on 5.17:1.
    secondary = DeepGreen,
    onSecondary = Neutral950,
    secondaryContainer = DeepGreen,
    onSecondaryContainer = Neutral950,
    tertiary = SpringGreen,
    onTertiary = Neutral950,
    tertiaryContainer = Neutral900,
    onTertiaryContainer = SpringGreen,
    background = Neutral950,
    onBackground = Neutral0,
    surface = Neutral900,
    onSurface = Neutral0,
    surfaceVariant = Neutral800,
    onSurfaceVariant = Neutral400,
    outline = Neutral600,
    outlineVariant = Neutral700,
    error = Danger500,
    onError = Neutral950,
    // The `surfaceContainer*` family -- what NavigationBar, dialogs, dropdown menus and bottom
    // sheets default their own background to -- was never set here, so it fell through to
    // Material3's own baseline dark tokens, disconnected from this app's palette (Owner-reported:
    // the nav bar background looked "off the color palette"). This is the exact bug class
    // ADR-0002 already found and fixed once for `tertiary`/`secondaryContainer` -- same root
    // cause (an unset-but-consumed role), a different role this time. Mapped onto the existing
    // neutral ramp rather than introducing new hex values.
    surfaceContainerLowest = Neutral950,
    surfaceContainerLow = Neutral950,
    surfaceContainer = Neutral900,
    surfaceContainerHigh = Neutral800,
    surfaceContainerHighest = Neutral800,
)

/** Dark-only (Owner directive) — applies the design-system color scheme + typography. All screens must be composed under this. */
@Composable
fun LogEzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = LogEzTypography,
        content = content,
    )
}
