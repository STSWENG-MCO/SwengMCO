package com.enil.logez.feature.workout.finish

import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineExerciseEntity
import com.enil.logez.core.data.entity.RoutineSetEntity
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.fakes.FakeClock
import com.enil.logez.fakes.FakeExerciseRepository
import com.enil.logez.fakes.FakeHealthMetricsSource
import com.enil.logez.fakes.FakeMeasurementRepository
import com.enil.logez.fakes.FakePersonalRecordsRepository
import com.enil.logez.fakes.FakeRoutineRepository
import com.enil.logez.fakes.FakeSettingsRepository
import com.enil.logez.fakes.FakeTransactionRunner
import com.enil.logez.fakes.FakeWorkoutHeartRateSampleRepository
import com.enil.logez.fakes.FakeWorkoutRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regressions from the M4c adversarial review. Each test is the reviewer's own reproduction,
 * pinned so the defect cannot come back: every one of these was a silent data corruption — the
 * save reported success while writing something the user never logged.
 */
class WorkoutFinisherRegressionTest {
    private val startedAt = 10_000L
    private val now = 100_000L

    @Test
    fun `a routine using one exercise twice does not overwrite the first block with the second`() = runTest {
        // The reviewer's repro: "Push" = Bench 3 sets, Overhead 3 sets, Bench-burnout 1 set.
        // Keying logged sets by exerciseId fused both bench blocks into one list whose
        // orderIndexes collided, so the burnout's 40x20 landed on the main lift's set 1.
        val routineRepo = FakeRoutineRepository(
            routines = listOf(routine()),
            exercises = listOf(
                routineExercise("re-bench", "ex-bench", 0),
                routineExercise("re-ohp", "ex-ohp", 1),
                routineExercise("re-burnout", "ex-bench", 2),
            ),
            sets = listOf(
                routineSet("rs-b0", "re-bench", 0, 95.0, 5),
                routineSet("rs-b1", "re-bench", 1, 95.0, 5),
                routineSet("rs-b2", "re-bench", 2, 95.0, 5),
                routineSet("rs-o0", "re-ohp", 0, 40.0, 10),
                routineSet("rs-burn0", "re-burnout", 0, 45.0, 15),
            ),
        )
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout(routineId = "r1")),
            exercises = listOf(
                workoutExercise("we-bench", "ex-bench", 0),
                workoutExercise("we-ohp", "ex-ohp", 1),
                workoutExercise("we-burnout", "ex-bench", 2),
            ),
            sets = listOf(
                set("s-b0", "we-bench", 0, 100.0, 5),
                set("s-b1", "we-bench", 1, 100.0, 5),
                set("s-b2", "we-bench", 2, 100.0, 5),
                set("s-o0", "we-ohp", 0, 42.5, 10),
                set("s-burn0", "we-burnout", 0, 40.0, 20),
            ),
        )

        finisher(workoutRepo, routineRepo).finish(
            workout = workoutRepo.getById("w1")!!, title = "Push", notes = null, startedAt = startedAt,
            durationSeconds = 3600, updateRoutineValues = true, structureChoice = null,
        )

        val mainBench = routineRepo.getSetsForRoutineExercise("re-bench").sortedBy { it.orderIndex }
        assertEquals(listOf(100.0, 100.0, 100.0), mainBench.map { it.targetWeightKg })
        assertEquals(listOf(5, 5, 5), mainBench.map { it.targetReps })
        // The burnout block still takes its own values.
        val burnout = routineRepo.getSetsForRoutineExercise("re-burnout").single()
        assertEquals(40.0, burnout.targetWeightKg!!, 1e-9)
        assertEquals(20, burnout.targetReps)
    }

    @Test
    fun `skipping an entire duplicate block does not shift a later block's values onto it`() = runTest {
        // Second-round regression: fix 1 above re-derived "the Nth block of this exercise" from
        // what SURVIVES the uncompleted-set purge. Skip the main bench block entirely (all sets
        // stay unchecked, so finishWorkout's purge deletes it), and the burnout block — now the
        // only surviving bench block — used to inherit the main lift's slot at nth=0, overwriting
        // 95kg x 5 with the burnout's 40kg x 20.
        val routineRepo = FakeRoutineRepository(
            routines = listOf(routine()),
            exercises = listOf(
                routineExercise("re-bench", "ex-bench", 0),
                routineExercise("re-ohp", "ex-ohp", 1),
                routineExercise("re-burnout", "ex-bench", 2),
            ),
            sets = listOf(
                routineSet("rs-b0", "re-bench", 0, 95.0, 5),
                routineSet("rs-o0", "re-ohp", 0, 40.0, 10),
                routineSet("rs-burn0", "re-burnout", 0, 45.0, 15),
            ),
        )
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout(routineId = "r1")),
            exercises = listOf(
                workoutExercise("we-bench", "ex-bench", 0),
                workoutExercise("we-ohp", "ex-ohp", 1),
                workoutExercise("we-burnout", "ex-bench", 2),
            ),
            sets = listOf(
                set("s-b0", "we-bench", 0, 95.0, 5, isCompleted = false), // the whole main block skipped
                set("s-o0", "we-ohp", 0, 42.5, 10),
                set("s-burn0", "we-burnout", 0, 40.0, 20),
            ),
        )

        finisher(workoutRepo, routineRepo).finish(
            workout = workoutRepo.getById("w1")!!, title = "Push", notes = null, startedAt = startedAt,
            durationSeconds = 3600, updateRoutineValues = true, structureChoice = RoutineStructureChoice.KEEP_ORIGINAL,
        )

        // The main lift's authored target must survive untouched — nothing was logged against it.
        val mainBench = routineRepo.getSetsForRoutineExercise("re-bench").single()
        assertEquals(95.0, mainBench.targetWeightKg!!, 1e-9)
        assertEquals(5, mainBench.targetReps)
        // The burnout's own values land on the burnout slot, not the main lift's.
        val burnout = routineRepo.getSetsForRoutineExercise("re-burnout").single()
        assertEquals(40.0, burnout.targetWeightKg!!, 1e-9)
        assertEquals(20, burnout.targetReps)
    }

    @Test
    fun `a structural rewrite of a skipped duplicate block keeps the surviving block's own note and rep range`() = runTest {
        // Same root cause as above, in rewriteRoutineStructure: the surviving burnout block used
        // to be paired against the main lift's routine slot (its note and rep range), erasing the
        // burnout's own note and imposing a rep range it never had.
        val routineRepo = FakeRoutineRepository(
            routines = listOf(routine()),
            exercises = listOf(
                routineExercise("re-bench", "ex-bench", 0, notes = "main lift: pause at chest"),
                routineExercise("re-burnout", "ex-bench", 1, notes = "burnout: no pause"),
            ),
            sets = listOf(
                routineSet("rs-b0", "re-bench", 0, 95.0, targetReps = null, rangeMin = 3, rangeMax = 5),
                routineSet("rs-burn0", "re-burnout", 0, 45.0, 15),
            ),
        )
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout(routineId = "r1")),
            exercises = listOf(
                workoutExercise("we-bench", "ex-bench", 0),
                workoutExercise("we-burnout", "ex-bench", 1),
            ),
            sets = listOf(
                set("s-b0", "we-bench", 0, 95.0, 5, isCompleted = false), // main block skipped entirely
                set("s-burn0", "we-burnout", 0, 40.0, 20),
                set("s-burn1", "we-burnout", 1, 40.0, 18),
            ),
        )

        finisher(workoutRepo, routineRepo).finish(
            workout = workoutRepo.getById("w1")!!, title = "Push", notes = null, startedAt = startedAt,
            durationSeconds = 3600, updateRoutineValues = false, structureChoice = RoutineStructureChoice.UPDATE_ROUTINE,
        )

        val survivor = routineRepo.getExercisesForRoutine("r1").single()
        assertEquals("burnout: no pause", survivor.notes)
        val survivorSet = routineRepo.getSetsForRoutineExercise(survivor.id).first()
        assertEquals(40.0, survivorSet.targetWeightKg!!, 1e-9)
        assertEquals(20, survivorSet.targetReps)
        assertNull("the burnout block never had a rep range — it must not inherit the main lift's", survivorSet.targetRepRangeMin)
    }

    @Test
    fun `a structural rewrite keeps rep ranges on the exercises it did not change`() = runTest {
        // Squat carries an authored 8-12 range; the session only adds a Leg Press set. The rewrite
        // re-mints every row, and used to hard-code the range columns to null — erasing Squat's.
        val routineRepo = FakeRoutineRepository(
            routines = listOf(routine()),
            exercises = listOf(routineExercise("re-squat", "ex-squat", 0), routineExercise("re-press", "ex-press", 1)),
            sets = listOf(
                routineSet("rs-s0", "re-squat", 0, 80.0, targetReps = null, rangeMin = 8, rangeMax = 12),
                routineSet("rs-p0", "re-press", 0, 120.0, 10),
            ),
        )
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout(routineId = "r1")),
            exercises = listOf(workoutExercise("we-squat", "ex-squat", 0), workoutExercise("we-press", "ex-press", 1)),
            sets = listOf(
                set("s-s0", "we-squat", 0, 85.0, 10),
                set("s-p0", "we-press", 0, 125.0, 10),
                set("s-p1", "we-press", 1, 125.0, 8), // the structural change
            ),
        )

        finisher(workoutRepo, routineRepo).finish(
            workout = workoutRepo.getById("w1")!!, title = "Legs", notes = null, startedAt = startedAt,
            durationSeconds = 3600, updateRoutineValues = false,
            structureChoice = RoutineStructureChoice.UPDATE_ROUTINE,
        )

        val squat = routineRepo.getExercisesForRoutine("r1").single { it.exerciseId == "ex-squat" }
        val squatSet = routineRepo.getSetsForRoutineExercise(squat.id).single()
        assertEquals(8, squatSet.targetRepRangeMin)
        assertEquals(12, squatSet.targetRepRangeMax)
        assertNull("a ranged target must not collapse to an exact rep count", squatSet.targetReps)
        assertEquals(85.0, squatSet.targetWeightKg!!, 1e-9) // weight still adopts the session's

        // The genuinely changed exercise does pick up its new set.
        val press = routineRepo.getExercisesForRoutine("r1").single { it.exerciseId == "ex-press" }
        assertEquals(2, routineRepo.getSetsForRoutineExercise(press.id).size)
    }

    @Test
    fun `a structural rewrite does not leave a superset partner alone in its group`() = runTest {
        // Bench and Row are supersetted; Row is never completed, so the purge deletes it and the
        // rewrite would have written group 1 with exactly one member — a state the rest of the app
        // actively cleans up.
        val routineRepo = FakeRoutineRepository(
            routines = listOf(routine()),
            exercises = listOf(
                routineExercise("re-bench", "ex-bench", 0, supersetGroup = 1),
                routineExercise("re-row", "ex-row", 1, supersetGroup = 1),
            ),
            sets = listOf(routineSet("rs-b0", "re-bench", 0, 95.0, 5), routineSet("rs-r0", "re-row", 0, 70.0, 10)),
        )
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout(routineId = "r1")),
            exercises = listOf(
                workoutExercise("we-bench", "ex-bench", 0, supersetGroup = 1),
                workoutExercise("we-row", "ex-row", 1, supersetGroup = 1),
            ),
            sets = listOf(
                set("s-b0", "we-bench", 0, 100.0, 5),
                set("s-r0", "we-row", 0, 70.0, 10, isCompleted = false),
            ),
        )

        finisher(workoutRepo, routineRepo).finish(
            workout = workoutRepo.getById("w1")!!, title = "Pull", notes = null, startedAt = startedAt,
            durationSeconds = 3600, updateRoutineValues = false,
            structureChoice = RoutineStructureChoice.UPDATE_ROUTINE,
        )

        val survivors = routineRepo.getExercisesForRoutine("r1")
        assertEquals(1, survivors.size)
        assertNull("a one-member superset group is not a valid routine state", survivors.single().supersetGroup)
    }

    @Test
    fun `a structural rewrite keeps the routine's coaching note instead of the session note`() = runTest {
        val routineRepo = FakeRoutineRepository(
            routines = listOf(routine()),
            exercises = listOf(routineExercise("re-bench", "ex-bench", 0, notes = "pause 1s at chest")),
            sets = listOf(routineSet("rs-b0", "re-bench", 0, 95.0, 5)),
        )
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout(routineId = "r1")),
            exercises = listOf(workoutExercise("we-bench", "ex-bench", 0, notes = "felt weak today, shoulder tight")),
            sets = listOf(set("s-b0", "we-bench", 0, 100.0, 5), set("s-b1", "we-bench", 1, 100.0, 5)),
        )

        finisher(workoutRepo, routineRepo).finish(
            workout = workoutRepo.getById("w1")!!, title = "Push", notes = null, startedAt = startedAt,
            durationSeconds = 3600, updateRoutineValues = false,
            structureChoice = RoutineStructureChoice.UPDATE_ROUTINE,
        )

        assertEquals("pause 1s at chest", routineRepo.getExercisesForRoutine("r1").single().notes)
    }

    @Test
    fun `the whole save runs inside a single transaction`() = runTest {
        // Not a style point: the workout is COMPLETED by step 2, and once it is, the finish screen
        // is unreachable — so a routine update or PR rebuild lost after that can never be retried.
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout(routineId = null)),
            exercises = listOf(workoutExercise("we-bench", "ex-bench", 0)),
            sets = listOf(set("s-b0", "we-bench", 0, 100.0, 5)),
        )
        val runner = FakeTransactionRunner()

        finisher(workoutRepo, FakeRoutineRepository(), runner = runner).finish(
            workout = workoutRepo.getById("w1")!!, title = "Push", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = false, structureChoice = null,
        )

        assertEquals(1, runner.transactionCount)
    }

    @Test
    fun `historical PRs use the bodyweight of their own workout, not today's`() = runTest {
        // Weighted pull-ups: 10kg x 8 at 70kg bodyweight is 640 volume. Months later the user is
        // 80kg. Rebuilding with one current weight recomputed that January set as (80+10)x8 = 720
        // and stored a number the user never achieved.
        val janStart = isoMillis("2026-01-05")
        val augStart = isoMillis("2026-08-24")
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(
                workout(id = "w-jan", startedAtMillis = janStart, completed = true),
                workout(id = "w1", startedAtMillis = augStart),
            ),
            exercises = listOf(
                workoutExercise("we-jan", "ex-pullup", 0, workoutId = "w-jan"),
                workoutExercise("we-aug", "ex-pullup", 0, workoutId = "w1"),
            ),
            sets = listOf(
                set("s-jan", "we-jan", 0, 10.0, 8),
                set("s-aug", "we-aug", 0, 20.0, 6),
            ),
        )
        val measurements = FakeMeasurementRepository(
            weightsByDate = mapOf("2026-01-01" to 70.0, "2026-08-20" to 80.0),
        )
        val recordsRepo = FakePersonalRecordsRepository()

        finisher(
            workoutRepo,
            FakeRoutineRepository(),
            exerciseRepo = FakeExerciseRepository(listOf(pullUp())),
            measurements = measurements,
            recordsRepo = recordsRepo,
            // Must be after the session, or §5.1.8's future-date clamp rewrites startedAt to "now"
            // and the dates under test stop being the dates the save actually sees.
            nowMillis = augStart + 3_600_000L,
        ).finish(
            workout = workoutRepo.getById("w1")!!, title = "Pull", notes = null, startedAt = augStart,
            durationSeconds = 60, updateRoutineValues = false, structureChoice = null,
        )

        val best = recordsRepo.getForExercise("ex-pullup").single { it.prType.name == "BEST_SET_VOLUME" }
        assertEquals(640.0, best.value, 1e-9) // (70 + 10) x 8, the weight in effect that January
        assertEquals("w-jan", best.workoutId)
        // And it genuinely asked about both dates rather than resolving once.
        assertEquals(setOf("2026-01-05", "2026-08-24"), measurements.queriedDates.toSet())
    }

    @Test
    fun `a workout with no measurements at all still saves`() = runTest {
        val workoutRepo = FakeWorkoutRepository(
            workouts = listOf(workout(routineId = null)),
            exercises = listOf(workoutExercise("we-p", "ex-pullup", 0)),
            sets = listOf(set("s-p", "we-p", 0, 10.0, 8)),
        )
        val recordsRepo = FakePersonalRecordsRepository()

        finisher(
            workoutRepo,
            FakeRoutineRepository(),
            exerciseRepo = FakeExerciseRepository(listOf(pullUp())),
            measurements = FakeMeasurementRepository(),
            recordsRepo = recordsRepo,
        ).finish(
            workout = workoutRepo.getById("w1")!!, title = "Pull", notes = null, startedAt = startedAt,
            durationSeconds = 60, updateRoutineValues = false, structureChoice = null,
        )

        // Bodyweight unknown -> BODYWEIGHT_WEIGHTED falls back to the added load alone (§8.3).
        assertNotNull(recordsRepo.getForExercise("ex-pullup").firstOrNull { it.prType.name == "HEAVIEST_WEIGHT" })
    }

    // --- fixture ---

    private fun finisher(
        workoutRepo: FakeWorkoutRepository,
        routineRepo: FakeRoutineRepository,
        runner: FakeTransactionRunner = FakeTransactionRunner(),
        exerciseRepo: FakeExerciseRepository = FakeExerciseRepository(
            listOf(exercise("ex-bench"), exercise("ex-ohp"), exercise("ex-squat"), exercise("ex-press"), exercise("ex-row")),
        ),
        measurements: FakeMeasurementRepository = FakeMeasurementRepository(),
        recordsRepo: FakePersonalRecordsRepository = FakePersonalRecordsRepository(),
        nowMillis: Long = now,
    ): WorkoutFinisher {
        val updater = PersonalRecordsUpdater(
            workoutRepo, exerciseRepo, recordsRepo, measurements, FakeSettingsRepository(),
        )
        return WorkoutFinisher(
            workoutRepo, routineRepo, updater, runner,
            FakeHealthMetricsSource(), FakeWorkoutHeartRateSampleRepository(), FakeClock(currentMillis = nowMillis),
        )
    }

    private fun isoMillis(date: String): Long =
        java.time.LocalDate.parse(date).atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun routine() = RoutineEntity(
        id = "r1", folderId = null, name = "Push", notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0,
    )

    private fun routineExercise(
        id: String,
        exerciseId: String,
        orderIndex: Int,
        supersetGroup: Int? = null,
        notes: String? = null,
    ) = RoutineExerciseEntity(
        id = id, routineId = "r1", exerciseId = exerciseId, orderIndex = orderIndex,
        supersetGroup = supersetGroup, restTimerSeconds = null, notes = notes,
    )

    private fun routineSet(
        id: String,
        routineExerciseId: String,
        orderIndex: Int,
        weightKg: Double?,
        targetReps: Int?,
        rangeMin: Int? = null,
        rangeMax: Int? = null,
    ) = RoutineSetEntity(
        id = id, routineExerciseId = routineExerciseId, orderIndex = orderIndex, setType = SetType.NORMAL,
        targetWeightKg = weightKg, targetReps = targetReps, targetRepRangeMin = rangeMin, targetRepRangeMax = rangeMax,
        targetDurationSeconds = null, targetDistanceMeters = null,
    )

    private fun workout(
        id: String = "w1",
        routineId: String? = null,
        startedAtMillis: Long = startedAt,
        completed: Boolean = false,
    ) = WorkoutEntity(
        id = id, routineId = routineId, title = "Original Title", notes = null,
        status = if (completed) com.enil.logez.core.domain.model.WorkoutStatus.COMPLETED
        else com.enil.logez.core.domain.model.WorkoutStatus.IN_PROGRESS,
        startedAt = startedAtMillis, endedAt = null, durationSeconds = 0,
        createdAt = startedAtMillis, updatedAt = startedAtMillis,
    )

    private fun workoutExercise(
        id: String,
        exerciseId: String,
        orderIndex: Int,
        supersetGroup: Int? = null,
        notes: String? = null,
        workoutId: String = "w1",
    ) = WorkoutExerciseEntity(
        id = id, workoutId = workoutId, exerciseId = exerciseId, orderIndex = orderIndex,
        supersetGroup = supersetGroup, restTimerSeconds = null, notes = notes,
    )

    private fun set(
        id: String,
        workoutExerciseId: String,
        orderIndex: Int,
        weightKg: Double?,
        reps: Int?,
        isCompleted: Boolean = true,
    ) = WorkoutSetEntity(
        id = id, workoutExerciseId = workoutExerciseId, orderIndex = orderIndex, setType = SetType.NORMAL,
        weightKg = weightKg, reps = reps, durationSeconds = null, distanceMeters = null, rpe = null,
        customMetric = null, isCompleted = isCompleted, completedAt = if (isCompleted) 1L else null,
    )

    private fun exercise(id: String) = Exercise(
        id = id, name = "Ex $id", exerciseType = ExerciseType.WEIGHT_REPS, primaryMuscleGroup = MuscleGroup.CHEST,
        secondaryMuscleGroups = emptyList(), equipment = Equipment.BARBELL, instructions = "", mediaPath = null,
        isCustom = false, isBodyweightVolumeEligible = false, isDeleted = false, createdAt = 0, updatedAt = 0,
    )

    private fun pullUp() = Exercise(
        id = "ex-pullup", name = "Pull Up (Weighted)", exerciseType = ExerciseType.BODYWEIGHT_WEIGHTED,
        primaryMuscleGroup = MuscleGroup.LATS, secondaryMuscleGroups = emptyList(), equipment = Equipment.NONE,
        instructions = "", mediaPath = null, isCustom = false, isBodyweightVolumeEligible = true,
        isDeleted = false, createdAt = 0, updatedAt = 0,
    )
}
