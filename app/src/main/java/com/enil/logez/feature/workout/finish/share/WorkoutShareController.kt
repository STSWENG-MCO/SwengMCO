package com.enil.logez.feature.workout.finish.share

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import com.enil.logez.core.common.AppLogger
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.io.IOException
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Platform side-effect component for the summary-card share flow ([com.enil.logez.feature.workout.audio.WorkoutAudioPlayer]
 * precedent: never injected into a ViewModel — the screen captures the card and hands the bitmap
 * straight here). Local-only posture holds: the PNG lands in this app's own cache and leaves the
 * device only through the share target the user picks in the system chooser — or, via
 * [saveToPictures], stays on the device in the user's own Pictures library.
 */
@Singleton
class WorkoutShareController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger = AppLogger.NoOp,
) {
    /**
     * PNG-encodes [bitmap] into `cacheDir/shared_images/` under a unique per-export filename and
     * returns a grantable content Uri for it. Prior exports are NOT deleted immediately: a share
     * target picked in the chooser may read its granted Uri lazily (an email draft attaches at
     * send time), and deleting or overwriting the file behind it would break that read. Instead,
     * exports older than [STALE_EXPORT_MAX_AGE_MS] are pruned here — and this all lives in the
     * cache dir, which the OS may purge on its own regardless.
     */
    suspend fun exportPng(bitmap: Bitmap, format: ShareCardFormat): Uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARED_IMAGES_DIR)
        dir.mkdirs()
        val cutoff = System.currentTimeMillis() - STALE_EXPORT_MAX_AGE_MS
        dir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
        val file = File(dir, exportFileName(format))
        file.outputStream().use { out ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("PNG encoding failed")
        }
        FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)
    }

    /**
     * Saves [bitmap] as a PNG into the device's shared Pictures library under `Pictures/logEZ/`
     * and returns the MediaStore item Uri. Q+ uses a scoped MediaStore insert (row held at
     * IS_PENDING while the bytes stream in, so gallery apps never see a half-written image);
     * API 26–28 writes the public file directly and indexes it — that branch needs
     * WRITE_EXTERNAL_STORAGE, which the caller must already hold. Every failure throws after
     * rolling back the partial row/file — never a silent no-op success.
     */
    suspend fun saveToPictures(bitmap: Bitmap, format: ShareCardFormat): Uri = withContext(Dispatchers.IO) {
        val displayName = exportFileName(format)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            insertIntoMediaStore(displayName, bitmap)
        } else {
            writeToLegacyPicturesDir(displayName, bitmap)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertIntoMediaStore(displayName: String, bitmap: Bitmap): Uri {
        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE_PNG)
            put(MediaStore.Images.Media.RELATIVE_PATH, PICTURES_RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pending)
            ?: throw IOException("MediaStore insert returned no row")
        try {
            val stream = resolver.openOutputStream(uri) ?: throw IOException("MediaStore row not writable")
            stream.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("PNG encoding failed")
            }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            return uri
        } catch (e: Exception) {
            // A row stuck at IS_PENDING=1 would linger invisibly in the library forever.
            logger.e(TAG, "MediaStore export failed for $displayName; deleting the pending row", e)
            resolver.delete(uri, null, null)
            throw e
        }
    }

    /**
     * Pre-Q: MediaStore can't stream into shared storage on the app's behalf, so write the public
     * file directly and insert an index row so gallery apps pick it up without waiting for a scan.
     */
    private fun writeToLegacyPicturesDir(displayName: String, bitmap: Bitmap): Uri {
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), SAVE_SUBDIR)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Couldn't create ${dir.path}")
        val file = File(dir, displayName)
        try {
            file.outputStream().use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IOException("PNG encoding failed")
            }
            @Suppress("DEPRECATION")
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, MIME_TYPE_PNG)
                put(MediaStore.Images.Media.DATA, file.absolutePath)
            }
            return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("MediaStore insert returned no row")
        } catch (e: Exception) {
            logger.e(TAG, "Legacy Pictures-dir export failed for $displayName; deleting the partial file", e)
            file.delete()
            throw e
        }
    }

    private fun exportFileName(format: ShareCardFormat) =
        "logez_workout_${format.name.lowercase(Locale.US)}_${System.currentTimeMillis()}.png"

    /**
     * Generic ACTION_SEND chooser only — no Instagram story intent (that path requires a Meta app
     * registration). `clipData` mirrors the stream extra so targets can render a thumbnail;
     * [launchContext] is the activity context, since a chooser launched from the app UI should
     * stay in its task.
     */
    fun launchShareChooser(launchContext: Context, uri: Uri) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE_PNG
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).apply {
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launchContext.startActivity(chooser)
    }

    companion object {
        private const val TAG = "WorkoutShareController"
        // Derived from the package name (2026-09-25) so the staging build, installed beside the
        // release one under its own application id, does not collide on a hardcoded authority.
        // Must match the manifest's ${applicationId}.fileprovider.
        private const val AUTHORITY_SUFFIX = ".fileprovider"
        private const val SHARED_IMAGES_DIR = "shared_images"
        private const val MIME_TYPE_PNG = "image/png"
        private const val SAVE_SUBDIR = "logEZ"
        private const val PICTURES_RELATIVE_PATH = "Pictures/$SAVE_SUBDIR"

        /** Grace window before a prior export's file may be pruned (see [exportPng]). */
        private const val STALE_EXPORT_MAX_AGE_MS = 24L * 60 * 60 * 1000
    }
}

/** Composable access without routing an android.graphics type through the ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkoutShareEntryPoint {
    fun workoutShareController(): WorkoutShareController
    fun appLogger(): AppLogger
}
