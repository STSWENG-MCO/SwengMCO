package com.enil.logez.feature.routines

import com.enil.logez.core.data.dao.RoutineExercisePreviewRow
import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeWellnessRepository
import com.enil.logez.fakes.FakeWidgetRefresher
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.activity.ActivityTrackingController
import com.enil.logez.feature.activity.ActivityTrackingStartResult
import com.enil.logez.core.wellness.DailyStepCount
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.StartResult
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.session.WorkoutSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutTabViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun newViewModel(
        routineRepo: FakeRoutineRepository,
        clock: FakeClock = FakeClock(),
        workoutRepo: FakeWorkoutRepository = FakeWorkoutRepository(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(),
        activityTrackingController: ActivityTrackingController = ActivityTrackingController(
            workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), clock, CoroutineScope(UnconfinedTestDispatcher()),
        ),
        healthMetricsSource: FakeHealthMetricsSource = FakeHealthMetricsSource(),
        wellnessRepo: FakeWellnessRepository = FakeWellnessRepository(),
        widgetRefresher: FakeWidgetRefresher = FakeWidgetRefresher(),
    ): WorkoutTabViewModel {
        val sessionController = WorkoutSessionController(FakeActiveSessionRepository(), clock, FakeElapsedRealtimeClock(), CoroutineScope(UnconfinedTestDispatcher()))
        val workoutStarter = WorkoutStarter(workoutRepo, routineRepo, clock)
        return WorkoutTabViewModel(
            routineRepo, workoutRepo, settingsRepo, exerciseRepo,
            workoutStarter, sessionController, activityTrackingController,
            SessionDiscarder(workoutStarter, sessionController, activityTrackingController),
            healthMetricsSource, wellnessRepo, widgetRefresher, clock,
        )
    }

    /** M21a: matches the real `exercises_seed.json` shape closely enough for these tests. */
    private fun cardioExercise(id: String, name: String) = Exercise(
        id = id, name = name, exerciseType = ExerciseType.DISTANCE_DURATION, primaryMuscleGroup = MuscleGroup.CARDIO,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.NONE, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )

    @Test
    fun `M8c heatmap counts a completed workout on its own local date`() = runTest {
        val startedAt = 1_700_000_000_000L // arbitrary fixed instant
        val expectedDate = java.time.Instant.ofEpochMilli(startedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(
                WorkoutEntity(
                    id = "w1", routineId = null, title = "Workout", notes = null,
                    status = WorkoutStatus.COMPLETED, startedAt = startedAt, endedAt = startedAt + 1000,
                    durationSeconds = 1000, createdAt = startedAt, updatedAt = startedAt,
                ),
            ),
        )
        val vm = newViewModel(FakeRoutineRepository(), workoutRepo = workoutRepo)

        assertEquals(1, vm.uiState.value.heatmapCounts[expectedDate])
    }

    @Test
    fun `M8c heatmap has no counts when there are no completed workouts`() = runTest {
        val vm = newViewModel(FakeRoutineRepository())
        assertEquals(emptyMap<java.time.LocalDate, Int>(), vm.uiState.value.heatmapCounts)
    }

    @Test
    fun `weeklyStreak reflects a workout completed this week`() = runTest {
        val clock = FakeClock()
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(
                WorkoutEntity(
                    id = "w1", routineId = null, title = "Workout", notes = null,
                    status = WorkoutStatus.COMPLETED, startedAt = clock.currentMillis, endedAt = clock.currentMillis + 1000,
                    durationSeconds = 1000, createdAt = clock.currentMillis, updatedAt = clock.currentMillis,
                ),
            ),
        )
        val vm = newViewModel(FakeRoutineRepository(), clock = clock, workoutRepo = workoutRepo)

        assertEquals(1, vm.uiState.value.weeklyStreak)
    }

    @Test
    fun `weeklyStreak is zero with no completed workouts`() = runTest {
        val vm = newViewModel(FakeRoutineRepository())
        assertEquals(0, vm.uiState.value.weeklyStreak)
    }

    @Test
    fun `dailyStreak reflects a workout completed today`() = runTest {
        val clock = FakeClock()
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(
                WorkoutEntity(
                    id = "w1", routineId = null, title = "Workout", notes = null,
                    status = WorkoutStatus.COMPLETED, startedAt = clock.currentMillis, endedAt = clock.currentMillis + 1000,
                    durationSeconds = 1000, createdAt = clock.currentMillis, updatedAt = clock.currentMillis,
                ),
            ),
        )
        val vm = newViewModel(FakeRoutineRepository(), clock = clock, workoutRepo = workoutRepo)

        assertEquals(1, vm.uiState.value.dailyStreak)
    }

    @Test
    fun `dailyStreak is zero with no completed workouts`() = runTest {
        val vm = newViewModel(FakeRoutineRepository())
        assertEquals(0, vm.uiState.value.dailyStreak)
    }

    @Test
    fun `showHeatmap and showGoals default true`() = runTest {
        val vm = newViewModel(FakeRoutineRepository())
        assertEquals(true, vm.uiState.value.showHeatmap)
        assertEquals(true, vm.uiState.value.showGoals)
    }

    @Test
    fun `showHeatmap and showGoals false in Settings hide via uiState`() = runTest {
        val settingsRepo = FakeSettingsRepository(
            com.enil.logez.core.domain.model.UserSettings(showHeatmap = false, showGoals = false),
        )
        val vm = newViewModel(FakeRoutineRepository(), settingsRepo = settingsRepo)
        assertEquals(false, vm.uiState.value.showHeatmap)
        assertEquals(false, vm.uiState.value.showGoals)
    }

    @Test
    fun `buildExercisePreview joins up to two names then counts the rest`() {
        assertEquals("", buildExercisePreview(emptyList()))
        assertEquals("Bench Press", buildExercisePreview(listOf("Bench Press")))
        assertEquals("Bench Press, Squat", buildExercisePreview(listOf("Bench Press", "Squat")))
        assertEquals("Bench Press, Squat, +3 more", buildExercisePreview(listOf("Bench Press", "Squat", "Row", "Curl", "Fly")))
    }

    @Test
    fun `folders and root routines are grouped correctly with exercise preview attached`() = runTest {
        val repo = FakeRoutineRepository(
            folders = listOf(RoutineFolderEntity(id = "f1", name = "Push Pull", orderIndex = 0, createdAt = 0, updatedAt = 0)),
            routines = listOf(
                RoutineEntity(id = "r1", folderId = "f1", name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0),
                RoutineEntity(id = "r2", folderId = null, name = "Full Body", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0),
            ),
            previewRows = listOf(
                RoutineExercisePreviewRow(routineId = "r1", exerciseName = "Bench Press", orderIndex = 0),
                RoutineExercisePreviewRow(routineId = "r1", exerciseName = "Incline Press", orderIndex = 1),
            ),
        )
        val vm = newViewModel(repo)

        val state = vm.uiState.value
        assertEquals(1, state.folders.size)
        assertEquals("r1", state.folders[0].routines.single().routine.id)
        assertEquals("Bench Press, Incline Press", state.folders[0].routines.single().exercisePreview)
        assertEquals("r2", state.rootRoutines.single().routine.id)
        assertEquals("", state.rootRoutines.single().exercisePreview) // no preview rows for r2
    }

    @Test
    fun `reorderRoutines within a folder only re-indexes that folder's routines`() = runTest {
        val repo = FakeRoutineRepository(
            folders = listOf(RoutineFolderEntity(id = "f1", name = "Push Pull", orderIndex = 0, createdAt = 0, updatedAt = 0)),
            routines = listOf(
                RoutineEntity(id = "p1", folderId = "f1", name = "Push", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0),
                RoutineEntity(id = "p2", folderId = "f1", name = "Pull", notes = null, orderIndex = 1, createdAt = 0, updatedAt = 0),
                RoutineEntity(id = "root", folderId = null, name = "Full Body", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0),
            ),
        )
        val vm = newViewModel(repo)

        // M20a: a drag inside folder f1 commits that folder's full id list, and nothing else.
        vm.reorderRoutines(listOf("p2", "p1"))

        val state = vm.uiState.value
        assertEquals(listOf("p2", "p1"), state.folders.single().routines.map { it.routine.id })
        assertEquals(0, repo.getRoutineById("p2")!!.orderIndex)
        assertEquals(1, repo.getRoutineById("p1")!!.orderIndex)
        assertEquals(0, repo.getRoutineById("root")!!.orderIndex) // untouched bucket
        assertEquals("root", state.rootRoutines.single().routine.id)
    }

    @Test
    fun `creating a folder inserts it at the top and shifts existing folders down`() = runTest {
        val repo = FakeRoutineRepository(folders = listOf(RoutineFolderEntity(id = "f1", name = "Existing", orderIndex = 0, createdAt = 0, updatedAt = 0)))
        val vm = newViewModel(repo)

        vm.createFolder("New Folder")

        val folders = vm.uiState.value.folders.map { it.folder }
        assertEquals("New Folder", folders[0].name)
        assertEquals(0, folders[0].orderIndex)
        assertEquals("Existing", folders[1].name)
        assertEquals(1, folders[1].orderIndex)
    }

    @Test
    fun `duplicating a routine copies its exercises and sets with fresh ids and a copy suffix`() = runTest {
        val repo = FakeRoutineRepository(
            routines = listOf(RoutineEntity(id = "r1", folderId = null, name = "Push Day", notes = null, orderIndex = 0, createdAt = 100L, updatedAt = 100L)),
            exercises = listOf(RoutineExerciseEntity(id = "re1", routineId = "r1", exerciseId = "ex-1", orderIndex = 0, supersetGroup = null, restTimerSeconds = null, notes = null)),
            sets = listOf(RoutineSetEntity(id = "s1", routineExerciseId = "re1", orderIndex = 0, setType = SetType.NORMAL, targetWeightKg = 100.0, targetReps = 5, targetRepRangeMin = null, targetRepRangeMax = null, targetDurationSeconds = null, targetDistanceMeters = null)),
        )
        val vm = newViewModel(repo, FakeClock(currentMillis = 5_000L))

        val newId = vm.duplicateRoutine("r1")

        assertNotNull(newId)
        assertNotEquals("r1", newId)
        val copy = repo.getRoutineById(newId!!)!!
        assertEquals("Push Day (copy)", copy.name)
        assertEquals(0, copy.orderIndex) // new copy at top
        assertEquals(1, repo.getRoutineById("r1")!!.orderIndex) // original pushed down

        val copyExercises = repo.getExercisesForRoutine(newId)
        assertEquals(1, copyExercises.size)
        assertNotEquals("re1", copyExercises[0].id)
        val copySets = repo.getSetsForRoutineExercise(copyExercises[0].id)
        assertEquals(100.0, copySets.single().targetWeightKg)
        assertEquals(5, copySets.single().targetReps)

        // Original untouched.
        assertEquals(1, repo.getExercisesForRoutine("r1").size)
        assertEquals("re1", repo.getExercisesForRoutine("r1").single().id)
    }

    @Test
    fun `deleting a folder moves its routines to root instead of deleting them`() = runTest {
        val repo = FakeRoutineRepository(
            folders = listOf(RoutineFolderEntity(id = "f1", name = "Push Pull", orderIndex = 0, createdAt = 0, updatedAt = 0)),
            routines = listOf(RoutineEntity(id = "r1", folderId = "f1", name = "Push Day", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0)),
        )
        val vm = newViewModel(repo)

        vm.deleteFolder(repo.getFolderById("f1")!!)

        assertNull(repo.getFolderById("f1"))
        assertNull(repo.getRoutineById("r1")!!.folderId) // survives, moved to root
    }

    // --- M21a "Track a walk/run" (GPS) ---

    @Test
    fun `quickTrackExercises resolves once both Running (Outdoor) and Walking (Outdoor) exist`() = runTest {
        val exerciseRepo = FakeExerciseRepository(
            listOf(cardioExercise("ex-run", "Running (Outdoor)"), cardioExercise("ex-walk", "Walking (Outdoor)")),
        )
        val vm = newViewModel(FakeRoutineRepository(), exerciseRepo = exerciseRepo)

        val exercises = vm.quickTrackExercises.value
        assertNotNull(exercises)
        assertEquals("ex-run", exercises!!.running.id)
        assertEquals("ex-walk", exercises.walking.id)
    }

    @Test
    fun `quickTrackExercises stays null when a seed exercise is missing`() = runTest {
        val exerciseRepo = FakeExerciseRepository(listOf(cardioExercise("ex-run", "Running (Outdoor)")))
        val vm = newViewModel(FakeRoutineRepository(), exerciseRepo = exerciseRepo)

        assertNull(vm.quickTrackExercises.value)
    }

    @Test
    fun `startActivityTracking starts both the workout and the GPS controller`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val clock = FakeClock(currentMillis = 2_000L)
        val controller = ActivityTrackingController(workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), clock, CoroutineScope(UnconfinedTestDispatcher()))
        val vm = newViewModel(FakeRoutineRepository(), clock = clock, workoutRepo = workoutRepo, activityTrackingController = controller)

        val result = vm.startActivityTracking("ex-run", "Running (Outdoor)")

        val started = result as ActivityTrackingStartResult.Started
        assertEquals("Running (Outdoor)", workoutRepo.getById(started.workoutId)!!.title)
        assertTrue(controller.state.value.isTracking)
        assertEquals(started.workoutId, controller.state.value.workoutId)
    }

    @Test
    fun `isGpsSessionAlive reflects the shared GPS controller's state`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val clock = FakeClock()
        val controller = ActivityTrackingController(workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), clock, CoroutineScope(UnconfinedTestDispatcher()))
        val vm = newViewModel(FakeRoutineRepository(), clock = clock, workoutRepo = workoutRepo, activityTrackingController = controller)

        assertFalse(vm.isGpsSessionAlive())
        vm.startActivityTracking("ex-run", "Running (Outdoor)")
        assertTrue(vm.isGpsSessionAlive())
    }

    @Test
    fun `inProgressWorkoutInfo reports the persisted kind, which outlives the controller's state`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val clock = FakeClock()
        val controller = ActivityTrackingController(workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), clock, CoroutineScope(UnconfinedTestDispatcher()))
        val vm = newViewModel(FakeRoutineRepository(), clock = clock, workoutRepo = workoutRepo, activityTrackingController = controller)
        vm.startActivityTracking("ex-run", "Running (Outdoor)")

        // Simulates the process dying: the in-memory session is gone, the Room row is not. Reading
        // the controller alone here used to report "not a GPS run" and send it to the Logger.
        controller.cancelTracking()

        assertFalse(vm.isGpsSessionAlive())
        assertEquals(WorkoutKind.GPS_TRACKED, vm.inProgressWorkoutInfo()?.kind)
    }

    @Test
    fun `discardInProgressAndStartActivityTracking replaces the in-progress workout with a GPS-tracked one`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val clock = FakeClock()
        val controller = ActivityTrackingController(workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), clock, CoroutineScope(UnconfinedTestDispatcher()))
        val vm = newViewModel(FakeRoutineRepository(), clock = clock, workoutRepo = workoutRepo, activityTrackingController = controller)
        val oldId = (vm.startEmptyWorkout() as StartResult.Started).workoutId

        val started = vm.discardInProgressAndStartActivityTracking("ex-walk", "Walking (Outdoor)")

        assertNull(workoutRepo.getById(oldId)) // discarded, not left dangling
        assertEquals("Walking (Outdoor)", workoutRepo.getById(started.workoutId)!!.title)
        assertTrue(controller.state.value.isTracking)
    }

    @Test
    fun `refreshSteps reports today's steps once Health Connect is available and granted`() = runTest {
        val clock = FakeClock()
        val today = java.time.Instant.ofEpochMilli(clock.currentMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val healthMetricsSource = FakeHealthMetricsSource(
            availabilityValue = com.enil.logez.core.wellness.HealthConnectAvailability.Available,
            permissionsGranted = true,
            totals = com.enil.logez.core.wellness.DailyTotals(steps = 4_210L, caloriesBurned = null),
            stepsHistory = listOf(DailyStepCount(today.minusDays(1), 3_200L)),
        )
        val vm = newViewModel(FakeRoutineRepository(), clock = clock, healthMetricsSource = healthMetricsSource)

        assertNull(vm.todaySteps.value) // never shows a stat before it's actually loaded
        vm.refreshSteps()
        assertEquals(4_210L, vm.todaySteps.value)
        assertEquals(today.minusDays(6) to today, healthMetricsSource.queriedStepsRanges.single())
        assertEquals(7, vm.recentSteps.value.size)
        assertEquals(3_200L, vm.recentSteps.value[5].steps)
        assertEquals(4_210L, vm.recentSteps.value.last().steps)
    }

    @Test
    fun `refreshSteps reports null -- not zero -- when Health Connect isn't usable on this device`() = runTest {
        val healthMetricsSource = FakeHealthMetricsSource(
            availabilityValue = com.enil.logez.core.wellness.HealthConnectAvailability.Unavailable,
        )
        val vm = newViewModel(FakeRoutineRepository(), healthMetricsSource = healthMetricsSource)

        vm.refreshSteps()

        assertNull(vm.todaySteps.value)
        assertEquals(emptyList<DailyStepCount>(), vm.recentSteps.value)
    }

    @Test
    fun `discarding from the resume dialog also cancels a live GPS session`() = runTest {
        val workoutRepo = FakeWorkoutRepository()
        val clock = FakeClock()
        val controller = ActivityTrackingController(workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), clock, CoroutineScope(UnconfinedTestDispatcher()))
        val vm = newViewModel(FakeRoutineRepository(), clock = clock, workoutRepo = workoutRepo, activityTrackingController = controller)
        vm.startActivityTracking("ex-run", "Running (Outdoor)")
        assertTrue(vm.isGpsSessionAlive())

        // The path that used to leave the GPS collector and its notification running against a
        // deleted row.
        vm.discardInProgressAndStartEmpty()

        assertFalse(vm.isGpsSessionAlive())
    }
}
