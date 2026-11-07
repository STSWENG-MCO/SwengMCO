package com.enil.logez.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleHeadTest {
    @Test
    fun `the six researched groups each expose their heads`() {
        assertEquals(
            listOf(MuscleHead.ANTERIOR_DELTOID, MuscleHead.LATERAL_DELTOID, MuscleHead.POSTERIOR_DELTOID),
            MuscleGroup.SHOULDERS.availableHeads,
        )
        assertEquals(listOf(MuscleHead.UPPER_CHEST, MuscleHead.LOWER_CHEST), MuscleGroup.CHEST.availableHeads)
        assertEquals(listOf(MuscleHead.TRICEPS_LATERAL_HEAD, MuscleHead.TRICEPS_LONG_HEAD), MuscleGroup.TRICEPS.availableHeads)
        assertEquals(listOf(MuscleHead.GASTROCNEMIUS, MuscleHead.SOLEUS), MuscleGroup.CALVES.availableHeads)
        assertEquals(listOf(MuscleHead.UPPER_LATS, MuscleHead.MID_LATS, MuscleHead.LOWER_LATS), MuscleGroup.LATS.availableHeads)
        assertEquals(listOf(MuscleHead.LATERAL_HAMSTRING, MuscleHead.MEDIAL_HAMSTRING), MuscleGroup.HAMSTRINGS.availableHeads)
    }

    @Test
    fun `every other group has no tracked heads`() {
        val withHeads = setOf(MuscleGroup.SHOULDERS, MuscleGroup.CHEST, MuscleGroup.TRICEPS, MuscleGroup.CALVES, MuscleGroup.LATS, MuscleGroup.HAMSTRINGS)
        MuscleGroup.entries.filterNot { it in withHeads }.forEach { group ->
            assertTrue("expected no heads for $group, got ${group.availableHeads}", group.availableHeads.isEmpty())
        }
    }

    @Test
    fun `every MuscleHead value belongs to exactly one group's list`() {
        val allListed = MuscleGroup.entries.flatMap { it.availableHeads }
        assertEquals(MuscleHead.entries.toSet(), allListed.toSet())
        assertEquals("no head should be duplicated across groups", allListed.size, allListed.toSet().size)
    }
}
