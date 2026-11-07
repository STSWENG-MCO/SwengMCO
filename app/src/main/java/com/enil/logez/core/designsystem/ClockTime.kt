package com.enil.logez.core.designsystem

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Time-of-day formatting that follows the phone's 12/24-hour setting (Play-readiness audit,
 * 2026-09-25). Screens used to hard-code "h:mm a", so a phone set to 24-hour time still read
 * "7:44 PM", while two other screens hard-coded "HH:mm" and ignored a 12-hour phone instead.
 */
fun clockTimePattern(is24Hour: Boolean): String = if (is24Hour) "HH:mm" else "h:mm a"

/** For code outside composition, such as the widget. */
fun clockTimeFormatter(context: Context, locale: Locale = Locale.getDefault()): DateTimeFormatter =
    DateTimeFormatter.ofPattern(clockTimePattern(DateFormat.is24HourFormat(context)), locale)

/** Re-read on every composition, so returning from a 12/24-hour change in Settings takes effect. */
@Composable
fun rememberClockTimeFormatter(): DateTimeFormatter {
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    val locale = currentLocale()
    return remember(is24Hour, locale) { DateTimeFormatter.ofPattern(clockTimePattern(is24Hour), locale) }
}
