package com.enil.logez.core.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import com.enil.logez.core.common.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** M20b: the stored file's long edge is capped here so a 12+ MP camera photo lands at a few
 *  hundred KB, not tens of megabytes — Coil's own decode-time downsampling ([LocalImage]) still
 *  applies on top of this at each call site's own smaller display size. */
private const val STORED_LONG_EDGE_TARGET_PX = 1024
private const val STORED_JPEG_QUALITY = 85

@Singleton
class ExerciseMediaStoreImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: AppLogger = AppLogger.NoOp,
) : ExerciseMediaStore {
    override suspend fun copyToAppStorage(uri: Uri): String? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "exercise_media").apply { mkdirs() }
        val relativePath = "exercise_media/${UUID.randomUUID()}.jpg"
        val destination = File(context.filesDir, relativePath)
        runCatching {
            val decoded = decodeDownsampled(uri) ?: return@withContext null
            // M20b re-encodes into a fresh JPEG with no EXIF block of its own, so the source's
            // orientation tag has to be baked into the pixels here or it is lost for good -- the
            // picked-file grant is transient, so there is no second chance to read it once this
            // call returns. AndroidX's ExifInterface since 2026-09-25 (was the platform class, which
            // lint flags for bugs on older releases); CameraX already shipped the same artifact.
            val upright = decoded.withExifOrientation(readExifOrientation(uri))
            try {
                var encoded = false
                destination.outputStream().use { output ->
                    // M20b: re-encoded as JPEG regardless of the source format (the destination
                    // name already promised ".jpg" before this milestone) -- quality 85 is the
                    // standard "visually lossless, meaningfully smaller" compromise. compress()
                    // returns false (rather than throwing) on encoder failure -- checked below so
                    // a bad encode doesn't get treated as a saved photo.
                    encoded = upright.compress(Bitmap.CompressFormat.JPEG, STORED_JPEG_QUALITY, output)
                }
                if (!encoded) {
                    destination.delete()
                    return@withContext null
                }
            } finally {
                // withExifOrientation returns the SAME bitmap for the common (already-upright)
                // case rather than an identity-transformed copy, so recycle each object exactly
                // once -- recycling `decoded` a second time when it and `upright` are identical
                // would double-free.
                if (upright !== decoded) decoded.recycle()
                upright.recycle()
            }
        }.onFailure {
            logger.e(TAG, "copyToAppStorage failed for $uri", it)
            destination.delete()
            return@withContext null
        }
        relativePath
    }

    private fun decodeDownsampled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // decodeStream(..., inJustDecodeBounds = true) always returns null by contract -- it only
        // has the side effect of populating bounds.outWidth/outHeight -- so the stream's own
        // nullability must be checked separately, never via the decode call's return value.
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        val options = BitmapFactory.Options().apply {
            inSampleSize = inSampleSizeFor(bounds.outWidth, bounds.outHeight, STORED_LONG_EDGE_TARGET_PX)
        }
        val decodeStream = context.contentResolver.openInputStream(uri) ?: return null
        return decodeStream.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun readExifOrientation(uri: Uri): Int {
        val stream = context.contentResolver.openInputStream(uri) ?: return ExifInterface.ORIENTATION_NORMAL
        return stream.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    }

    private companion object {
        private const val TAG = "ExerciseMediaStoreImpl"
    }
}

/**
 * Bakes an EXIF orientation tag into the pixels themselves, since [ExerciseMediaStoreImpl] discards
 * EXIF when it re-encodes. Returns `this` unrotated for `ORIENTATION_NORMAL`/`ORIENTATION_UNDEFINED`
 * (the common case) instead of allocating a redundant identity-transformed copy; every other branch
 * always returns a genuinely new [Bitmap] ([Bitmap.createBitmap] with a non-identity matrix cannot
 * take AOSP's early-return-the-source path), which is what lets the caller recycle both bitmaps
 * safely with a single `!==` check.
 */
internal fun Bitmap.withExifOrientation(orientation: Int): Bitmap {
    if (orientation == ExifInterface.ORIENTATION_NORMAL || orientation == ExifInterface.ORIENTATION_UNDEFINED) return this
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(-90f)
        }
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
