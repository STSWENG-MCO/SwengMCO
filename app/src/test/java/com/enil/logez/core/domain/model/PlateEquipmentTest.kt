package com.enil.logez.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * M17 equipment-editor invariants (§5.1.5 "Manage"): entries round to the calculator's quarter-kg
 * grid, dedupe, stay sorted, and the last bar can never be removed. All expected values literal.
 */
class PlateEquipmentTest {

    @Test
    fun `default equipment is one 20 bar and the seven standard denominations`() {
        assertEquals(listOf(20.0), defaultPlateEquipment.barsKg)
        assertEquals(listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0), defaultPlateEquipment.platesKg)
    }

    // --- Bars ---

    @Test
    fun `withBarAdded inserts in sorted order`() {
        val equipment = defaultPlateEquipment.withBarAdded(15.0).withBarAdded(25.0)
        assertEquals(listOf(15.0, 20.0, 25.0), equipment.barsKg)
    }

    @Test
    fun `withBarAdded rounds to the quarter-kg grid before storing`() {
        assertEquals(listOf(17.5, 20.0), defaultPlateEquipment.withBarAdded(17.4).barsKg)
    }

    @Test
    fun `withBarAdded ignores duplicates and non-positive weights`() {
        assertEquals(listOf(20.0), defaultPlateEquipment.withBarAdded(20.0).barsKg)
        // A weight that rounds onto an existing entry is a duplicate too.
        assertEquals(listOf(20.0), defaultPlateEquipment.withBarAdded(20.1).barsKg)
        assertEquals(listOf(20.0), defaultPlateEquipment.withBarAdded(0.0).barsKg)
        assertEquals(listOf(20.0), defaultPlateEquipment.withBarAdded(-7.5).barsKg)
    }

    @Test
    fun `withBarRemoved never removes the last bar`() {
        val single = defaultPlateEquipment
        assertSame(single, single.withBarRemoved(20.0))
        assertEquals(listOf(20.0), single.withBarRemoved(20.0).barsKg)
    }

    @Test
    fun `withBarRemoved drops the matching bar when more than one remains`() {
        val two = defaultPlateEquipment.withBarAdded(15.0)
        assertEquals(listOf(20.0), two.withBarRemoved(15.0).barsKg)
    }

    @Test
    fun `withBarRemoved matches on the same rounded value the add stored`() {
        // 17.4 was stored as 17.5; removing with the same raw input must find it.
        val two = defaultPlateEquipment.withBarAdded(17.4)
        assertEquals(listOf(20.0), two.withBarRemoved(17.4).barsKg)
    }

    // --- Plates ---

    @Test
    fun `withPlateAdded rounds, dedupes and keeps the list sorted`() {
        val added = defaultPlateEquipment.withPlateAdded(0.6)
        assertEquals(listOf(0.5, 1.25, 2.5, 5.0, 10.0, 15.0, 20.0, 25.0), added.platesKg)
        assertEquals(added.platesKg, added.withPlateAdded(2.5).platesKg)
    }

    @Test
    fun `withPlateRemoved can empty the plate list entirely`() {
        var equipment = defaultPlateEquipment
        for (plate in defaultPlateEquipment.platesKg) equipment = equipment.withPlateRemoved(plate)
        assertEquals(emptyList<Double>(), equipment.platesKg)
        // ...while the bar invariant still holds independently.
        assertEquals(listOf(20.0), equipment.barsKg)
    }

    @Test
    fun `bar and plate operations never touch the other list`() {
        val equipment = defaultPlateEquipment.withBarAdded(15.0).withPlateRemoved(25.0)
        assertEquals(listOf(15.0, 20.0), equipment.barsKg)
        assertEquals(listOf(1.25, 2.5, 5.0, 10.0, 15.0, 20.0), equipment.platesKg)
    }
}
