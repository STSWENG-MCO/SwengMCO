package com.enil.logez.core.common

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * M21a. A plain-Kotlin haversine distance, not `android.location.Location.distanceTo()` — that
 * static method needs Robolectric to unit-test, and `ActivityTrackingController` follows this
 * codebase's framework-free-controller convention (`WorkoutSessionController` and its plain-JVM
 * test are the precedent). Haversine's spherical-Earth approximation differs from `distanceTo()`'s
 * WGS84-ellipsoidal one by well under 0.5% at running-route scale — summing many closely-spaced
 * GPS fixes, that gap is negligible next to ordinary GPS fix noise.
 */
object GeoDistance {
    private const val EARTH_RADIUS_METERS = 6_371_000.0

    fun metersBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).let { it * it }
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }
}
