package com.enil.logez.feature.widget

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.DailyWellnessTotal
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWellnessRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** M23b: that the widget is fed the right inputs, including from the right day. */
class WidgetSnapshotLoaderTest {
    private val clock = FakeClock()
    private val zone = ZoneId.systemDefault()
    private val today = Instant.ofEpochMilli(clock.currentMillis).atZone(zone).toLocalDate()

    private fun completedWorkout(id: String, startedAt: Long) = WorkoutEntity(
        id = id, routineId = null, title = "Session", notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = startedAt, endedAt = startedAt + 1_000, durationSeconds = 60,
        createdAt = startedAt, updatedAt = startedAt,
    )

    private fun loader(
        workoutRepo: FakeWorkoutRepository = FakeWorkoutRepository(),
        settings: UserSettings = UserSettings(),
        wellnessRepo: FakeWellnessRepository = FakeWellnessRepository(),
    ) = WidgetSnapshotLoader(workoutRepo, FakeSettingsRepository(settings), wellnessRepo, clock)

    @Test
    fun `a workout completed today counts as an active day`() = runTest {
        val repo = FakeWorkoutRepository(listOf(completedWorkout("w1", clock.currentMillis)))
        assertEquals(1, loader(repo).load().activeDaysThisWeek)
    }

    @Test
    fun `the target comes from settings, not a constant`() = runTest {
        assertEquals(6, loader(settings = UserSettings(weeklyActiveDayTarget = 6)).load().targetDaysThisWeek)
    }

    @Test
    fun `the first day of week setting re-buckets the week grid`() = runTest {
        // FakeClock's anchor is a Monday, so it is the first slot of a Monday-start week and the
        // second of a Sunday-start one.
        assertEquals(DayOfWeek.MONDAY, today.dayOfWeek)
        assertEquals(0, loader(settings = UserSettings(firstDayOfWeek = DayOfWeek.MONDAY)).load().todayIndexInWeek)
        assertEquals(1, loader(settings = UserSettings(firstDayOfWeek = DayOfWeek.SUNDAY)).load().todayIndexInWeek)
    }

    @Test
    fun `steps come from today's cached row`() = runTest {
        val iso = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val wellness = FakeWellnessRepository(
            listOf(DailyWellnessTotal(iso, steps = 9_312L, caloriesBurned = null, updatedAt = clock.currentMillis)),
        )
        assertEquals(9_312L, loader(wellnessRepo = wellness).load().steps)
    }

    @Test
    fun `a cached row for another day is not read at all`() = runTest {
        val yesterday = today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val wellness = FakeWellnessRepository(
            listOf(DailyWellnessTotal(yesterday, steps = 9_312L, caloriesBurned = null, updatedAt = clock.currentMillis)),
        )
        assertNull(loader(wellnessRepo = wellness).load().steps)
    }

    @Test
    fun `no cached steps renders as absent rather than zero`() = runTest {
        assertNull(loader().load().steps)
    }

    @Test
    fun `today is re-resolved per call, so the widget survives midnight`() = runTest {
        val repo = FakeWorkoutRepository(listOf(completedWorkout("w1", clock.currentMillis)))
        val loader = loader(repo)
        assertEquals(1, loader.load().activeDaysThisWeek)

        // Same loader instance, eight days later: the workout is now in a previous week. A loader
        // that cached "today" at construction would still report it as this week's.
        clock.currentMillis += 8 * 24 * 60 * 60 * 1000L
        assertEquals(0, loader.load().activeDaysThisWeek)
    }
}
