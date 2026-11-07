package com.enil.logez.core.data.backup

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup's table lists are checked against the database's own entity list by reflection, so
 * adding a sixteenth table and forgetting the backup fails here rather than silently shipping an
 * archive that drops it on restore.
 *
 * That is not hypothetical: the original spec was written when there were ten tables, and by the
 * time anyone looked again four had been added — two of them holding data nothing could rebuild.
 */
class BackupTablesTest {
    /**
     * Read from the newest committed schema export rather than by reflection: Room's annotations
     * are not retained at runtime, and the exported JSON is generated from them at build time, so
     * it is the authoritative list. Picking the highest version automatically means a future v10
     * is checked without anyone remembering to update this.
     */
    private val schemaTables: Set<String> = run {
        val dir = listOf(
            File("schemas/com.enil.logez.core.data.LogEzDatabase"),
            File("app/schemas/com.enil.logez.core.data.LogEzDatabase"),
        ).firstOrNull { it.isDirectory } ?: error("no exported Room schema found")

        val newest = dir.listFiles { f -> f.extension == "json" }
            ?.maxByOrNull { it.nameWithoutExtension.toInt() }
            ?: error("no schema JSON in $dir")

        Json.parseToJsonElement(newest.readText())
            .jsonObject.getValue("database")
            .jsonObject.getValue("entities")
            .jsonArray
            .map { it.jsonObject.getValue("tableName").jsonPrimitive.content }
            .toSet()
    }

    @Test
    fun `the schema export was actually found, so the guards below mean something`() {
        assertTrue("expected the real table list, got $schemaTables", schemaTables.size >= 15)
        assertTrue(BackupTables.WORKOUT_SETS in schemaTables)
    }

    @Test
    fun `the wipe order covers every table in the database, exactly once`() {
        assertEquals(schemaTables, BackupTables.WIPE_ORDER.toSet())
        assertEquals(BackupTables.WIPE_ORDER.size, BackupTables.WIPE_ORDER.toSet().size)
    }

    @Test
    fun `the export order is every table except the derived cache`() {
        assertEquals(
            schemaTables - BackupTables.PERSONAL_RECORDS,
            BackupTables.EXPORT_ORDER.toSet(),
        )
        assertEquals(BackupTables.EXPORT_ORDER.size, BackupTables.EXPORT_ORDER.toSet().size)
    }

    @Test
    fun `children are wiped before their parents`() {
        fun before(child: String, parent: String) {
            val c = BackupTables.WIPE_ORDER.indexOf(child)
            val p = BackupTables.WIPE_ORDER.indexOf(parent)
            assert(c < p) { "$child must be wiped before $parent (got $c vs $p)" }
        }
        before(BackupTables.WORKOUT_SETS, BackupTables.WORKOUT_EXERCISES)
        before(BackupTables.WORKOUT_EXERCISES, BackupTables.WORKOUTS)
        before(BackupTables.ACTIVITY_TRACKS, BackupTables.WORKOUT_SETS)
        before(BackupTables.WORKOUT_HEART_RATE_SAMPLES, BackupTables.WORKOUTS)
        before(BackupTables.ROUTINE_SETS, BackupTables.ROUTINE_EXERCISES)
        before(BackupTables.ROUTINE_EXERCISES, BackupTables.ROUTINES)
        before(BackupTables.ROUTINES, BackupTables.ROUTINE_FOLDERS)
        // exercises is referenced by both families, so it goes last of all.
        assertEquals(BackupTables.EXERCISES, BackupTables.WIPE_ORDER.last())
    }

    @Test
    fun `parents are inserted before their children`() {
        fun before(parent: String, child: String) {
            val p = BackupTables.EXPORT_ORDER.indexOf(parent)
            val c = BackupTables.EXPORT_ORDER.indexOf(child)
            assert(p < c) { "$parent must be inserted before $child (got $p vs $c)" }
        }
        // exercises is referenced by both families, so it goes first of all.
        assertEquals(BackupTables.EXERCISES, BackupTables.EXPORT_ORDER.first())
        before(BackupTables.ROUTINE_FOLDERS, BackupTables.ROUTINES)
        before(BackupTables.ROUTINES, BackupTables.ROUTINE_EXERCISES)
        before(BackupTables.ROUTINE_EXERCISES, BackupTables.ROUTINE_SETS)
        before(BackupTables.WORKOUTS, BackupTables.WORKOUT_EXERCISES)
        before(BackupTables.WORKOUT_EXERCISES, BackupTables.WORKOUT_SETS)
        before(BackupTables.WORKOUT_SETS, BackupTables.ACTIVITY_TRACKS)
        before(BackupTables.WORKOUTS, BackupTables.WORKOUT_HEART_RATE_SAMPLES)
    }

}
