package com.enil.logez.core.domain

/**
 * Repaints the home-screen widget. An interface here, with no Android types, so the domain-layer
 * callers that trigger it stay framework-free and testable.
 *
 * Every refresh is pushed. Nothing observes the widget's data, because a Glance session times out
 * within seconds of rendering and any subscription started there dies with it.
 */
interface WidgetRefresher {
    suspend fun refresh()

    companion object {
        /**
         * Lets hand-constructed tests build callers without a widget, the same way
         * [com.enil.logez.core.common.AppLogger.NoOp] already serves `WorkoutFinisher`.
         */
        val NoOp: WidgetRefresher = object : WidgetRefresher {
            override suspend fun refresh() = Unit
        }
    }
}
