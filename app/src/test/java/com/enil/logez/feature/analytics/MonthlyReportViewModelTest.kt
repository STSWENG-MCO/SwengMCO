package com.enil.logez.feature.analytics

import com.enil.logez.core.data.entity.PersonalRecordEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.PrType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MonthlyReportViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Noon UTC 2026-08-22 — current month resolves to 2026-08 in every plausible test JVM zone. */
    private val nowMillis = 1_787_400_000_000L

    private fun millisOn(date: String): Long =
        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 12 * 3_600_000L

    private fun exercise(id: String, name: String) = Exercise(
        id = id, name = name, exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )

    private fun completedWorkout(id: String, date: String, durationSeconds: Int = 3600) = WorkoutEntity(
        id = id, routineId = null, title = "W$id", notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = millisOn(date), endedAt = millisOn(date) + durationSeconds * 1000L,
        durationSeconds = durationSeconds, createdAt = millisOn(date), updatedAt = millisOn(date),
    )

    private fun newViewModel(
        workoutRepo: FakeWorkoutRepository,
        recordsRepo: FakePersonalRecordsRepository = FakePersonalRecordsRepository(),
    ) = MonthlyReportViewModel(
        workoutRepo,
        FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
        recordsRepo,
        FakeSettingsRepository(),
        FakeClock(currentMillis = nowMillis),
        // The screen's RefreshOnResume drives the first load (no init load) — mirror it here.
    ).also { it.refresh() }

    private fun fixtureWorkoutRepo() = FakeWorkoutRepository(
        workouts = listOf(
            completedWorkout("w-jul", "2026-07-10", 1800),
            completedWorkout("w-jul2", "2026-07-20", 1200),
            completedWorkout("w-aug", "2026-08-05", 900),
        ),
        exercises = listOf(
            WorkoutExerciseEntity(id = "we1", workoutId = "w-jul", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null),
        ),
        sets = listOf(
            WorkoutSetEntity(id = "s1", workoutExerciseId = "we1", orderIndex = 0, setType = SetType.NORMAL, weightKg = 100.0, reps = 5, durationSeconds = null, distanceMeters = null, rpe = null, customMetric = null, isCompleted = true, completedAt = 1L),
        ),
    )

    @Test
    fun `defaults to the last completed calendar month with its totals`() = runTest {
        val vm = newViewModel(fixtureWorkoutRepo())
        val state = vm.uiState.value
        assertEquals(YearMonth.of(2026, 7), state.month)
        assertEquals(2, state.totals.workouts)
        assertEquals(3000L, state.totals.durationSeconds)
        assertEquals(500.0, state.totals.volumeKg, 1e-9)
        // Picker spans first-workout month through the current month.
        assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8)), state.months)
        // 6-month comparison ends at the report month.
        assertEquals(YearMonth.of(2026, 7), state.comparison.last().month)
        assertEquals(6, state.comparison.size)
    }

    @Test
    fun `PR list contains only records achieved inside the month`() = runTest {
        val record = { id: String, at: Long ->
            PersonalRecordEntity(id = id, exerciseId = "ex-1", workoutId = "w-jul", workoutSetId = null, prType = PrType.BEST_1RM, value = 110.0, achievedAt = at)
        }
        val vm = newViewModel(
            fixtureWorkoutRepo(),
            FakePersonalRecordsRepository(
                listOf(record("r-in", millisOn("2026-07-10")), record("r-out", millisOn("2026-06-10"))),
            ),
        )
        assertEquals(1, vm.uiState.value.personalRecords.size)
        assertEquals("Bench Press", vm.uiState.value.personalRecords.single().exerciseName)
    }

    @Test
    fun `an empty month reports zero workouts and no fabricated content`() = runTest {
        // The only workout is in July; the picker still offers August (current month), which is empty.
        val vm2 = newViewModel(
            FakeWorkoutRepository(workouts = listOf(completedWorkout("w-jul", "2026-07-10"))),
        )
        vm2.selectMonth(YearMonth.of(2026, 8))
        val state = vm2.uiState.value
        assertEquals(YearMonth.of(2026, 8), state.month)
        assertEquals(0, state.totals.workouts)
        assertEquals(0, state.personalRecords.size)
        assertNull(state.distributionPrevious)
    }

    @Test
    fun `stepMonth steps from the ViewModel's own resolved month and clamps at the picker's span`() = runTest {
        val vm = newViewModel(fixtureWorkoutRepo())
        // Defaults to July (last completed month). Step forward -> August; forward again -> clamped.
        vm.stepMonth(1)
        assertEquals(YearMonth.of(2026, 8), vm.uiState.value.month)
        vm.stepMonth(1)
        assertEquals(YearMonth.of(2026, 8), vm.uiState.value.month)
        // Back twice: August -> July, then clamped at the first workout month.
        vm.stepMonth(-1)
        vm.stepMonth(-1)
        assertEquals(YearMonth.of(2026, 7), vm.uiState.value.month)
    }

    @Test
    fun `rapid month switches always resolve to the last-selected month, even when the earlier read is slower`() = runTest {
        // Pins rebuildAsync()'s cancel-and-restart guard (see its own doc comment): the PR read is
        // the one suspend point mid-rebuild, so a naive implementation without the guard could let
        // an in-flight rebuild for an OLDER selection publish after a NEWER one, silently reverting
        // it. July is queried first but answers slowest; August is queried second but fastest --
        // if the guard ever regressed (e.g. swapped for a debounce with no cancellation), July's
        // slow answer would land last and this test would see July instead of August.
        fun monthStartMillis(month: YearMonth) = month.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val julyStart = monthStartMillis(YearMonth.of(2026, 7))
        val augustStart = monthStartMillis(YearMonth.of(2026, 8))
        val recordsDelegate = FakePersonalRecordsRepository()
        var slowMode = false
        val racyRecordsRepo = object : PersonalRecordsRepository by recordsDelegate {
            override suspend fun getAchievedBetween(fromMillis: Long, untilMillis: Long): List<PersonalRecordEntity> {
                if (slowMode) {
                    val delayMillis = when (fromMillis) {
                        julyStart -> 1_000L
                        augustStart -> 10L
                        else -> 0L
                    }
                    delay(delayMillis)
                }
                return recordsDelegate.getAchievedBetween(fromMillis, untilMillis)
            }
        }
        val vm = MonthlyReportViewModel(
            fixtureWorkoutRepo(),
            FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"))),
            racyRecordsRepo,
            FakeSettingsRepository(),
            FakeClock(currentMillis = nowMillis),
        )
        vm.refresh()
        advanceUntilIdle() // settle the initial (non-racy) load before arming the race

        slowMode = true
        vm.selectMonth(YearMonth.of(2026, 7)) // starts the slow (1000ms) rebuild
        vm.selectMonth(YearMonth.of(2026, 8)) // cancels it mid-flight, starts the fast (10ms) rebuild
        advanceUntilIdle()

        assertEquals(YearMonth.of(2026, 8), vm.uiState.value.month)
    }

    @Test
    fun `workout dates feed the mini calendar for the selected month only`() = runTest {
        val vm = newViewModel(fixtureWorkoutRepo())
        assertEquals(
            setOf(LocalDate.parse("2026-07-10"), LocalDate.parse("2026-07-20")),
            vm.uiState.value.workoutDates,
        )
    }
}
