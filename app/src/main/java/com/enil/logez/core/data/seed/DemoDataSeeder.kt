package com.enil.logez.core.data.seed

import com.enil.logez.core.common.Clock
import com.enil.logez.core.data.entity.WorkoutEntity
import com.enil.logez.core.data.entity.WorkoutExerciseEntity
import com.enil.logez.core.data.entity.WorkoutSetEntity
import com.enil.logez.core.domain.model.ExerciseType
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutStatus
import com.enil.logez.core.domain.repository.Exercise
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import com.enil.logez.feature.history.WorkoutDeleter
import com.enil.logez.feature.workout.finish.PersonalRecordsUpdater
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Temporary dev/demo tool (Owner request 2026-08-26, extended to 2 years 2026-08-27) — populates
 * 2 years of realistic COMPLETED workout history against real seeded exercises, so the Analytics
 * dashboard, Statistics, the Workout-tab heatmap, and Goals progress all have real content to show
 * rather than an empty install. Not wired into app-start seeding anywhere; only the temporary "Seed
 * Demo Data" row on the Profile tab calls [seed]. Every generated workout's `notes` field is
 * tagged [DEMO_MARKER] so [clear] can find and remove exactly what this seeded — via the same
 * [WorkoutDeleter] a real "Delete Workout" uses, so PRs get rebuilt correctly on clear too — and
 * never touch a real logged workout, which would never carry that marker.
 *
 * Session scheduling follows a real periodization shape (Owner request 2026-08-26, "slight noise
 * ... as if the user is periodizing") rather than a flat N-times-a-week grid: a 4-week mesocycle of
 * 3 build weeks (3-5 sessions, ramping) followed by 1 deload week (1-2 sessions), repeated across
 * the full span, with each week's session days drawn at random rather than fixed to e.g.
 * Mon/Wed/Fri — a perfectly uniform grid is exactly what would read as fake on the heatmap.
 * [SEED] is fixed so the "randomness" is still deterministic and testable, not
 * [kotlin.random.Random]'s default source (which this codebase avoids in production logic for the
 * same reason it avoids `Clock.System`/`Date()` — see `Clock` in `core/common`).
 */
@Singleton
class DemoDataSeeder @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val workoutRepository: WorkoutRepository,
    private val personalRecordsUpdater: PersonalRecordsUpdater,
    private val workoutDeleter: WorkoutDeleter,
    private val clock: Clock,
) {
    suspend fun seed() {
        val exercisesByName = exerciseRepository.getAllActive().associateBy { it.name }
        // The seed library ships names in a fixed, hand-verified set (docs/PHASE2_PLAN.md's 400)
        // -- if any of these is missing (seed not applied yet, or a future rename), skip it rather
        // than build a workout with a hole in it.
        val push = PUSH_NAMES.mapNotNull { exercisesByName[it] }
        val pull = PULL_NAMES.mapNotNull { exercisesByName[it] }
        val legs = LEGS_NAMES.mapNotNull { exercisesByName[it] }
        if (push.isEmpty() && pull.isEmpty() && legs.isEmpty()) return // seed library not loaded yet

        val includeWarmups = personalRecordsUpdater.includeWarmupsInStats()
        val nowMillis = clock.now().toEpochMilliseconds()
        val touchedExerciseIds = mutableSetOf<String>()
        val random = Random(SEED)

        var sessionIndex = 0 // runs across the whole 12 weeks -- drives the push/pull/legs rotation and the progression trend, independent of any one week's session count

        for (week in 0 until WEEKS) {
            val sessionCount = sessionsForWeek(week, random)
            // Distinct random weekdays, not a fixed Mon/Wed/Fri slot -- this is the actual "noise".
            val daysInWeek = (0..6).shuffled(random).take(sessionCount).sorted()

            for (dayOfWeek in daysInWeek) {
                // dayOfWeek 0=Monday..6=Sunday. +2 keeps even the most recent week's latest
                // possible day at least 2 days back, so "today" (and "yesterday") stay honestly
                // empty on the heatmap until a real workout is logged.
                val daysAgo = (WEEKS - 1 - week) * 7 + (6 - dayOfWeek) + 2
                val timeJitterMillis = random.nextLong(-3 * HOUR_MILLIS, 3 * HOUR_MILLIS)
                val startedAt = nowMillis - daysAgo * DAY_MILLIS - MORNING_OFFSET_MILLIS + timeJitterMillis
                val durationSeconds = 2_700 + random.nextInt(0, 1_800) // 45-75 min
                val progression = (sessionIndex.toDouble() / ESTIMATED_TOTAL_SESSIONS).coerceIn(0.0, 1.0)

                val todays = when (sessionIndex % 3) { 0 -> push; 1 -> pull; else -> legs }
                sessionIndex++
                if (todays.isEmpty()) continue

                val workoutId = UUID.randomUUID().toString()
                val workoutExercises = mutableListOf<WorkoutExerciseEntity>()
                val workoutSets = mutableListOf<WorkoutSetEntity>()

                todays.forEachIndexed { exIndex, exercise ->
                    val weId = UUID.randomUUID().toString()
                    workoutExercises += WorkoutExerciseEntity(
                        id = weId, workoutId = workoutId, exerciseId = exercise.id, orderIndex = exIndex,
                        supersetGroup = null, restTimerSeconds = null, notes = null,
                    )
                    touchedExerciseIds += exercise.id

                    for (setIndex in 0 until SETS_PER_EXERCISE) {
                        val isWarmup = setIndex == 0 && exercise.exerciseType == ExerciseType.WEIGHT_REPS
                        val values = setValues(exercise, setIndex, progression, isWarmup, random)
                        workoutSets += WorkoutSetEntity(
                            id = UUID.randomUUID().toString(),
                            workoutExerciseId = weId,
                            orderIndex = setIndex,
                            setType = if (isWarmup) SetType.WARMUP else SetType.NORMAL,
                            weightKg = values.weightKg,
                            reps = values.reps,
                            durationSeconds = values.durationSeconds,
                            distanceMeters = null,
                            rpe = null,
                            customMetric = null,
                            isCompleted = true,
                            completedAt = startedAt + setIndex * 90_000L,
                        )
                    }
                }

                workoutRepository.insertFullWorkout(
                    WorkoutEntity(
                        id = workoutId, routineId = null, title = DEMO_TITLE, notes = DEMO_MARKER,
                        status = WorkoutStatus.COMPLETED, startedAt = startedAt,
                        endedAt = startedAt + durationSeconds * 1_000L, durationSeconds = durationSeconds,
                        createdAt = startedAt, updatedAt = startedAt,
                    ),
                    workoutExercises,
                    workoutSets,
                )
            }
        }

        if (touchedExerciseIds.isNotEmpty()) {
            personalRecordsUpdater.rebuildForExercises(touchedExerciseIds, workoutId = "", includeWarmupsInStats = includeWarmups)
        }
    }

    /** Removes exactly what [seed] created (matched by [DEMO_MARKER]), never a real logged workout. */
    suspend fun clear() {
        workoutRepository.getCompletedWorkouts()
            .filter { it.notes == DEMO_MARKER }
            .forEach { workoutDeleter.delete(it.id) }
    }

    /** 4-week mesocycle: 3 build weeks (ramping volume) then 1 lighter deload week, repeated. */
    private fun sessionsForWeek(week: Int, random: Random): Int =
        if (week % 4 == 3) 1 + random.nextInt(0, 2) else 3 + random.nextInt(0, 3)

    private data class SetValues(val weightKg: Double?, val reps: Int?, val durationSeconds: Int?)

    private fun setValues(exercise: Exercise, setIndex: Int, progression: Double, isWarmup: Boolean, random: Random): SetValues =
        when (exercise.exerciseType) {
            ExerciseType.WEIGHT_REPS -> {
                val jitter = 1.0 + random.nextInt(-5, 6) / 100.0 // +/-5%, session-to-session variation
                val working = roundToNearestPlate(BASELINE_KG.getValue(exercise.name) * (1.0 + progression * 0.15) * jitter)
                SetValues(
                    weightKg = if (isWarmup) roundToNearestPlate(working * 0.5) else working,
                    reps = 6 + (setIndex % 3) + random.nextInt(0, 2),
                    durationSeconds = null,
                )
            }
            ExerciseType.REPS_ONLY -> SetValues(
                weightKg = null,
                reps = 6 + (progression * 4).roundToInt() + (setIndex % 2) + random.nextInt(0, 2),
                durationSeconds = null,
            )
            ExerciseType.DURATION -> SetValues(
                weightKg = null,
                reps = null,
                durationSeconds = 30 + (progression * 30).roundToInt() + setIndex * 5 + random.nextInt(-5, 6),
            )
            else -> SetValues(null, null, null)
        }

    private fun roundToNearestPlate(kg: Double): Double = (kg / 2.5).roundToInt() * 2.5

    companion object {
        const val DEMO_MARKER = "__logEZ_demo_seed__"
        private const val DEMO_TITLE = "Workout"
        private const val SEED = 20_260_826L
        private const val WEEKS = 104 // 2 years
        private const val SETS_PER_EXERCISE = 4
        private const val ESTIMATED_TOTAL_SESSIONS = 351.0 // ~(3 build weeks avg 4 + 1 deload avg 1.5) x 26 cycles -- only used to shape the progression trend, not asserted on
        private const val HOUR_MILLIS = 60 * 60 * 1_000L
        private const val DAY_MILLIS = 24 * HOUR_MILLIS
        private const val MORNING_OFFSET_MILLIS = 15 * HOUR_MILLIS // "today minus N days" lands ~9am before jitter

        private val PUSH_NAMES = listOf("Bench Press (Barbell)", "Overhead Press (Barbell)", "Plank")
        private val PULL_NAMES = listOf("Bent Over Row (Barbell)", "Pull Up")
        private val LEGS_NAMES = listOf("Back Squat (Barbell)", "Deadlift (Barbell)")

        private val BASELINE_KG = mapOf(
            "Bench Press (Barbell)" to 60.0,
            "Overhead Press (Barbell)" to 40.0,
            "Bent Over Row (Barbell)" to 50.0,
            "Back Squat (Barbell)" to 80.0,
            "Deadlift (Barbell)" to 100.0,
        )
    }
}
