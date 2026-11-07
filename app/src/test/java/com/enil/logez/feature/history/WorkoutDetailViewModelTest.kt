package com.enil.logez.feature.history

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.common.PolylineEncoding
import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeActiveSessionRepository
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.feature.workout.SessionDiscarder
import com.enil.logez.feature.workout.InProgressWorkoutResolver
import com.enil.logez.feature.activity.ActivityTrackingController
import com.enil.logez.fakes.FakeLocationSource
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeElapsedRealtimeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeTransactionRunner
import com.enil.logez.fakes.FakeWorkoutRepository
import com.enil.logez.feature.workout.WorkoutStarter
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * First test coverage for this ViewModel (adversarial review, 2026-09-10 flagged its total
 * absence as a high-severity gap once hasVolume/hasDistance/hasRoute logic landed here with
 * nothing exercising it). Scoped to `reload()`'s stat-gating and route-presence logic -- the
 * screen's copy/save/delete actions already have their own dedicated collaborator classes and
 * aren't re-tested here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutDetailViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a GPS-tracked walk with no weight logged hides Volume and reports its distance`() = runTest {
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", weightKg = null, reps = null, distanceMeters = 2_000.0)),
            ),
        )

        val state = vm.uiState.value
        assertFalse(state.hasVolume)
        assertTrue(state.hasDistance)
        assertEquals(2_000.0, state.distanceMeters, 1e-9)
    }

    @Test
    fun `a strength workout with logged weight shows Volume and has no distance`() = runTest {
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", weightKg = 100.0, reps = 5)),
            ),
        )

        val state = vm.uiState.value
        assertTrue(state.hasVolume)
        assertFalse(state.hasDistance)
    }

    @Test
    fun `hasRoute is true and routePoints is decoded when the workout's set has a saved GPS track`() = runTest {
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", weightKg = null, reps = null, distanceMeters = 500.0)),
            ),
            trackRepo = FakeActivityTrackRepository(listOf(track("s1"))),
        )

        val state = vm.uiState.value
        assertTrue(state.hasRoute)
        assertEquals(2, state.routePoints.size)
        assertEquals(14.5995, state.routePoints[0].first, 1e-4)
        assertEquals(120.9842, state.routePoints[0].second, 1e-4)
    }

    @Test
    fun `a track with no recorded point shows no Route card, rather than an empty map`() = runTest {
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", weightKg = null, reps = null, distanceMeters = 0.0)),
            ),
            trackRepo = FakeActivityTrackRepository(
                listOf(com.enil.logez.core.data.entity.ActivityTrackEntity("t-empty", "s1", routePolyline = null, pointCount = 0, avgAccuracyM = null)),
            ),
        )
        assertFalse(vm.uiState.value.hasRoute)
    }

    @Test
    fun `hasRoute stays true even when the tracked set is excluded from stats as a warm-up`() = runTest {
        // Regression (adversarial review, 2026-09-10): hasRoute deliberately scans the unfiltered
        // set list, not the isIncluded-filtered one hasVolume/hasDistance use -- a recorded GPS
        // track is a fact about what happened, not a stats-inclusion choice. Re-tagging the
        // tracked set as a warm-up (with warm-ups excluded from stats) must hide the Distance
        // stat, but must NOT also hide the Route card -- the two are intentionally independent.
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(
                    aSet("s1", "we1", weightKg = null, reps = null, distanceMeters = 500.0, setType = SetType.WARMUP),
                ),
            ),
            trackRepo = FakeActivityTrackRepository(listOf(track("s1"))),
            settingsRepo = FakeSettingsRepository(UserSettings(includeWarmupsInStats = false)),
        )

        val state = vm.uiState.value
        assertFalse("the warm-up set is excluded from stats", state.hasDistance)
        assertTrue("the route it recorded must still show", state.hasRoute)
    }

    @Test
    fun `hasRoute is false for an ordinary strength workout with no GPS track`() = runTest {
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", weightKg = 100.0, reps = 5)),
            ),
        )

        assertFalse(vm.uiState.value.hasRoute)
    }

    @Test
    fun `exercise lookup is batched once per distinct exercise, not once per set or per block`() = runTest {
        // 2 exercise blocks, 3 sets each, sharing between them only 2 DISTINCT exercises -- a
        // naive per-block-and-per-set lookup would call getById 2 (blocks) + 6 (sets) = 8 times;
        // batched correctly, it's called exactly twice (see WorkoutDetailViewModel.reload()).
        val exerciseRepo = FakeExerciseRepository(listOf(exercise("ex-1", "Bench Press"), exercise("ex-2", "Squat")))
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(
                    workoutExercise("we1", "w1", exerciseId = "ex-1", orderIndex = 0),
                    workoutExercise("we2", "w1", exerciseId = "ex-2", orderIndex = 1),
                ),
                sets = listOf(
                    aSet("s1", "we1", weightKg = 100.0, reps = 5), aSet("s2", "we1", weightKg = 100.0, reps = 5), aSet("s3", "we1", weightKg = 100.0, reps = 5),
                    aSet("s4", "we2", weightKg = 60.0, reps = 8), aSet("s5", "we2", weightKg = 60.0, reps = 8), aSet("s6", "we2", weightKg = 60.0, reps = 8),
                ),
            ),
            exerciseRepo = exerciseRepo,
        )

        // Volume actually computed (exercise resolves this time, unlike the other tests' empty repo):
        // 3 sets of 100kg x 5 (500 each) + 3 sets of 60kg x 8 (480 each) = 1500 + 1440.
        assertEquals(2_940.0, vm.uiState.value.volumeKg, 1e-9)
        assertEquals(2, exerciseRepo.getByIdCallCount)
    }

    @Test
    fun `a workout that no longer exists reports isMissing instead of an error`() = runTest {
        val vm = viewModel(workoutRepo = FakeWorkoutRepository())

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.isMissing)
    }

    // --- fixture ---

    private fun viewModel(
        workoutRepo: FakeWorkoutRepository,
        trackRepo: FakeActivityTrackRepository = FakeActivityTrackRepository(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(listOf()),
        sampleRepo: com.enil.logez.fakes.FakeWorkoutHeartRateSampleRepository = com.enil.logez.fakes.FakeWorkoutHeartRateSampleRepository(),
        healthSource: com.enil.logez.fakes.FakeHealthMetricsSource = com.enil.logez.fakes.FakeHealthMetricsSource(),
    ): WorkoutDetailViewModel {
        val personalRecordsRepo = FakePersonalRecordsRepository()
        val personalRecordsUpdater = PersonalRecordsUpdater(
            workoutRepo, exerciseRepo, personalRecordsRepo, FakeMeasurementRepository(), settingsRepo,
        )
        return WorkoutDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf(WorkoutDetailViewModel.WORKOUT_ID_ARG to "w1")),
            workoutRepository = workoutRepo,
            exerciseRepository = exerciseRepo,
            routineRepository = FakeRoutineRepository(),
            personalRecordsRepository = personalRecordsRepo,
            settingsRepository = settingsRepo,
            workoutDeleter = WorkoutDeleter(workoutRepo, personalRecordsUpdater, FakeTransactionRunner()),
            workoutToRoutineConverter = WorkoutToRoutineConverter(workoutRepo, FakeRoutineRepository(), FakeClock()),
            workoutStarter = WorkoutStarter(workoutRepo, FakeRoutineRepository(), FakeClock()),
            sessionController = WorkoutSessionController(
                FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(dispatcher),
            ),
            activityTrackRepository = trackRepo,
            sessionDiscarder = SessionDiscarder(
                WorkoutStarter(workoutRepo, FakeRoutineRepository(), FakeClock()),
                WorkoutSessionController(FakeActiveSessionRepository(), FakeClock(), FakeElapsedRealtimeClock(), CoroutineScope(dispatcher)),
                ActivityTrackingController(workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), FakeClock(), CoroutineScope(dispatcher)),
            ),
            inProgressWorkoutResolver = InProgressWorkoutResolver(
                workoutRepo,
                ActivityTrackingController(
                    workoutRepo, FakeActivityTrackRepository(), FakeLocationSource(), FakeClock(),
                    CoroutineScope(dispatcher),
                ),
            ),
            heartRateSampleRepository = sampleRepo,
            heartRateBackfill = com.enil.logez.core.wellness.WorkoutHeartRateBackfill(healthSource, sampleRepo, com.enil.logez.core.common.AppLogger.NoOp),
        )
    }

    @Test
    fun `saved heart rate shows as a summary on the workout detail`() = runTest {
        val sampleRepo = com.enil.logez.fakes.FakeWorkoutHeartRateSampleRepository(
            listOf(
                com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity("hr1", "w1", 1_000L, 120L),
                com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity("hr2", "w1", 1_500L, 140L),
            ),
        )
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(workouts = listOf(workout("w1")), exercises = listOf(workoutExercise("we1", "w1")), sets = listOf(aSet("s1", "we1", weightKg = 50.0, reps = 5))),
            sampleRepo = sampleRepo,
        )
        assertEquals(140L, vm.uiState.value.heartRateSummary!!.maxBpm)
    }

    @Test
    fun `opening the workout again adds heart rate the watch synced after Save`() = runTest {
        val sampleRepo = com.enil.logez.fakes.FakeWorkoutHeartRateSampleRepository()
        val health = com.enil.logez.fakes.FakeHealthMetricsSource(
            availabilityValue = com.enil.logez.core.wellness.HealthConnectAvailability.Available,
            permissionsGranted = true,
            heartRateSamples = listOf(com.enil.logez.core.wellness.HeartRateSample(java.time.Instant.ofEpochMilli(1_500L), 133L)),
        )
        val vm = viewModel(
            workoutRepo = FakeWorkoutRepository(workouts = listOf(workout("w1")), exercises = listOf(workoutExercise("we1", "w1")), sets = listOf(aSet("s1", "we1", weightKg = 50.0, reps = 5))),
            sampleRepo = sampleRepo,
            healthSource = health,
        )
        assertEquals(null, vm.uiState.value.heartRateSummary)

        vm.refresh() // the screen's ON_RESUME

        assertEquals(1, sampleRepo.all.size)
        assertEquals(133L, vm.uiState.value.heartRateSummary!!.maxBpm)
    }

    private fun workout(id: String) = WorkoutEntity(
        id = id, routineId = null, title = "Session $id", notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = 1_000L, endedAt = 2_000L, durationSeconds = 60, createdAt = 1_000L, updatedAt = 1_000L,
    )

    private fun workoutExercise(id: String, workoutId: String, exerciseId: String = "ex-1", orderIndex: Int = 0) = WorkoutExerciseEntity(
        id = id, workoutId = workoutId, exerciseId = exerciseId, orderIndex = orderIndex, supersetGroup = null, restTimerSeconds = null, notes = null,
    )

    private fun exercise(id: String, name: String) = Exercise(
        id = id, name = name, exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )

    private fun aSet(
        id: String,
        workoutExerciseId: String,
        weightKg: Double?,
        reps: Int?,
        distanceMeters: Double? = null,
        setType: SetType = SetType.NORMAL,
    ) = WorkoutSetEntity(
        id = id, workoutExerciseId = workoutExerciseId, orderIndex = 0, setType = setType,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = distanceMeters, rpe = null,
        customMetric = null, isCompleted = true, completedAt = 1L,
    )

    private fun track(workoutSetId: String) = ActivityTrackEntity(
        id = "track-$workoutSetId", workoutSetId = workoutSetId,
        routePolyline = PolylineEncoding.encode(listOf(14.5995 to 120.9842, 14.5985 to 120.9842)),
        pointCount = 2, avgAccuracyM = 5.0,
    )
}
