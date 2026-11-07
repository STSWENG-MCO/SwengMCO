package com.enil.logez.core.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

/** Same storage discipline as [ExerciseMediaStoreImpl] — capped long edge, JPEG quality, EXIF
 *  baked into pixels before re-encoding — just a different directory. */
private const val STORED_LONG_EDGE_TARGET_PX = 1024
private const val STORED_JPEG_QUALITY = 85

@Singleton
class ProgressPhotoStoreImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val logger: AppLogger = AppLogger.NoOp,
) : ProgressPhotoStore {
    override suspend fun copyToAppStorage(uri: Uri): String? = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "progress_photos").apply { mkdirs() }
        val relativePath = "progress_photos/${UUID.randomUUID()}.jpg"
        val destination = File(context.filesDir, relativePath)
        runCatching {
            val decoded = decodeDownsampled(uri) ?: return@withContext null
            val upright = decoded.withExifOrientation(readExifOrientation(uri))
            try {
                var encoded = false
                destination.outputStream().use { output ->
                    encoded = upright.compress(Bitmap.CompressFormat.JPEG, STORED_JPEG_QUALITY, output)
                }
                if (!encoded) {
                    destination.delete()
                    return@withContext null
                }
            } finally {
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
        private const val TAG = "ProgressPhotoStoreImpl"
    }
}
