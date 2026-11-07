package com.enil.logez.core.data.export

import com.enil.logez.core.domain.model.SetType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One logged set, joined with the workout and exercise it belongs to. */
data class WorkoutCsvRow(
    val workoutTitle: String,
    val workoutNotes: String?,
    val startedAt: Long,
    val endedAt: Long?,
    val exerciseName: String,
    val supersetGroup: Int?,
    val exerciseNotes: String?,
    val setIndex: Int,
    val setType: SetType,
    val weightKg: Double?,
    val reps: Int?,
    val distanceMeters: Double?,
    val durationSeconds: Int?,
    val rpe: Double?,
)

/**
 * One row per set, in the column order the export schema pins so that tooling built for that
 * schema can read it unmodified.
 *
 * Weights are always kilograms regardless of the display setting, because the format has no unit
 * column and guessing from the app's current preference would make two exports of the same data
 * disagree. `customMetric` has no column here and is dropped; the JSON backup is the lossless
 * format.
 *
 * Every number and date is formatted against an explicit locale. A default-locale format emits
 * `12,5` on a comma-decimal device and silently destroys a comma-delimited file — and it will not
 * reproduce on an English-locale emulator.
 */
object WorkoutCsvFormatter {
    val HEADER: List<String> = listOf(
        "title", "start_time", "end_time", "description", "exercise_title", "superset_id",
        "exercise_notes", "set_index", "set_type", "weight_kg", "reps", "distance_km",
        "duration_seconds", "rpe",
    )

    private val TIMESTAMP = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)

    fun header(): String = CsvWriter.row(HEADER)

    fun row(row: WorkoutCsvRow, zone: ZoneId): String = CsvWriter.row(
        listOf(
            row.workoutTitle,
            formatTimestamp(row.startedAt, zone),
            row.endedAt?.let { formatTimestamp(it, zone) },
            row.workoutNotes,
            row.exerciseName,
            row.supersetGroup?.toString(),
            row.exerciseNotes,
            row.setIndex.toString(),
            row.setType.name.lowercase(Locale.ROOT),
            row.weightKg?.let(::formatDecimal),
            row.reps?.toString(),
            row.distanceMeters?.let { formatDecimal(it / 1000.0) },
            row.durationSeconds?.toString(),
            row.rpe?.let(::formatDecimal),
        ),
    )

    private fun formatTimestamp(epochMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(TIMESTAMP)

    /** Trailing zeros trimmed, so 100.0 kg writes as `100` rather than `100.00`. */
    internal fun formatDecimal(value: Double): String {
        val rendered = String.format(Locale.ROOT, "%.2f", value)
        return rendered.trimEnd('0').trimEnd('.')
    }
}
