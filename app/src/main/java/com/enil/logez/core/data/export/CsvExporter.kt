package com.enil.logez.core.data.export

import com.enil.logez.core.data.dao.MeasurementDao
import com.enil.logez.core.data.dao.WorkoutDao
import java.io.OutputStream
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the CSV exports.
 *
 * Takes an [OutputStream] rather than anything Android-shaped, so the whole format is testable
 * without a document provider — the Uri is resolved one layer up.
 */
@Singleton
class CsvExporter @Inject constructor(
    private val workoutDao: WorkoutDao,
    private val measurementDao: MeasurementDao,
) {
    suspend fun exportWorkouts(out: OutputStream): Int {
        // Resolved per call, never cached: an export is a local-time artifact, and a zone read at
        // construction would outlive a real zone change.
        val zone = ZoneId.systemDefault()
        val rows = workoutDao.getWorkoutCsvRows()
        out.writer(Charsets.UTF_8).use { writer ->
            writer.write(WorkoutCsvFormatter.header())
            rows.forEach { writer.write(WorkoutCsvFormatter.row(it, zone)) }
        }
        return rows.size
    }

    suspend fun exportMeasurements(out: OutputStream): Int {
        val rows = measurementDao.getAllForExport()
        out.writer(Charsets.UTF_8).use { writer ->
            writer.write(MeasurementCsvFormatter.header())
            rows.forEach { writer.write(MeasurementCsvFormatter.row(it)) }
        }
        return rows.size
    }

    suspend fun workoutSetCount(): Int = workoutDao.getWorkoutCsvRows().size

    suspend fun measurementCount(): Int = measurementDao.getAllForExport().size
}
