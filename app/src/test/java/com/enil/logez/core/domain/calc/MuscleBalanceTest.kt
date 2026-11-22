package com.enil.logez.core.domain.calc

import com.enil.logez.core.domain.model.MuscleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MuscleBalanceTest {
    private fun share(
        group: MuscleGroup,
        count: Int,
    ) = MuscleStatsCalculator.GroupShare(group, count, 0) // sharePercent unused by balanceAxes

    @Test
    fun `four non-region groups map to no BodyRegion`() {
        assertEquals(null, MuscleGroup.CARDIO.toBodyRegion())
        assertEquals(null, MuscleGroup.FULL_BODY.toBodyRegion())
        assertEquals(null, MuscleGroup.OTHER.toBodyRegion())
        assertEquals(null, MuscleGroup.NECK.toBodyRegion())
    }

    @Test
    fun `every one of the 16 body-relevant groups maps to exactly one region`() {
        val mapped = MuscleGroup.entries.mapNotNull { it.toBodyRegion() }
        assertEquals(16, mapped.size)
    }

    @Test
    fun `chest 3 + lats 1 + traps 1 gives Chest 60 percent and Back 40 percent, six zeros`() {
        val result = balanceAxes(listOf(share(MuscleGroup.CHEST, 3), share(MuscleGroup.LATS, 1), share(MuscleGroup.TRAPS, 1)))

        assertEquals(8, result.size)
        assertEquals(BodyRegion.entries.toList(), result.map { it.region }) // fixed order, always all 8
        assertEquals(RegionShare(BodyRegion.CHEST, 3, 60), result.first { it.region == BodyRegion.CHEST })
        assertEquals(RegionShare(BodyRegion.BACK, 2, 40), result.first { it.region == BodyRegion.BACK })
        val zeroRegions = BodyRegion.entries - BodyRegion.CHEST - BodyRegion.BACK
        zeroRegions.forEach { region ->
            assertEquals(RegionShare(region, 0, 0), result.first { it.region == region })
        }
    }

    @Test
    fun `cardio-only input is all zeros, not empty`() {
        val result = balanceAxes(listOf(share(MuscleGroup.CARDIO, 5)))

        assertEquals(8, result.size)
        result.forEach {
            assertEquals(0, it.setCount)
            assertEquals(0, it.sharePercent)
        }
    }

    @Test
    fun `an empty distribution list is also all zeros`() {
        val result = balanceAxes(emptyList())
        assertEquals(8, result.size)
        result.forEach {
            assertEquals(0, it.setCount)
            assertEquals(0, it.sharePercent)
        }
    }

    @Test
    fun `a group sharing multiple raw groups sums them into one region`() {
        val result = balanceAxes(listOf(share(MuscleGroup.HAMSTRINGS, 2), share(MuscleGroup.GLUTES, 2)))
        assertEquals(RegionShare(BodyRegion.HAMSTRINGS_GLUTES, 4, 100), result.first { it.region == BodyRegion.HAMSTRINGS_GLUTES })
    }

    @Test
    fun `non-region groups are left out of the denominator`() {
        val result = balanceAxes(listOf(share(MuscleGroup.CHEST, 2), share(MuscleGroup.CARDIO, 6), share(MuscleGroup.NECK, 2)))

        // 10 sets in total, but only 2 are on the wheel, so chest is 100% of the wheel, not 20%.
        assertEquals(RegionShare(BodyRegion.CHEST, 2, 100), result.first { it.region == BodyRegion.CHEST })
        assertEquals(2, result.sumOf { it.setCount })
    }

    @Test
    fun `equal thirds each round to 33 percent`() {
        val result = balanceAxes(listOf(share(MuscleGroup.CHEST, 1), share(MuscleGroup.LATS, 1), share(MuscleGroup.SHOULDERS, 1)))

        val percents = listOf(BodyRegion.CHEST, BodyRegion.BACK, BodyRegion.SHOULDERS).map { r -> result.first { it.region == r }.sharePercent }
        assertEquals(listOf(33, 33, 33), percents)
    }

    @Test
    fun `hasRegionData is false for empty and non-region-only input, true once a region has a set`() {
        assertFalse(balanceAxes(emptyList()).hasRegionData())
        assertFalse(balanceAxes(listOf(share(MuscleGroup.NECK, 4))).hasRegionData())
        assertTrue(balanceAxes(listOf(share(MuscleGroup.CHEST, 1))).hasRegionData())
    }
}
