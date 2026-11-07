package com.enil.logez.core.data.media

import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.backup.RestoreMarker
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaFileJanitorTest {
    @get:Rule val temp = TemporaryFolder()

    private val now = 10_000_000_000L
    private val old = now - MediaFileJanitor.GRACE_MILLIS - 1
    private val fresh = now - 1_000

    private fun file(relative: String, modified: Long): File =
        File(temp.root, relative).apply {
            parentFile!!.mkdirs()
            writeText("jpeg")
            setLastModified(modified)
        }

    private fun janitor(referenced: Set<String>) =
        MediaFileJanitor(temp.root, { referenced }, { now }, AppLogger.NoOp)

    @Test
    fun `the sweep deletes old unreferenced files and keeps referenced ones`() = runTest {
        val kept = file("progress_photos/kept.jpg", old)
        val orphan = file("progress_photos/orphan.jpg", old)
        val keptMedia = file("exercise_media/kept.jpg", old)
        val orphanMedia = file("exercise_media/orphan.jpg", old)

        val deleted = janitor(setOf("progress_photos/kept.jpg", "exercise_media/kept.jpg")).sweepOrphans()

        assertEquals(2, deleted)
        assertTrue(kept.exists())
        assertTrue(keptMedia.exists())
        assertFalse(orphan.exists())
        assertFalse(orphanMedia.exists())
    }

    /** A photo is written before its row is inserted; a sweep in between must not eat it. */
    @Test
    fun `the sweep spares a recent unreferenced file`() = runTest {
        val inFlight = file("progress_photos/capturing.jpg", fresh)

        assertEquals(0, janitor(emptySet()).sweepOrphans())
        assertTrue(inFlight.exists())
    }

    @Test
    fun `the sweep does nothing while a restore is mid-way`() = runTest {
        val orphan = file("progress_photos/orphan.jpg", old)
        RestoreMarker(temp.root).write(RestoreMarker.Phase.MEDIA, File(temp.root, "restore_staging"))

        assertEquals(0, janitor(emptySet()).sweepOrphans())
        assertTrue(orphan.exists())
    }

    @Test
    fun `deleteIfUnreferenced removes the file once no row points at it`() = runTest {
        val photo = file("progress_photos/gone.jpg", fresh)

        janitor(emptySet()).deleteIfUnreferenced("progress_photos/gone.jpg")

        assertFalse(photo.exists())
    }

    @Test
    fun `deleteIfUnreferenced keeps a file another row still references`() = runTest {
        val shared = file("exercise_media/shared.jpg", fresh)

        janitor(setOf("exercise_media/shared.jpg")).deleteIfUnreferenced("exercise_media/shared.jpg")

        assertTrue(shared.exists())
    }

    @Test
    fun `deleteIfUnreferenced refuses anything outside the two media directories`() = runTest {
        val database = file("databases/logez.db", fresh)
        val outside = file("secret.txt", fresh)

        val janitor = janitor(emptySet())
        janitor.deleteIfUnreferenced("databases/logez.db")
        janitor.deleteIfUnreferenced("progress_photos/../secret.txt")
        janitor.deleteIfUnreferenced("../secret.txt")

        assertTrue(database.exists())
        assertTrue(outside.exists())
    }

    @Test
    fun `the sweep clears stale camera captures from the cache but not fresh ones`() = runTest {
        val cache = temp.newFolder("cache")
        val stale = File(cache, "${MediaFileJanitor.CAMERA_CAPTURE_PREFIX}old.jpg").apply { writeText("x"); setLastModified(old) }
        val inUse = File(cache, "${MediaFileJanitor.CAMERA_CAPTURE_PREFIX}new.jpg").apply { writeText("x"); setLastModified(fresh) }
        val other = File(cache, "shared_images.png").apply { writeText("x"); setLastModified(old) }

        MediaFileJanitor(temp.root, { emptySet() }, { now }, AppLogger.NoOp, cacheDir = cache).sweepOrphans()

        assertFalse(stale.exists())
        assertTrue(inUse.exists())
        assertTrue("only camera captures are swept from the cache", other.exists())
    }
}
