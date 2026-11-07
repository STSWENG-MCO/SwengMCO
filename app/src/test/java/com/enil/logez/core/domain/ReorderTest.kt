package com.enil.logez.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M20a: `reorderedBy` is the pure step behind every drag-reorder commit (routine builder and
 * logger exercises). It must never silently delete an item the caller's id list omits -- that id
 * list comes from a screen-side optimistic copy of the list, which can be stale or a partial
 * snapshot taken mid-drag.
 */
class ReorderTest {
    private data class Item(val id: String)

    private fun reorder(items: List<String>, orderedIds: List<String>) =
        items.map { Item(it) }.reorderedBy(orderedIds) { it.id }.map { it.id }

    @Test
    fun `a full id list permutes the items in that order`() {
        assertEquals(listOf("c", "a", "b"), reorder(listOf("a", "b", "c"), listOf("c", "a", "b")))
    }

    @Test
    fun `ids the caller omits are appended, in their original relative order`() {
        assertEquals(listOf("b", "a", "c", "d"), reorder(listOf("a", "b", "c", "d"), listOf("b", "a")))
    }

    @Test
    fun `an empty id list is a no-op`() {
        assertEquals(listOf("a", "b", "c"), reorder(listOf("a", "b", "c"), emptyList()))
    }

    @Test
    fun `ids naming nothing in the list are ignored`() {
        assertEquals(listOf("b", "a", "c"), reorder(listOf("a", "b", "c"), listOf("b", "a", "ghost")))
    }

    @Test
    fun `a duplicated id in the caller's list is applied once`() {
        assertEquals(listOf("b", "a", "c"), reorder(listOf("a", "b", "c"), listOf("b", "b", "a")))
    }
}
