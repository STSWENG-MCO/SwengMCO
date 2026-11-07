package com.enil.logez.feature.workout.finish

import androidx.lifecycle.SavedStateHandle
import com.enil.logez.core.common.PolylineEncoding
import com.enil.logez.core.data.entity.ActivityTrackEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutHeartRateSampleEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.calc.HeartRateZone
import com.enil.logez.core.domain.model.DistanceUnit
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.GpsActivity
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.model.WorkoutStructure
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.wellness.WorkoutHeartRateBackfill
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeActivityTrackRepository
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeWorkoutHeartRateSampleRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Post-save summary stats — the Reps total (same `isIncluded` list as the Sets stat) and the M11
 * circuit fields (structure passthrough plus History's post-purge rounds derivation).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSummaryViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `muscleDiagramVariant reflects whatever the settings repository is seeded with`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(workouts = listOf(workout("w1")), exercises = listOf(workoutExercise("we1", "w1")), sets = listOf(aSet("s1", "we1", 0, reps = 8))),
            settingsRepo = FakeSettingsRepository(UserSettings(muscleDiagramVariant = MuscleDiagramVariant.FEMALE)),
        )
        assertEquals(MuscleDiagramVariant.FEMALE, vm.uiState.value.muscleDiagramVariant)
    }

    @Test
    fun `totalReps sums included sets only — warm-ups excluded, like the Sets stat`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(
                    aSet("s1", "we1", 0, reps = 8),
                    aSet("s2", "we1", 1, reps = 10),
                    aSet("s3", "we1", 2, reps = 12),
                    aSet("s4", "we1", 3, reps = 20, setType = SetType.WARMUP), // excluded by default
                ),
            ),
        )

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(30, state.totalReps) // 8 + 10 + 12, hard-coded — never recomputed
        assertEquals(3, state.completedSetCount)
    }

    @Test
    fun `a GPS-tracked walk with no weight or reps logged hides Volume and Reps and reports distance`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", 0, reps = null, weightKg = null, distanceMeters = 2_000.0)),
            ),
        )

        val state = vm.uiState.value
        assertFalse(state.hasVolume)
        assertFalse(state.hasReps)
        assertEquals(2_000.0, state.totalDistanceMeters, 1e-9)
        // Distance-tracked is asserted via totalDistanceMeters above; hasDistance mirrors it.
        assertEquals(true, state.hasDistance)
    }

    @Test
    fun `a strength set with logged weight and reps shows Volume and Reps, no distance`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", 0, reps = 8)), // aSet defaults weightKg = 50.0
            ),
        )

        val state = vm.uiState.value
        assertEquals(true, state.hasVolume)
        assertEquals(true, state.hasReps)
        assertFalse(state.hasDistance)
    }

    @Test
    fun `a GPS-tracked workout's saved route decodes onto the summary`() = runTest {
        val trackRepo = FakeActivityTrackRepository(
            listOf(
                ActivityTrackEntity(
                    id = "track-1",
                    workoutSetId = "s1",
                    routePolyline = PolylineEncoding.encode(listOf(14.5995 to 120.9842, 14.6 to 120.99)),
                    pointCount = 2,
                    avgAccuracyM = 8.0,
                ),
            ),
        )
        val vm = WorkoutSummaryViewModel(
            savedStateHandle = SavedStateHandle(mapOf(WorkoutSummaryViewModel.WORKOUT_ID_ARG to "w1")),
            workoutRepository = FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", 0, reps = null, weightKg = null, distanceMeters = 500.0)),
            ),
            exerciseRepository = FakeExerciseRepository(listOf(exercise("ex-1"))),
            personalRecordsRepository = FakePersonalRecordsRepository(),
            settingsRepository = FakeSettingsRepository(),
            activityTrackRepository = trackRepo,
            heartRateSampleRepository = FakeWorkoutHeartRateSampleRepository(),
            heartRateBackfill = noBackfill(),
        )

        val points = vm.uiState.value.routePoints
        assertEquals(2, points.size)
        assertEquals(14.5995, points[0].first, 1e-4)
        assertEquals(120.9842, points[0].second, 1e-4)
    }

    @Test
    fun `heart-rate samples WorkoutFinisher already saved surface on the summary, oldest first`() = runTest {
        val heartRateRepo = FakeWorkoutHeartRateSampleRepository(
            listOf(
                WorkoutHeartRateSampleEntity(id = "hr2", workoutId = "w1", recordedAt = 2_000L, bpm = 140L),
                WorkoutHeartRateSampleEntity(id = "hr1", workoutId = "w1", recordedAt = 1_000L, bpm = 120L),
            ),
        )
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", 0, reps = 8)),
            ),
            heartRateRepo = heartRateRepo,
        )

        val samples = vm.uiState.value.heartRateSamples
        assertEquals(2, samples.size)
        assertEquals(1_000L to 120L, samples[0])
        assertEquals(2_000L to 140L, samples[1])
    }

    @Test
    fun `a workout with no saved heart-rate samples shows an empty chart list, not a crash`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", 0, reps = 8)),
            ),
        )

        assertTrue(vm.uiState.value.heartRateSamples.isEmpty())
    }

    @Test
    fun `a workout with no GPS track has an empty route`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", 0, reps = 8)),
            ),
        )

        assertTrue(vm.uiState.value.routePoints.isEmpty())
    }

    @Test
    fun `summary muscle diagram reflects included primary and secondary targets`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout("w1")),
            exercises = listOf(
                workoutExercise("we-chest", "w1", exerciseId = "chest"),
                workoutExercise("we-back", "w1", orderIndex = 1, exerciseId = "back"),
            ),
            sets = listOf(
                aSet("s1", "we-chest", 0, reps = 8),
                aSet("s2", "we-chest", 1, reps = 8),
                aSet("s3", "we-back", 0, reps = 8),
            ),
        )
        val vm = WorkoutSummaryViewModel(
            savedStateHandle = SavedStateHandle(mapOf(WorkoutSummaryViewModel.WORKOUT_ID_ARG to "w1")),
            workoutRepository = workoutRepo,
            exerciseRepository = FakeExerciseRepository(
                listOf(
                    exercise("chest", MuscleGroup.CHEST, listOf(MuscleGroup.TRICEPS)),
                    exercise("back", MuscleGroup.LATS),
                ),
            ),
            personalRecordsRepository = FakePersonalRecordsRepository(),
            settingsRepository = FakeSettingsRepository(),
            activityTrackRepository = FakeActivityTrackRepository(),
            heartRateSampleRepository = FakeWorkoutHeartRateSampleRepository(),
            heartRateBackfill = noBackfill(),
        )

        val intensity = vm.uiState.value.muscleIntensity
        assertEquals(1f, intensity.getValue(MuscleGroup.CHEST), 0.001f)
        assertEquals(0.5f, intensity.getValue(MuscleGroup.LATS), 0.001f)
        assertEquals(0.5f, intensity.getValue(MuscleGroup.TRICEPS), 0.001f)
        val balance = vm.uiState.value.muscleBalance.associateBy { it.region }
        assertEquals(67, balance.getValue(com.enil.logez.core.domain.calc.BodyRegion.CHEST).sharePercent)
        assertEquals(33, balance.getValue(com.enil.logez.core.domain.calc.BodyRegion.BACK).sharePercent)
    }

    @Test
    fun `rep-less sets contribute a real zero, never a fabricated count`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(
                    aSet("s1", "we1", 0, reps = null), // cardio-style set: no reps logged
                    aSet("s2", "we1", 1, reps = 5),
                ),
            ),
        )

        assertEquals(5, vm.uiState.value.totalReps)
        assertEquals(2, vm.uiState.value.completedSetCount)
    }

    @Test
    fun `a circuit with a purged middle round reports the surviving round count, like History`() = runTest {
        // Round 2 was purged (its sets were never completed): each block keeps rows at
        // orderIndex 0 and 2 only, so the post-purge MAX-across-blocks round count is 2.
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1", structure = WorkoutStructure.CIRCUIT)),
                exercises = listOf(
                    workoutExercise("we-squat", "w1", orderIndex = 0),
                    workoutExercise("we-curl", "w1", orderIndex = 1),
                ),
                sets = listOf(
                    aSet("s1", "we-squat", 0, reps = 5),
                    aSet("s2", "we-squat", 2, reps = 5),
                    aSet("s3", "we-curl", 0, reps = 10),
                    aSet("s4", "we-curl", 2, reps = 10),
                ),
            ),
        )

        val state = vm.uiState.value
        assertEquals(WorkoutStructure.CIRCUIT, state.structure)
        assertEquals(2, state.rounds)
    }

    @Test
    fun `a regular workout passes its structure through unchanged`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(
                workouts = listOf(workout("w1")),
                exercises = listOf(workoutExercise("we1", "w1")),
                sets = listOf(aSet("s1", "we1", 0, reps = 8), aSet("s2", "we1", 1, reps = 8)),
            ),
        )

        val state = vm.uiState.value
        assertEquals(WorkoutStructure.REGULAR, state.structure)
        assertEquals(2, state.rounds) // derived either way; only CIRCUIT summaries render it
    }

    // --- walk/run summary (2026-09-26) ---

    @Test
    fun `a GPS run shows no muscle data, even though its exercise lists leg muscles`() = runTest {
        val vm = gpsViewModel(distanceMeters = 4_620.0, durationSeconds = 1_678)
        val state = vm.uiState.value
        assertTrue(state.isGpsTracked)
        assertTrue(state.muscleIntensity.isEmpty())
        assertTrue(state.muscleBalance.isEmpty())
    }

    @Test
    fun `a GPS workout is labelled a run or a walk from its seed exercise`() = runTest {
        assertEquals(GpsActivity.RUN, gpsViewModel(exerciseId = GpsActivity.RUNNING_OUTDOOR_EXERCISE_ID).uiState.value.gpsActivity)
        assertEquals(GpsActivity.WALK, gpsViewModel(exerciseId = GpsActivity.WALKING_OUTDOOR_EXERCISE_ID).uiState.value.gpsActivity)
    }

    @Test
    fun `average pace and speed come from saved distance and time, in the user's unit`() = runTest {
        val km = gpsViewModel(distanceMeters = 4_620.0, durationSeconds = 1_678).uiState.value
        assertEquals(363.2, km.averagePaceSecondsPerUnit!!, 0.1) // 6:03 /km
        assertEquals(9.91, km.averageSpeedPerHour!!, 0.01)

        val miles = gpsViewModel(distanceMeters = 4_620.0, durationSeconds = 1_678, settings = UserSettings(distanceUnit = DistanceUnit.MILES)).uiState.value
        assertEquals(584.5, miles.averagePaceSecondsPerUnit!!, 0.2) // 9:44 /mi
        assertEquals(6.16, miles.averageSpeedPerHour!!, 0.01)
    }

    @Test
    fun `under 50 m there is no honest pace or speed to show`() = runTest {
        val state = gpsViewModel(distanceMeters = 30.0, durationSeconds = 60).uiState.value
        assertNull(state.averagePaceSecondsPerUnit)
        assertNull(state.averageSpeedPerHour)
    }

    @Test
    fun `heart rate is summarised inside the workout's window, with zones when a max is set`() = runTest {
        val start = 1_000_000L
        val samples = listOf(
            WorkoutHeartRateSampleEntity("hr0", "w1", start - 60_000, 190L), // before the run: dropped
            WorkoutHeartRateSampleEntity("hr1", "w1", start, 120L),
            WorkoutHeartRateSampleEntity("hr2", "w1", start + 60_000, 160L),
        )
        val state = gpsViewModel(
            durationSeconds = 120,
            heartRate = samples,
            settings = UserSettings(maxHeartRateBpm = 200),
        ).uiState.value
        assertNotNull(state.heartRateSummary)
        val summary = state.heartRateSummary!!
        assertEquals(160L, summary.maxBpm)
        assertEquals(140L, summary.averageBpm)
        assertEquals(60, summary.zoneSeconds!![HeartRateZone.ZONE_2]) // 120 bpm = 60% of 200
        assertEquals(60, summary.zoneSeconds!![HeartRateZone.ZONE_4]) // 160 bpm = 80%
    }

    @Test
    fun `a GPS workout without a watch has no heart-rate summary`() = runTest {
        assertNull(gpsViewModel().uiState.value.heartRateSummary)
    }

    @Test
    fun `a run with saved route times gets splits and a pace series`() = runTest {
        val degreesPer100m = 100.0 / 111_195.0
        val points = (0..25).map { (14.6 + it * degreesPer100m) to 121.06 } // 2.5 km north
        val times = points.indices.map { it * 30L } // 5:00/km
        val track = ActivityTrackEntity(
            id = "t1", workoutSetId = "s1", routePolyline = PolylineEncoding.encode(points), pointCount = points.size,
            avgAccuracyM = 5.0, routeTimes = PolylineEncoding.encodeDeltas(times),
        )
        val state = gpsViewModel(distanceMeters = 2_500.0, durationSeconds = 750, track = track).uiState.value
        assertEquals(3, state.splits.size)
        assertTrue(state.splits.last().isPartial)
        assertTrue(state.paceSeries.isNotEmpty())
    }

    @Test
    fun `a run tracked before route times were saved still shows its route, just no splits`() = runTest {
        val track = ActivityTrackEntity(
            id = "t1", workoutSetId = "s1", routePolyline = PolylineEncoding.encode(listOf(14.6 to 121.06, 14.61 to 121.06)),
            pointCount = 2, avgAccuracyM = 5.0, routeTimes = null,
        )
        val state = gpsViewModel(distanceMeters = 1_100.0, durationSeconds = 400, track = track).uiState.value
        assertEquals(2, state.routePoints.size)
        assertTrue(state.hasTrack)
        assertTrue(state.splits.isEmpty())
        assertTrue(state.paceSeries.isEmpty())
    }

    @Test
    fun `an interrupted run that kept only its time has no track at all`() = runTest {
        val state = gpsViewModel(distanceMeters = null, durationSeconds = 900, track = null).uiState.value
        assertTrue(state.isGpsTracked)
        assertFalse(state.hasTrack)
        assertFalse(state.hasDistance)
        assertTrue(state.routePoints.isEmpty())
    }

    @Test
    fun `a run where GPS never got a fix has a track but no points`() = runTest {
        val track = ActivityTrackEntity(id = "t1", workoutSetId = "s1", routePolyline = null, pointCount = 0, avgAccuracyM = null)
        val state = gpsViewModel(distanceMeters = 0.0, durationSeconds = 300, track = track).uiState.value
        assertTrue(state.hasTrack)
        assertTrue(state.routePoints.isEmpty())
    }

    @Test
    fun `a strength workout keeps its muscle data and gets none of the walk-run fields`() = runTest {
        val vm = viewModel(
            FakeWorkoutRepository(workouts = listOf(workout("w1")), exercises = listOf(workoutExercise("we1", "w1")), sets = listOf(aSet("s1", "we1", 0, reps = 8))),
        )
        val state = vm.uiState.value
        assertFalse(state.isGpsTracked)
        assertTrue(state.muscleIntensity.isNotEmpty())
        assertNull(state.heartRateSummary)
        assertNull(state.averagePaceSecondsPerUnit)
    }

    @Test
    fun `heart rate that syncs after Save appears when the summary comes back to the foreground`() = runTest {
        val start = 1_000_000L
        val health = FakeHealthMetricsSource(
            availabilityValue = com.enil.logez.core.wellness.HealthConnectAvailability.Available,
            permissionsGranted = true,
        )
        val vm = gpsViewModel(durationSeconds = 600, healthSource = health)
        assertNull(vm.uiState.value.heartRateSummary) // nothing had synced at Save

        // The watch syncs later; the user returns to the summary.
        health.heartRateSamples = listOf(
            com.enil.logez.core.wellness.HeartRateSample(java.time.Instant.ofEpochMilli(start + 60_000), 130L),
            com.enil.logez.core.wellness.HeartRateSample(java.time.Instant.ofEpochMilli(start + 120_000), 150L),
        )
        vm.refreshHeartRate()

        assertEquals(150L, vm.uiState.value.heartRateSummary!!.maxBpm)
    }

    // --- fixture ---

    /** A backfill that never finds anything new: Health Connect unavailable. */
    private fun noBackfill(repo: FakeWorkoutHeartRateSampleRepository = FakeWorkoutHeartRateSampleRepository()) =
        WorkoutHeartRateBackfill(FakeHealthMetricsSource(), repo, AppLogger.NoOp)

    /** A realistic tracked run: GPS_TRACKED kind, the DISTANCE_DURATION seed exercise with its leg secondaries. */
    private fun gpsViewModel(
        exerciseId: String = GpsActivity.RUNNING_OUTDOOR_EXERCISE_ID,
        distanceMeters: Double? = 1_000.0,
        durationSeconds: Int = 360,
        track: ActivityTrackEntity? = null,
        heartRate: List<WorkoutHeartRateSampleEntity> = emptyList(),
        settings: UserSettings = UserSettings(),
        healthSource: FakeHealthMetricsSource = FakeHealthMetricsSource(),
        sampleRepo: FakeWorkoutHeartRateSampleRepository = FakeWorkoutHeartRateSampleRepository(heartRate),
    ): WorkoutSummaryViewModel {
        val start = 1_000_000L
        val run = Exercise(
            id = exerciseId, name = "Running (Outdoor)", exerciseType = ExerciseType.DISTANCE_DURATION,
            primaryMuscleGroup = MuscleGroup.CARDIO,
            secondaryMuscleGroups = listOf(MuscleGroup.QUADRICEPS, MuscleGroup.HAMSTRINGS, MuscleGroup.CALVES),
            equipment = Equipment.NONE, instructions = "", mediaPath = null, isCustom = false,
            isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
        )
        val workout = workout("w1", kind = WorkoutKind.GPS_TRACKED, startedAt = start, durationSeconds = durationSeconds)
        return WorkoutSummaryViewModel(
            savedStateHandle = SavedStateHandle(mapOf(WorkoutSummaryViewModel.WORKOUT_ID_ARG to "w1")),
            workoutRepository = FakeWorkoutRepository(
                workouts = listOf(workout),
                exercises = listOf(workoutExercise("we1", "w1", exerciseId = exerciseId)),
                sets = listOf(aSet("s1", "we1", 0, reps = null, weightKg = null, distanceMeters = distanceMeters)),
            ),
            exerciseRepository = FakeExerciseRepository(listOf(run)),
            personalRecordsRepository = FakePersonalRecordsRepository(),
            settingsRepository = FakeSettingsRepository(settings),
            activityTrackRepository = FakeActivityTrackRepository(listOfNotNull(track)),
            heartRateSampleRepository = sampleRepo,
            heartRateBackfill = WorkoutHeartRateBackfill(healthSource, sampleRepo, AppLogger.NoOp),
        )
    }

    private fun viewModel(
        workoutRepo: FakeWorkoutRepository,
        heartRateRepo: FakeWorkoutHeartRateSampleRepository = FakeWorkoutHeartRateSampleRepository(),
        settingsRepo: FakeSettingsRepository = FakeSettingsRepository(),
    ) = WorkoutSummaryViewModel(
        savedStateHandle = SavedStateHandle(mapOf(WorkoutSummaryViewModel.WORKOUT_ID_ARG to "w1")),
        workoutRepository = workoutRepo,
        exerciseRepository = FakeExerciseRepository(listOf(exercise("ex-1"))),
        personalRecordsRepository = FakePersonalRecordsRepository(),
        settingsRepository = settingsRepo,
        activityTrackRepository = FakeActivityTrackRepository(),
        heartRateSampleRepository = heartRateRepo,
        heartRateBackfill = noBackfill(heartRateRepo),
    )

    private fun workout(
        id: String,
        structure: WorkoutStructure = WorkoutStructure.REGULAR,
        kind: WorkoutKind = WorkoutKind.STRENGTH,
        startedAt: Long = 1_000L,
        durationSeconds: Int = 1,
    ) = WorkoutEntity(
        id = id, routineId = null, title = "Session $id", notes = null, status = WorkoutStatus.COMPLETED,
        startedAt = startedAt, endedAt = startedAt + durationSeconds * 1000L, durationSeconds = durationSeconds,
        createdAt = 1_000L, updatedAt = 1_000L, structure = structure, kind = kind,
    )

    private fun workoutExercise(id: String, workoutId: String, orderIndex: Int = 0, exerciseId: String = "ex-1") = WorkoutExerciseEntity(
        id = id, workoutId = workoutId, exerciseId = exerciseId, orderIndex = orderIndex, supersetGroup = null, restTimerSeconds = null, notes = null,
    )

    private fun aSet(
        id: String,
        workoutExerciseId: String,
        orderIndex: Int,
        reps: Int?,
        setType: SetType = SetType.NORMAL,
        weightKg: Double? = 50.0,
        distanceMeters: Double? = null,
    ) = WorkoutSetEntity(
        id = id, workoutExerciseId = workoutExerciseId, orderIndex = orderIndex, setType = setType,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = distanceMeters, rpe = null,
        customMetric = null, isCompleted = true, completedAt = 1L,
    )

    private fun exercise(
        id: String,
        primary: MuscleGroup = MuscleGroup.CHEST,
        secondary: List<MuscleGroup> = emptyList(),
    ) = Exercise(
        id = id, name = "Ex $id", exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = primary,
        secondaryMuscleGroups = secondary, equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )
}
