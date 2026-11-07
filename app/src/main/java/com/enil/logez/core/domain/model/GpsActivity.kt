package com.enil.logez.core.domain.model

/**
 * What a GPS-tracked workout was, for its summary's RUN / WALK label. Nothing stores this as a
 * column: a tracked session is identified by the one exercise the "Track a walk/run" card started
 * it with, so it is resolved from that exercise here.
 */
enum class GpsActivity {
    RUN,
    WALK,

    /** A GPS workout whose exercise is neither seed walk nor seed run, and whose name says neither. */
    OTHER,
    ;

    companion object {
        /**
         * The seed ids of the two exercises the Workout tab's quick-track card starts sessions
         * with (`assets/seed/exercises_seed.json`). Seed ids never change once shipped, unlike
         * names, which the user can edit.
         */
        const val RUNNING_OUTDOOR_EXERCISE_ID = "9d6b8b71-631e-5250-becb-099917d7805d"
        const val WALKING_OUTDOOR_EXERCISE_ID = "32912a68-64de-590c-a562-9361b1856839"

        fun resolve(exerciseId: String?, exerciseName: String?): GpsActivity = when {
            exerciseId == RUNNING_OUTDOOR_EXERCISE_ID -> RUN
            exerciseId == WALKING_OUTDOOR_EXERCISE_ID -> WALK
            exerciseName?.contains("walk", ignoreCase = true) == true -> WALK
            exerciseName?.contains("run", ignoreCase = true) == true -> RUN
            else -> OTHER
        }
    }
}
