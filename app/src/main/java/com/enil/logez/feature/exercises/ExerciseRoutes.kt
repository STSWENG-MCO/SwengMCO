package com.enil.logez.feature.exercises

import android.net.Uri

/**
 * Route strings for the exercise-library sub-screens (PHASE2_PLAN.md §4: reached from Profile ->
 * Exercises; sub-screens hide the bottom bar, unlike the three tab roots).
 */
object ExerciseRoutes {
    const val LIBRARY = "exercise_library?muscle={muscle}"
    const val DETAIL = "exercise_detail/{exerciseId}"
    const val EDITOR = "custom_exercise_editor?exerciseId={exerciseId}&prefillName={prefillName}"

    fun library(muscle: String? = null) = "exercise_library" + (muscle?.let { "?muscle=$it" } ?: "")

    fun detail(exerciseId: String) = "exercise_detail/$exerciseId"

    fun editor(exerciseId: String? = null, prefillName: String? = null): String {
        val params = buildList {
            exerciseId?.let { add("exerciseId=${Uri.encode(it)}") }
            prefillName?.let { add("prefillName=${Uri.encode(it)}") }
        }
        return "custom_exercise_editor" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
    }
}
