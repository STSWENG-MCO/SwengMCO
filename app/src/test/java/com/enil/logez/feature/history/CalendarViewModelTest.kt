package com.enil.logez.feature.history

import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** PHASE2_PLAN.md §5.2 Calendar screen + §8.7's date bucketing behind it. */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val zone: ZoneId = ZoneId.systemDefault()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun millis(isoLocal: String): Long =
        LocalDateTime.parse(isoLocal).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `completed workouts are bucketed onto their local calendar day`() = runTest {
        val vm = viewModel(
            workouts = listOf(
                completed("w1", millis("2026-08-10T07:30")),
                completed("w2", millis("2026-08-10T19:15")), // same day, second session
                completed("w3", millis("2026-08-12T08:00")),
            ),
            now = millis("2026-08-14T12:00"),
        )

        val counts = vm.uiState.value.countsByDate
        assertEquals(2, counts[LocalDate.of(2026, 8, 10)])
        assertEquals(1, counts[LocalDate.of(2026, 8, 12)])
        assertEquals(null, counts[LocalDate.of(2026, 8, 11)])
    }

    @Test
    fun `an IN_PROGRESS workout never appears on the calendar`() = runTest {
        val vm = viewModel(
            workouts = listOf(
                completed("w1", millis("2026-08-10T07:30")),
                WorkoutEntity(
                    id = "live", routineId = null, title = "Live", notes = null,
                    status = WorkoutStatus.IN_PROGRESS, startedAt = millis("2026-08-11T07:30"),
                    endedAt = null, durationSeconds = 0, createdAt = 0, updatedAt = 0,
                ),
            ),
            now = millis("2026-08-14T12:00"),
        )

        assertEquals(setOf(LocalDate.of(2026, 8, 10)), vm.uiState.value.countsByDate.keys)
    }

    @Test
    fun `a backdated workout lands on the date it was backdated to`() = runTest {
        // §8.7: "backdated workouts appear on their startedAt date".
        val vm = viewModel(
            workouts = listOf(completed("w-back", millis("2026-07-04T09:00"))),
            now = millis("2026-08-14T12:00"),
        )

        assertTrue(vm.uiState.value.countsByDate.containsKey(LocalDate.of(2026, 7, 4)))
    }

    @Test
    fun `paging back stops at the start of the calendar window, not before`() = runTest {
        // M20f bounded the calendar to a finite [monthRangeStart, monthRangeEnd] window (the
        // library's swipe range needs one); a prior version of this test pinned the OLD unbounded
        // contract (20 taps -> Dec 2024) and stayed green after the bound landed, because nothing
        // in showPreviousMonth clamped against it -- the chevron could walk displayedMonth outside
        // the range the calendar was actually built with, where the library's scrollToMonth
        // silently no-ops and the header label and the rendered grid permanently disagree (found in
        // the M20a-h code audit, 2026-09-08). This asserts the bound is real, not just declared.
        val vm = viewModel(workouts = emptyList(), now = millis("2026-08-14T12:00"))
        assertEquals(YearMonth.of(2026, 8), vm.uiState.value.displayedMonth)
        assertEquals(YearMonth.of(2025, 8), vm.uiState.value.monthRangeStart)

        repeat(20) { vm.showPreviousMonth() }

        assertEquals(YearMonth.of(2025, 8), vm.uiState.value.displayedMonth)
    }

    @Test
    fun `paging forward stops at the end of the calendar window`() = runTest {
        val vm = viewModel(workouts = emptyList(), now = millis("2026-08-14T12:00"))
        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.monthRangeEnd)

        repeat(5) { vm.showNextMonth() }

        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.displayedMonth)
    }

    @Test
    fun `paging forward and back returns to the starting month`() = runTest {
        val vm = viewModel(workouts = emptyList(), now = millis("2026-08-14T12:00"))
        // Step off the current-month edge first -- from the current month itself, two forward taps
        // would hit monthRangeEnd (current + 1) and the second tap would clip, breaking the
        // round-trip this test is actually about.
        vm.showPreviousMonth()
        val start = vm.uiState.value.displayedMonth

        vm.showNextMonth(); vm.showNextMonth(); vm.showPreviousMonth(); vm.showPreviousMonth()

        assertEquals(start, vm.uiState.value.displayedMonth)
    }

    @Test
    fun `refresh re-derives the time zone, so a real zone change is not left bucketing by the old one`() = runTest {
        // Regression: `zone` used to be cached at construction, so a ViewModel that outlived a real
        // system zone change (travel with automatic time zone, or a manual Settings change) kept
        // bucketing by the stale zone while History and Workout Detail — which resolve
        // ZoneId.systemDefault() fresh at render time — switched immediately. Same workout, two
        // different dates in the same app.
        val previousDefault = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Tokyo"))
            // 2026-08-09T22:00Z = 2026-08-10T07:00 JST (Tokyo) = 2026-08-09T23:00 BST (London).
            val instant = LocalDateTime.parse("2026-08-09T22:00").toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
            val vm = CalendarViewModel(
                FakeWorkoutRepository(workouts = listOf(completed("w1", instant))),
                FakeSettingsRepository(),
                FakeClock(currentMillis = instant),
            )
            assertTrue("bucketed by the Tokyo zone at construction", vm.uiState.value.countsByDate.containsKey(LocalDate.of(2026, 8, 10)))

            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Europe/London"))
            vm.refresh()

            assertTrue(
                "the same workout must move to the live (London) zone's date, not stay on Tokyo's",
                vm.uiState.value.countsByDate.containsKey(LocalDate.of(2026, 8, 9)),
            )
            assertTrue(vm.uiState.value.countsByDate[LocalDate.of(2026, 8, 10)] == null)
        } finally {
            java.util.TimeZone.setDefault(previousDefault)
        }
    }

    @Test
    fun `the streak banner honours the first-day-of-week setting`() = runTest {
        // Sun 9 Aug and Mon 10 Aug 2026 fall in the SAME week when the week starts Sunday, but in
        // two consecutive weeks when it starts Monday — so the same data yields a different streak.
        val workouts = listOf(
            completed("w1", millis("2026-08-09T09:00")), // Sunday
            completed("w2", millis("2026-08-10T09:00")), // Monday
        )
        val now = millis("2026-08-10T12:00")

        val mondayStart = viewModel(workouts, now, DayOfWeek.MONDAY)
        val sundayStart = viewModel(workouts, now, DayOfWeek.SUNDAY)

        assertEquals(2, mondayStart.uiState.value.weeklyStreak)
        assertEquals(1, sundayStart.uiState.value.weeklyStreak)
    }

    @Test
    fun `refresh re-derives today, so a midnight the screen slept through is noticed`() = runTest {
        // Regression: `today` used to be resolved once at construction and never revisited, so a
        // ViewModel that outlived a midnight (it's scoped to the NavBackStackEntry, not the screen's
        // composition) kept marking yesterday as "today" and fed StreakCalculator a stale anchor.
        val clock = FakeClock(currentMillis = millis("2026-08-30T22:00")) // Sunday night
        val vm = CalendarViewModel(
            FakeWorkoutRepository(workouts = listOf(completed("w1", millis("2026-08-18T09:00")))), // week of Mon 17 Aug
            FakeSettingsRepository(UserSettings(firstDayOfWeek = DayOfWeek.MONDAY)),
            clock,
        )
        // Grace branch: currentWeekStart (24 Aug) has no workout, but the prior week (17 Aug) does.
        assertEquals(LocalDate.of(2026, 8, 30), vm.uiState.value.today)
        assertEquals(1, vm.uiState.value.weeklyStreak)

        clock.currentMillis = millis("2026-08-31T00:05") // rolled into Monday, a new week
        vm.refresh()

        assertEquals("the today-marker must move with the clock, not stay frozen", LocalDate.of(2026, 8, 31), vm.uiState.value.today)
        assertEquals("the grace window has now passed, so the stale streak must not survive", 0, vm.uiState.value.weeklyStreak)
    }

    @Test
    fun `no workouts means no streak and an undecorated calendar`() = runTest {
        val vm = viewModel(workouts = emptyList(), now = millis("2026-08-14T12:00"))

        assertEquals(0, vm.uiState.value.weeklyStreak)
        assertTrue(vm.uiState.value.countsByDate.isEmpty())
    }

    @Test
    fun `setDisplayedMonth updates displayedMonth`() = runTest {
        // M20f: this is how a swipe on the library's calendar feeds back into the same
        // displayedMonth the chevrons and header label already read.
        val vm = viewModel(workouts = emptyList(), now = millis("2026-08-14T12:00"))

        vm.setDisplayedMonth(YearMonth.of(2026, 9)) // in range: exactly monthRangeEnd

        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.displayedMonth)
    }

    @Test
    fun `setDisplayedMonth clamps a month outside the calendar window`() = runTest {
        // The library itself can never hand back an out-of-range month (it clamps its own swipe
        // range), but this is a public VM method with no other caller enforcing that -- clamp here
        // too, so it can never disagree with the chevrons' own bound.
        val vm = viewModel(workouts = emptyList(), now = millis("2026-08-14T12:00"))

        vm.setDisplayedMonth(YearMonth.of(2026, 11)) // beyond monthRangeEnd (2026-09)

        assertEquals(YearMonth.of(2026, 9), vm.uiState.value.displayedMonth)
    }

    @Test
    fun `earliestWorkoutMonth is null with no workouts`() = runTest {
        val vm = viewModel(workouts = emptyList(), now = millis("2026-08-14T12:00"))

        assertEquals(null, vm.uiState.value.earliestWorkoutMonth)
    }

    @Test
    fun `earliestWorkoutMonth is the month of the oldest workout`() = runTest {
        val vm = viewModel(
            workouts = listOf(
                completed("w1", millis("2026-08-11T07:30")),
                completed("w2", millis("2026-08-18T07:30")),
            ),
            now = millis("2026-08-19T12:00"),
        )

        assertEquals(YearMonth.of(2026, 8), vm.uiState.value.earliestWorkoutMonth)
    }

    @Test
    fun `tapping a day returns exactly that day's workouts, not the next day's`() = runTest {
        val vm = viewModel(
            workouts = listOf(
                completed("w-early", millis("2026-08-10T00:05"), title = "Early"),
                completed("w-late", millis("2026-08-10T23:55"), title = "Late"),
                completed("w-next", millis("2026-08-11T00:05"), title = "Next day"),
            ),
            now = millis("2026-08-14T12:00"),
        )

        val onThe10th = vm.workoutsOn(LocalDate.of(2026, 8, 10))

        assertEquals(listOf("Early", "Late"), onThe10th.map { it.title })
    }

    // --- fixture ---

    private fun viewModel(
        workouts: List<WorkoutEntity>,
        now: Long,
        firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    ) = CalendarViewModel(
        FakeWorkoutRepository(workouts = workouts),
        FakeSettingsRepository(UserSettings(firstDayOfWeek = firstDayOfWeek)),
        FakeClock(currentMillis = now),
    )

    private fun completed(id: String, startedAt: Long, title: String = "Session") = WorkoutEntity(
        id = id, routineId = null, title = title, notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = startedAt, endedAt = startedAt + 3_600_000L, durationSeconds = 3600,
        createdAt = startedAt, updatedAt = startedAt,
    )
}
