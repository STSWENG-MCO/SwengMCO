package com.enil.logez.core.data

import com.enil.logez.core.domain.model.Equipment
import com.enil.logez.core.domain.model.MuscleGroup
import com.enil.logez.core.domain.model.MuscleHead
import com.enil.logez.core.domain.model.SetType
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.model.WorkoutStructure
import org.junit.Assert.assertEquals
import org.junit.Test

/** A stored enum name the app no longer knows must degrade the row, not crash every read of it. */
class ConvertersFallbackTest {
    private val c = Converters()

    @Test
    fun `an unknown muscle group decodes as OTHER`() = assertEquals(MuscleGroup.OTHER, c.toMuscleGroup("RENAMED_AWAY"))

    @Test
    fun `an unknown equipment decodes as OTHER`() = assertEquals(Equipment.OTHER, c.toEquipment("HOVERBOARD"))

    @Test
    fun `an unknown set type decodes as NORMAL`() = assertEquals(SetType.NORMAL, c.toSetType("CLUSTER"))

    @Test
    fun `an unknown structure decodes as REGULAR`() = assertEquals(WorkoutStructure.REGULAR, c.toWorkoutStructure("EMOM"))

    @Test
    fun `an unknown workout kind decodes as STRENGTH`() = assertEquals(WorkoutKind.STRENGTH, c.toWorkoutKind("SWIM"))

    @Test
    fun `unknown entries are dropped from a muscle group list, known ones kept`() =
        assertEquals(listOf(MuscleGroup.CHEST), c.toMuscleGroupList("""["NOPE","CHEST"]"""))

    @Test
    fun `unknown entries are dropped from a muscle head list`() =
        assertEquals(listOf(MuscleHead.UPPER_CHEST), c.toMuscleHeadList("""["UPPER_CHEST","GONE"]"""))

    @Test
    fun `an unknown legacy single muscle head decodes as null instead of throwing`() =
        assertEquals(null, c.toMuscleHead("RETIRED_HEAD"))

    @Test
    fun `a known legacy single muscle head still round-trips`() =
        assertEquals(MuscleHead.TRICEPS_LONG_HEAD, c.toMuscleHead(c.fromMuscleHead(MuscleHead.TRICEPS_LONG_HEAD)))

    @Test
    fun `a null legacy single muscle head stays null`() = assertEquals(null, c.toMuscleHead(null))

    @Test
    fun `known values still round-trip exactly`() {
        assertEquals(MuscleGroup.LATS, c.toMuscleGroup(c.fromMuscleGroup(MuscleGroup.LATS)))
        assertEquals(SetType.DROPSET, c.toSetType(c.fromSetType(SetType.DROPSET)))
    }
}
