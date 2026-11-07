package com.enil.logez.feature.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import com.enil.logez.MainActivity
import com.enil.logez.core.domain.calc.WidgetSnapshot
import dagger.hilt.android.EntryPointAccessors
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.glance.LocalContext
import com.enil.logez.core.designsystem.clockTimeFormatter
import com.enil.logez.R

private const val TAG = "WeeklyProgressWidget"
private val SMALL = androidx.compose.ui.unit.DpSize(110.dp, 110.dp)
private val WIDE = androidx.compose.ui.unit.DpSize(250.dp, 110.dp)

class WeeklyProgressWidget : GlanceAppWidget() {
    // Responsive rather than Exact: both layouts are pre-rendered at update time, so resizing is
    // instant instead of waiting on a new Glance session.
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        // Loaded once, never observed: a Glance session times out within seconds, so a Flow
        // subscription started here would die and the widget would silently stop updating.
        // Updates are pushed via WidgetRefresher instead.
        val snapshot = runCatching { entryPoint.widgetSnapshotLoader().load() }
            .onFailure { entryPoint.appLogger().e(TAG, "Widget snapshot load failed", it) }
            .getOrNull()

        // An exception escaping provideGlance strands the widget on its loading layout with no way
        // back short of removing and re-adding it, so failure renders as empty state instead.
        provideContent { WeeklyProgressContent(snapshot) }
    }
}

class WeeklyProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    // Not @AndroidEntryPoint and no @Inject fields: this runs during construction, before Hilt
    // would populate anything. The widget reaches DI itself inside provideGlance.
    override val glanceAppWidget: GlanceAppWidget = WeeklyProgressWidget()
}

@Composable
private fun WeeklyProgressContent(snapshot: WidgetSnapshot?) {
    val isWide = LocalSize.current.width >= WIDE.width

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.background)
            .cornerRadius(18.dp) // API 31+; silently square-cornered below, which is acceptable
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        if (snapshot == null) {
            Text("—", style = TextStyle(color = WidgetColors.secondaryText, fontSize = 20.dp.toSp()))
            return@Column
        }

        Text(
            text = "${snapshot.activeDaysThisWeek}/${snapshot.targetDaysThisWeek}",
            style = TextStyle(
                color = WidgetColors.primaryText,
                fontSize = if (isWide) 34.dp.toSp() else 30.dp.toSp(),
                fontWeight = FontWeight.Bold,
            ),
        )
        Text(
            text = LocalContext.current.getString(R.string.widget_days_this_week),
            style = TextStyle(color = WidgetColors.secondaryText, fontSize = 12.dp.toSp()),
        )

        Spacer(GlanceModifier.height(10.dp))
        WeekDots(snapshot)

        if (snapshot.weekStreakWeeks > 0) {
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = weekStreakLabel(LocalContext.current, snapshot.weekStreakWeeks),
                style = TextStyle(color = WidgetColors.accent, fontSize = 12.dp.toSp(), fontWeight = FontWeight.Medium),
            )
        }

        if (isWide && snapshot.steps != null) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = stepsLabel(LocalContext.current, snapshot),
                style = TextStyle(color = WidgetColors.secondaryText, fontSize = 11.dp.toSp()),
            )
        }
    }
}

/**
 * One dot per day of the week, filled for days trained. Today's is drawn larger.
 *
 * Spacing comes from each dot's own padding rather than Spacer siblings: a Glance Row rejects more
 * than ten children at inflation time, and seven dots interleaved with six spacers is thirteen.
 */
@Composable
private fun WeekDots(snapshot: WidgetSnapshot) {
    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
        snapshot.weekDayStates.forEachIndexed { index, trained ->
            val isToday = index == snapshot.todayIndexInWeek
            Column(
                modifier = GlanceModifier
                    .padding(horizontal = 2.dp)
                    .size(if (isToday) 12.dp else 10.dp)
                    .cornerRadius(6.dp)
                    .background(if (trained) WidgetColors.accent else WidgetColors.emptyTrack),
                content = {},
            )
        }
    }
}

private fun weekStreakLabel(context: Context, weeks: Int): String =
    context.resources.getQuantityString(R.plurals.widget_week_streak, weeks, weeks)

private fun stepsLabel(context: Context, snapshot: WidgetSnapshot): String {
    val steps = "%,d".format(Locale.getDefault(), snapshot.steps ?: 0L)
    // Stated rather than hidden: nothing refreshes steps in the background, so the number can be
    // hours old and saying so is more useful than presenting it as live. The clock follows the
    // phone's 12/24-hour setting.
    val asOf = snapshot.stepsAsOf?.format(clockTimeFormatter(context))
    return if (asOf == null) context.getString(R.string.widget_steps, steps) else context.getString(R.string.widget_steps_as_of, steps, asOf)
}

/** Glance text sizes are TextUnit; the theme's tokens are Dp. */
private fun androidx.compose.ui.unit.Dp.toSp() = androidx.compose.ui.unit.TextUnit(
    value,
    androidx.compose.ui.unit.TextUnitType.Sp,
)
