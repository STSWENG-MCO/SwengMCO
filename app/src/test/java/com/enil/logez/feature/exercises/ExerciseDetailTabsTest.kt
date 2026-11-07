package com.enil.logez.feature.exercises

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which tabs the exercise detail screen shows, and which one is selected. */
class ExerciseDetailTabsTest {
    @Test
    fun `nothing is shown while the exercise is still loading`() {
        assertEquals(emptyList<DetailTab>(), detailTabsFor(isLoading = true, instructions = null))
    }

    @Test
    fun `loading wins over having instructions, so no tab pops in a frame later`() {
        assertEquals(emptyList<DetailTab>(), detailTabsFor(isLoading = true, instructions = "1. Set up"))
    }

    @Test
    fun `an exercise with no instructions has no How-to tab`() {
        // The case for all 400 seeded exercises, which ship with instructions unset.
        assertEquals(
            listOf(DetailTab.SUMMARY, DetailTab.HISTORY),
            detailTabsFor(isLoading = false, instructions = ""),
        )
    }

    @Test
    fun `a missing exercise has no How-to tab either`() {
        assertEquals(
            listOf(DetailTab.SUMMARY, DetailTab.HISTORY),
            detailTabsFor(isLoading = false, instructions = null),
        )
    }

    @Test
    fun `whitespace-only instructions do not earn a tab`() {
        assertEquals(
            listOf(DetailTab.SUMMARY, DetailTab.HISTORY),
            detailTabsFor(isLoading = false, instructions = "  \n  "),
        )
    }

    @Test
    fun `an exercise the user has written steps for gets all three tabs`() {
        assertEquals(
            listOf(DetailTab.SUMMARY, DetailTab.HISTORY, DetailTab.HOW_TO),
            detailTabsFor(isLoading = false, instructions = "1. Set up\n2. Brace"),
        )
    }

    @Test
    fun `a selection that no longer exists falls back to Summary`() {
        // Reachable: clear an exercise's instructions elsewhere, then return to this screen.
        assertEquals(
            DetailTab.SUMMARY,
            selectedDetailTab(DetailTab.HOW_TO, listOf(DetailTab.SUMMARY, DetailTab.HISTORY)),
        )
    }

    @Test
    fun `a selection that still exists is kept`() {
        assertEquals(DetailTab.HISTORY, selectedDetailTab(DetailTab.HISTORY, DetailTab.entries))
    }

    @Test
    fun `the selected tab is always present in the tab list`() {
        // The invariant the old ordinal-indexed TabRow violated: indexOf must never return -1.
        val tabLists = listOf(
            detailTabsFor(isLoading = false, instructions = ""),
            detailTabsFor(isLoading = false, instructions = "steps"),
        )
        for (tabs in tabLists) {
            for (requested in DetailTab.entries) {
                assertTrue(tabs.indexOf(selectedDetailTab(requested, tabs)) >= 0)
            }
        }
    }
}
