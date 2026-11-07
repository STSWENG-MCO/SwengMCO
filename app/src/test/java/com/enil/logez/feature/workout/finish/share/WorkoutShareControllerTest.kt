package com.enil.logez.feature.workout.finish.share

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Export contract only. The GraphicsLayer capture itself isn't exercised here — recording and
 * rasterizing a composition's layer under Robolectric is not reliable — so the tests feed the
 * controller a synthetic bitmap instead (the controller is agnostic to its dimensions).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutShareControllerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controller = WorkoutShareController(context)
    private val sharedImagesDir = File(context.cacheDir, "shared_images")

    // One test method on purpose: FileProvider caches its path strategy in a static map keyed by
    // authority, while Robolectric gives every test method a fresh temp dataDir — a second method
    // calling exportPng would resolve files against the first method's (now stale) cached root
    // and throw. Both halves of the contract therefore run as phases of the same export sequence.
    @Test
    fun `export writes a decodable 1080px png behind a content uri and prunes only stale prior exports`() = runTest {
        val bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF39FF6E.toInt()) }

        val uri = controller.exportPng(bitmap, ShareCardFormat.STORY)

        assertEquals("content", uri.scheme)
        assertEquals("com.enil.logez.fileprovider", uri.authority)
        val firstFile = sharedImagesDir.listFiles()!!.single()
        val signature = firstFile.inputStream().use { it.readNBytes(8) }
        assertArrayEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
            signature,
        )
        val decoded = BitmapFactory.decodeFile(firstFile.path)!!
        assertEquals(1080, decoded.width)
        assertEquals(1080, decoded.height)

        // Second export: the fresh prior export survives (a share target may still hold a lazy
        // read grant on it), while one past the grace window is pruned.
        val stale = File(sharedImagesDir, "logez_workout_story_0.png").apply {
            writeBytes(byteArrayOf(1))
            assertTrue(setLastModified(1L))
        }
        controller.exportPng(bitmap, ShareCardFormat.STORY)
        val names = sharedImagesDir.listFiles()!!.map { it.name }
        assertEquals(2, names.size)
        assertTrue(names.contains(firstFile.name))
        assertTrue(names.any { it.startsWith("logez_workout_story_") })
        assertFalse(names.contains(stale.name))
    }

    /**
     * Q+ save contract. Robolectric ships no real MediaStore provider: ShadowContentResolver
     * records insert/update statements, hands out sequential row ids, and only streams through
     * pre-registered sinks. So the "resolver can re-open and decode it" leg runs against the
     * registered sink's bytes (exactly what the controller wrote through openOutputStream) rather
     * than a true provider round trip, and DISPLAY_NAME/RELATIVE_PATH placement is asserted on
     * the recorded ContentValues rather than on disk. saveToPictures itself doesn't touch
     * FileProvider, so a separate method is safe despite the static-cache note above.
     */
    @Test
    fun `saveToPictures inserts a pending mediastore image, writes the png, then publishes the row`() = runTest {
        val bitmap = Bitmap.createBitmap(1080, 1080, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF39FF6E.toInt()) }
        val resolver = shadowOf(context.contentResolver)
        // ShadowContentResolver's first providerless insert deterministically returns row id 1.
        val expectedUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, 1)
        val sink = ByteArrayOutputStream()
        resolver.registerOutputStream(expectedUri, sink)

        val uri = controller.saveToPictures(bitmap, ShareCardFormat.STORY)

        assertEquals("content", uri.scheme)
        assertEquals(expectedUri, uri)

        val inserted = resolver.insertStatements.single().contentValues
        assertTrue(
            inserted.getAsString(MediaStore.Images.Media.DISPLAY_NAME).matches(Regex("logez_workout_story_\\d+\\.png")),
        )
        assertEquals("image/png", inserted.getAsString(MediaStore.Images.Media.MIME_TYPE))
        assertEquals("Pictures/logEZ", inserted.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
        assertEquals(1, inserted.getAsInteger(MediaStore.Images.Media.IS_PENDING))

        val update = resolver.updateStatements.single()
        assertEquals(uri, update.uri)
        assertEquals(0, update.contentValues.getAsInteger(MediaStore.Images.Media.IS_PENDING))

        val decoded = BitmapFactory.decodeByteArray(sink.toByteArray(), 0, sink.size())!!
        assertEquals(1080, decoded.width)
        assertEquals(1080, decoded.height)
    }

    /** Legacy branch (API 26-28): direct public-file write plus a MediaStore index row. */
    @Test
    @Config(sdk = [28])
    fun `saveToPictures on api 28 writes the public file and indexes it in mediastore`() = runTest {
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFF39FF6E.toInt()) }

        val uri = controller.saveToPictures(bitmap, ShareCardFormat.STORY)

        assertEquals("content", uri.scheme)
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "logEZ")
        val file = dir.listFiles()!!.single()
        assertTrue(file.name.matches(Regex("logez_workout_story_\\d+\\.png")))
        val decoded = BitmapFactory.decodeFile(file.path)!!
        assertEquals(1080, decoded.width)
        assertEquals(1920, decoded.height)
        @Suppress("DEPRECATION")
        val indexed = shadowOf(context.contentResolver).insertStatements.single().contentValues
        assertEquals(file.absolutePath, indexed.getAsString(MediaStore.Images.Media.DATA))
    }
}
