package com.enil.logez.core.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Keeps a screen's optimistic reorder list in step with the ViewModel's list.
 *
 * `sh.calvin.reorderable` calls `onMove` on every hover swap and expects the list it renders to
 * already reflect the move, so each reorderable screen renders a `mutableStateListOf` copy that
 * absorbs swaps synchronously and tells the ViewModel once, from the handle's `onDragStopped`.
 * That copy must be **one instance for the screen's lifetime** — the library's `pointerInput`
 * block is keyed on `(state, enabled)` only, so the dragged row keeps running with whatever lambda
 * it was launched with; re-creating the list per emission (`remember(source) { ... }`) left a
 * second drag of the same row committing a dead list's contents. This helper does the syncing:
 *
 * - every new [source] emission replaces [local]'s contents — unless a drag is in flight, in
 *   which case it is parked and applied when the drag ends (a mid-drag re-seed would throw away
 *   the swaps made so far);
 * - a drag ending with nothing parked syncs nothing: the drop already committed [local]'s order
 *   and the ViewModel's own emission of that order arrives next. Syncing from the pre-commit
 *   [source] at that instant would snap the row back for a frame.
 *
 * Seed [local] once with `remember { mutableStateListOf<T>().apply { addAll(source) } }` before
 * calling this, so the first frame is never empty.
 */
@Composable
fun <T> SyncOptimisticList(local: SnapshotStateList<T>, source: List<T>, isDragging: Boolean) {
    var parked by remember { mutableStateOf<List<T>?>(null) }
    LaunchedEffect(source) {
        if (isDragging) parked = source else local.replaceWith(source)
    }
    LaunchedEffect(isDragging) {
        if (!isDragging) parked?.let { local.replaceWith(it); parked = null }
    }
}

private fun <T> SnapshotStateList<T>.replaceWith(items: List<T>) {
    if (this.toList() == items) return
    clear()
    addAll(items)
}
