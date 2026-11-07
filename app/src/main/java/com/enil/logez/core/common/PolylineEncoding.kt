package com.enil.logez.core.common

import kotlin.math.roundToLong

/**
 * M21a. Google's classic polyline algorithm (precision 5) — a well-known, dependency-free string
 * encoding for a lat/lng sequence, unrelated to any Maps-SDK API. Used to compress a GPS route
 * into `activity_tracks.route_polyline` without a per-fix Room row.
 */
object PolylineEncoding {
    private const val PRECISION = 1e5

    fun encode(points: List<Pair<Double, Double>>): String {
        val sb = StringBuilder()
        var prevLat = 0L
        var prevLng = 0L
        for ((lat, lng) in points) {
            val lat5 = (lat * PRECISION).roundToLong()
            val lng5 = (lng * PRECISION).roundToLong()
            encodeValue(lat5 - prevLat, sb)
            encodeValue(lng5 - prevLng, sb)
            prevLat = lat5
            prevLng = lng5
        }
        return sb.toString()
    }

    fun decode(encoded: String): List<Pair<Double, Double>> {
        val points = mutableListOf<Pair<Double, Double>>()
        var index = 0
        var lat = 0L
        var lng = 0L
        while (index < encoded.length) {
            lat += decodeValue(encoded, index).also { index = it.second }.first
            lng += decodeValue(encoded, index).also { index = it.second }.first
            points += (lat / PRECISION) to (lng / PRECISION)
        }
        return points
    }

    /**
     * One integer stream, delta-encoded with the same character scheme as a route: used for
     * `activity_tracks.route_times`, the seconds-since-start of each route point (2026-09-26). A
     * run's times grow by a few seconds per point, so each one costs a single character.
     */
    fun encodeDeltas(values: List<Long>): String {
        val sb = StringBuilder()
        var previous = 0L
        for (value in values) {
            encodeValue(value - previous, sb)
            previous = value
        }
        return sb.toString()
    }

    fun decodeDeltas(encoded: String): List<Long> {
        val values = mutableListOf<Long>()
        var index = 0
        var current = 0L
        while (index < encoded.length) {
            current += decodeValue(encoded, index).also { index = it.second }.first
            values += current
        }
        return values
    }

    private fun encodeValue(value: Long, sb: StringBuilder) {
        var v = value shl 1
        if (v < 0) v = v.inv()
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f).toInt()) + 63).toChar())
            v = v shr 5
        }
        sb.append((v.toInt() + 63).toChar())
    }

    /** Returns the decoded delta and the index just past the consumed characters. */
    private fun decodeValue(encoded: String, start: Int): Pair<Long, Int> {
        var result = 0L
        var shift = 0
        var index = start
        var byte: Int
        do {
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f).toLong() shl shift)
            shift += 5
        } while (byte >= 0x20)
        val delta = if (result and 1L != 0L) (result shr 1).inv() else result shr 1
        return delta to index
    }
}
