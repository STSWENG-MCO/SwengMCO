package com.enil.logez.feature.routines

import android.net.Uri

/** Route strings for the Workout tab's sub-screens (PHASE2_PLAN.md §4). */
object RoutineRoutes {
    const val DETAIL = "routine_detail/{routineId}"
    const val BUILDER = "routine_builder?routineId={routineId}&folderId={folderId}"

    fun detail(routineId: String) = "routine_detail/$routineId"

    fun builder(routineId: String? = null, folderId: String? = null): String {
        val params = buildList {
            routineId?.let { add("routineId=${Uri.encode(it)}") }
            folderId?.let { add("folderId=${Uri.encode(it)}") }
        }
        return "routine_builder" + if (params.isEmpty()) "" else "?" + params.joinToString("&")
    }
}
