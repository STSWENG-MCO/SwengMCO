package com.enil.logez.core.domain

/**
 * The one way a drag-reorder drop is applied to an in-memory list.
 *
 * Returns this list permuted so the items whose [id] appears in [orderedIds] come first, in that
 * order, followed by every item [orderedIds] does not name, in their existing relative order. Ids
 * that match nothing are ignored. The append rule is deliberate: the id list originates from a
 * screen-side optimistic copy of the list, so if that copy is ever stale or partial the worst case
 * is a lost move — never a silently dropped exercise (`mapNotNull` alone would delete it).
 */
fun <T> List<T>.reorderedBy(orderedIds: List<String>, id: (T) -> String): List<T> {
    val byId = associateBy(id)
    val named = orderedIds.distinct().mapNotNull { byId[it] }
    val namedIds = named.mapTo(HashSet()) { id(it) }
    return named + filter { id(it) !in namedIds }
}
