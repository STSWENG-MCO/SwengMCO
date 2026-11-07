package com.enil.logez.core.data

import com.enil.logez.core.data.backup.BackupWriter
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The backup manifest's schema version is how an older app knows to refuse a newer backup. It was
 * left at 9 when the database moved to v10 (review, 2026-09-26), which would have let an older build
 * restore a v10 backup and silently drop the new column. These pin the three together.
 */
class DatabaseVersionConsistencyTest {
    @Test
    fun `the backup manifest stamps the database's own version`() {
        assertEquals(LogEzDatabase.VERSION, BackupWriter.ROOM_SCHEMA_VERSION)
    }

    @Test
    fun `the newest exported schema file is the current database version`() {
        val dir = File("schemas/com.enil.logez.core.data.LogEzDatabase")
        assertTrue("schema folder not found at ${dir.absolutePath}", dir.isDirectory)
        val newest = dir.listFiles().orEmpty().mapNotNull { it.nameWithoutExtension.toIntOrNull() }.max()
        assertEquals(LogEzDatabase.VERSION, newest)
    }
}
