package com.enil.logez.core.data.export

import com.enil.logez.core.data.entity.BodyMeasurementEntity

/**
 * One row per date, mirroring the stored columns exactly.
 *
 * Column names carry their canonical unit (`_kg`, `_cm`) and the values are always in it,
 * regardless of the display setting — a file whose meaning changes with an app preference is not
 * a record of anything.
 *
 * Unlike the workout export there is no external schema to match here, so this matches our own.
 */
object MeasurementCsvFormatter {
    val HEADER: List<String> = listOf(
        "date", "weight_kg", "lean_mass_kg", "fat_percent", "neck_cm", "shoulder_cm", "chest_cm",
        "left_bicep_cm", "right_bicep_cm", "left_forearm_cm", "right_forearm_cm", "abdomen_cm",
        "waist_cm", "hips_cm", "left_thigh_cm", "right_thigh_cm", "left_calf_cm", "right_calf_cm",
    )

    fun header(): String = CsvWriter.row(HEADER)

    /** A metric the user never entered writes as an empty cell, never as 0. */
    fun row(entity: BodyMeasurementEntity): String = CsvWriter.row(
        listOf(
            entity.date,
            entity.weightKg?.let(WorkoutCsvFormatter::formatDecimal),
            entity.leanMassKg?.let(WorkoutCsvFormatter::formatDecimal),
            entity.fatPercent?.let(WorkoutCsvFormatter::formatDecimal),
            entity.neckCm?.let(WorkoutCsvFormatter::formatDecimal),
            entity.shoulderCm?.let(WorkoutCsvFormatter::formatDecimal),
            entity.chestCm?.let(WorkoutCsvFormatter::formatDecimal),
            entity.leftBicepCm?.let(WorkoutCsvFormatter::formatDecimal),
            entity.rightBicepCm?.let(WorkoutCsvFormatter::formatDecimal),
            entity.leftForearmCm?.let(WorkoutCsvFormatter::formatDecimal),
            entity.rightForearmCm?.let(WorkoutCsvFormatter::formatDecimal),
            entity.abdomenCm?.let(WorkoutCsvFormatter::formatDecimal),
            entity.waistCm?.let(WorkoutCsvFormatter::formatDecimal),
            entity.hipsCm?.let(WorkoutCsvFormatter::formatDecimal),
            entity.leftThighCm?.let(WorkoutCsvFormatter::formatDecimal),
            entity.rightThighCm?.let(WorkoutCsvFormatter::formatDecimal),
            entity.leftCalfCm?.let(WorkoutCsvFormatter::formatDecimal),
            entity.rightCalfCm?.let(WorkoutCsvFormatter::formatDecimal),
        ),
    )
}
