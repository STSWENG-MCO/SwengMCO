package com.enil.logez.feature.routines

import com.enil.logez.core.data.entity.RoutineEntity
import com.enil.logez.core.data.entity.RoutineFolderEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * M20a: `reorderTabRows` is the pure hover-swap step behind the Workout tab's drag reorder. It must
 * permute exactly one bucket (folders, one folder's routines, or root routines) and refuse every
 * cross-bucket move, because the DAO writers stamp `orderIndex` for exactly the ids they are handed.
 */
class WorkoutTabReorderTest {
    private fun folder(id: String) = RoutineFolderEntity(id = id, name = id, orderIndex = 0, createdAt = 0, updatedAt = 0)
    private fun routine(id: String, folderId: String?) =
        RoutineCardModel(RoutineEntity(id = id, folderId = folderId, name = id, notes = null, orderIndex = 0, createdAt = 0, updatedAt = 0), exercisePreview = "")

    private fun fixture(): Pair<MutableList<FolderSection>, MutableList<RoutineCardModel>> {
        val folders = mutableListOf(
            FolderSection(folder("fA"), listOf(routine("a1", "fA"), routine("a2", "fA"))),
            FolderSection(folder("fB"), listOf(routine("b1", "fB"))),
        )
        val root = mutableListOf(routine("r1", null), routine("r2", null), routine("r3", null))
        return folders to root
    }

    @Test
    fun `a folder dragged over another folder takes its position`() {
        val (folders, root) = fixture()
        reorderTabRows(folders, root, fromKey = "folder:fA", toKey = "folder:fB")
        assertEquals(listOf("fB", "fA"), folders.map { it.folder.id })
    }

    @Test
    fun `a folder dragged over another folder's routine targets that folder`() {
        val (folders, root) = fixture()
        reorderTabRows(folders, root, fromKey = "folder:fA", toKey = "routine:b1")
        assertEquals(listOf("fB", "fA"), folders.map { it.folder.id })
    }

    @Test
    fun `a folder dragged over its own routine is a no-op`() {
        val (folders, root) = fixture()
        reorderTabRows(folders, root, fromKey = "folder:fA", toKey = "routine:a2")
        assertEquals(listOf("fA", "fB"), folders.map { it.folder.id })
    }

    @Test
    fun `routines reorder within their own folder only`() {
        val (folders, root) = fixture()
        reorderTabRows(folders, root, fromKey = "routine:a2", toKey = "routine:a1")
        assertEquals(listOf("a2", "a1"), folders[0].routines.map { it.routine.id })
        assertEquals(listOf("b1"), folders[1].routines.map { it.routine.id })
    }

    @Test
    fun `a routine dragged onto another folder's routine is refused`() {
        val (folders, root) = fixture()
        reorderTabRows(folders, root, fromKey = "routine:a1", toKey = "routine:b1")
        assertEquals(listOf("a1", "a2"), folders[0].routines.map { it.routine.id })
        assertEquals(listOf("b1"), folders[1].routines.map { it.routine.id })
    }

    @Test
    fun `root routines reorder among themselves`() {
        val (folders, root) = fixture()
        reorderTabRows(folders, root, fromKey = "root:r3", toKey = "root:r1")
        assertEquals(listOf("r3", "r1", "r2"), root.map { it.routine.id })
    }

    @Test
    fun `cross-bucket moves between root routines and folder rows are refused`() {
        val (folders, root) = fixture()
        reorderTabRows(folders, root, fromKey = "root:r1", toKey = "folder:fA")
        reorderTabRows(folders, root, fromKey = "root:r1", toKey = "routine:a1")
        reorderTabRows(folders, root, fromKey = "routine:a1", toKey = "root:r1")
        assertEquals(listOf("r1", "r2", "r3"), root.map { it.routine.id })
        assertEquals(listOf("a1", "a2"), folders[0].routines.map { it.routine.id })
        assertEquals(listOf("fA", "fB"), folders.map { it.folder.id })
    }

    @Test
    fun `unkeyed or malformed keys are ignored`() {
        val (folders, root) = fixture()
        reorderTabRows(folders, root, fromKey = "root-header", toKey = "root:r1")
        reorderTabRows(folders, root, fromKey = "root:r1", toKey = "root-header")
        assertEquals(listOf("r1", "r2", "r3"), root.map { it.routine.id })
    }

    @Test
    fun `a folder dragged onto a root routine or the root header is refused`() {
        val (folders, root) = fixture()
        reorderTabRows(folders, root, fromKey = "folder:fA", toKey = "root:r1")
        reorderTabRows(folders, root, fromKey = "folder:fB", toKey = "root-header")
        assertEquals(listOf("fA", "fB"), folders.map { it.folder.id })
        assertEquals(listOf("r1", "r2", "r3"), root.map { it.routine.id })
    }

    // --- Three-folder fixture: distinguishes a true move-to-index from an adjacent swap, and pins
    // moving a folder or a routine past more than one neighbour in one drop.

    private fun threeFolderFixture(): MutableList<FolderSection> = mutableListOf(
        FolderSection(folder("fA"), listOf(routine("a1", "fA"), routine("a2", "fA"), routine("a3", "fA"))),
        FolderSection(folder("fB"), listOf(routine("b1", "fB"))),
        FolderSection(folder("fC"), listOf(routine("c1", "fC"))),
    )

    @Test
    fun `a folder dragged past two neighbours lands at that index, not merely swapped with one`() {
        val folders = threeFolderFixture()
        val root = mutableListOf<RoutineCardModel>()
        // fA -> fC's position: a plain adjacent swap (fA<->fB, keeping fC put) would wrongly give
        // [fB, fA, fC]; a correct move-to-index gives fA landing where fC was.
        reorderTabRows(folders, root, fromKey = "folder:fA", toKey = "folder:fC")
        assertEquals(listOf("fB", "fC", "fA"), folders.map { it.folder.id })
    }

    @Test
    fun `a folder dragged onto a non-adjacent folder's routine targets that folder, not the routine's neighbour`() {
        val folders = threeFolderFixture()
        val root = mutableListOf<RoutineCardModel>()
        reorderTabRows(folders, root, fromKey = "folder:fA", toKey = "routine:c1")
        assertEquals(listOf("fB", "fC", "fA"), folders.map { it.folder.id })
    }

    @Test
    fun `a routine dragged past two siblings lands at that index within its own folder only`() {
        val folders = threeFolderFixture()
        val root = mutableListOf<RoutineCardModel>()
        reorderTabRows(folders, root, fromKey = "routine:a1", toKey = "routine:a3")
        assertEquals(listOf("a2", "a3", "a1"), folders[0].routines.map { it.routine.id })
        assertEquals(listOf("b1"), folders[1].routines.map { it.routine.id })
        assertEquals(listOf("c1"), folders[2].routines.map { it.routine.id })
    }
}
