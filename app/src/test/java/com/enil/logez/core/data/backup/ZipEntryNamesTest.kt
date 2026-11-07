package com.enil.logez.core.data.backup

import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Archive contents are untrusted input; these are the checks that keep them inside the sandbox. */
class ZipEntryNamesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun staging(): File = temp.newFolder("restore_staging")

    @Test
    fun `an ordinary table entry resolves inside the staging directory`() {
        val dir = staging()
        val resolved = ZipEntryNames.resolveSafely(dir, "tables/workouts.jsonl")
        assertTrue(resolved.canonicalPath.startsWith(dir.canonicalPath + File.separator))
    }

    @Test
    fun `a media entry resolves inside the staging directory`() {
        val dir = staging()
        val resolved = ZipEntryNames.resolveSafely(dir, "media/progress_photos/abc.jpg")
        assertTrue(resolved.canonicalPath.startsWith(dir.canonicalPath + File.separator))
    }

    @Test
    fun `a parent-directory escape is rejected`() {
        val dir = staging()
        assertThrows(ZipEntryNames.UnsafeEntryException::class.java) {
            ZipEntryNames.resolveSafely(dir, "../evil.txt")
        }
    }

    @Test
    fun `an escape aimed at the live database is rejected`() {
        val dir = staging()
        // The case that motivates all of this: overwriting the database mid-restore.
        assertThrows(ZipEntryNames.UnsafeEntryException::class.java) {
            ZipEntryNames.resolveSafely(dir, "../../databases/logez.db")
        }
    }

    @Test
    fun `an escape buried mid-path is rejected`() {
        val dir = staging()
        assertThrows(ZipEntryNames.UnsafeEntryException::class.java) {
            ZipEntryNames.resolveSafely(dir, "tables/../../../etc/passwd")
        }
    }

    @Test
    fun `an absolute path is rejected`() {
        val dir = staging()
        assertThrows(ZipEntryNames.UnsafeEntryException::class.java) {
            ZipEntryNames.resolveSafely(dir, "/etc/passwd")
        }
    }

    @Test
    fun `a backslash-separated path is rejected`() {
        val dir = staging()
        assertThrows(ZipEntryNames.UnsafeEntryException::class.java) {
            ZipEntryNames.resolveSafely(dir, "tables\\..\\..\\evil.txt")
        }
    }

    @Test
    fun `a blank name is rejected`() {
        val dir = staging()
        assertThrows(ZipEntryNames.UnsafeEntryException::class.java) {
            ZipEntryNames.resolveSafely(dir, "   ")
        }
    }

    @Test
    fun `a sibling directory sharing the staging prefix is rejected`() {
        val dir = staging()
        // "restore_staging_evil" starts with "restore_staging" as a string but is not inside it.
        assertThrows(ZipEntryNames.UnsafeEntryException::class.java) {
            ZipEntryNames.resolveSafely(dir, "../restore_staging_evil/x.txt")
        }
    }

    @Test
    fun `too many entries is rejected`() {
        assertThrows(ZipEntryNames.ArchiveTooLargeException::class.java) {
            ZipEntryNames.checkEntryCount(ZipEntryNames.MAX_ENTRIES + 1)
        }
        ZipEntryNames.checkEntryCount(ZipEntryNames.MAX_ENTRIES)
    }

    @Test
    fun `too many uncompressed bytes is rejected`() {
        assertThrows(ZipEntryNames.ArchiveTooLargeException::class.java) {
            ZipEntryNames.checkTotalBytes(ZipEntryNames.MAX_TOTAL_UNCOMPRESSED_BYTES + 1)
        }
        ZipEntryNames.checkTotalBytes(ZipEntryNames.MAX_TOTAL_UNCOMPRESSED_BYTES)
    }
}
