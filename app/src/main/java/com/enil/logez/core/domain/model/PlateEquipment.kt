package com.enil.logez.core.domain.model

import kotlinx.serialization.Serializable

/**
 * The Plate Calculator's available equipment (PHASE2_PLAN.md §5.1.5, §5.2) — settings-layer
 * shape only; the calculator's per-side-loading algorithm itself is M7 work.
 */
@Serializable
data class PlateEquipment(
    val barsKg: List<Double> = listOf(20.0),
    val platesKg: List<Double> = listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0),
)

val defaultPlateEquipment = PlateEquipment()

/*
 * M17 equipment-editor operations. All entries are rounded to the nearest quarter-kg (the
 * calculator's integer unit — see PlateCalculator's float-discipline doc), deduplicated, and kept
 * sorted ascending; a non-positive weight is a no-op. Removal matches on the same rounded value
 * the add stored, so the two are always symmetric.
 */

fun PlateEquipment.withBarAdded(kg: Double): PlateEquipment =
    copy(barsKg = addWeight(barsKg, kg))

/** The last bar is not removable — a plate calculator without a bar is meaningless (>=1 invariant). */
fun PlateEquipment.withBarRemoved(kg: Double): PlateEquipment =
    if (barsKg.size <= 1) this else copy(barsKg = removeWeight(barsKg, kg))

fun PlateEquipment.withPlateAdded(kg: Double): PlateEquipment =
    copy(platesKg = addWeight(platesKg, kg))

fun PlateEquipment.withPlateRemoved(kg: Double): PlateEquipment =
    copy(platesKg = removeWeight(platesKg, kg))

private fun addWeight(list: List<Double>, kg: Double): List<Double> {
    val rounded = roundToQuarterKg(kg)
    if (rounded <= 0.0 || rounded in list) return list
    return (list + rounded).sorted()
}

private fun removeWeight(list: List<Double>, kg: Double): List<Double> {
    val rounded = roundToQuarterKg(kg)
    return list.filterNot { it == rounded }
}

private fun roundToQuarterKg(kg: Double): Double =
    if (kg.isNaN() || kg <= 0.0) 0.0 else Math.round(kg * 4.0) / 4.0
