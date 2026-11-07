package com.enil.logez.feature.widget

import com.enil.logez.core.common.Clock
import com.enil.logez.core.domain.calc.DashboardAggregator
import com.enil.logez.core.domain.calc.WidgetSnapshot
import com.enil.logez.core.domain.calc.WidgetSnapshotCalculator
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.WellnessRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Gathers everything the widget needs into one [WidgetSnapshot]. Takes no Context, so it stays
 * unit-testable against the existing fakes.
 *
 * Steps come from the `daily_wellness_totals` cache rather than Health Connect directly: a widget
 * update runs in the background, where Health Connect's aggregate read throws unless the app holds
 * the separate background-read permission. The cache is written whenever the app reads steps in
 * the foreground.
 */
@Singleton
class WidgetSnapshotLoader @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    private val wellnessRepository: WellnessRepository,
    private val clock: Clock,
) {
    suspend fun load(): WidgetSnapshot {
        // Resolved fresh per call, never cached in a field: a widget outlives any zone change, and
        // a frozen zone would bucket workouts by the old one (the CalendarViewModel lesson).
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone).toLocalDate()

        val settings = settingsRepository.settings.first()
        val workoutDates = workoutRepository.getCompletedWorkoutTimestamps()
            .map { DashboardAggregator.localDate(it, zone) }
        val cachedSteps = wellnessRepository.getByDate(today.format(DateTimeFormatter.ISO_LOCAL_DATE))

        return WidgetSnapshotCalculator.snapshot(
            completedWorkoutDates = workoutDates,
            today = today,
            firstDayOfWeek = settings.firstDayOfWeek,
            targetDaysThisWeek = settings.weeklyActiveDayTarget,
            steps = cachedSteps?.steps,
            stepsUpdatedAt = cachedSteps?.let {
                LocalDateTime.ofInstant(Instant.ofEpochMilli(it.updatedAt), zone)
            },
        )
    }
}
