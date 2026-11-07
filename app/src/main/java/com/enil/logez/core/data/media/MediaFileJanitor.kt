package com.enil.logez.core.data.media

import android.content.Context
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.backup.BackupWriter
import com.enil.logez.core.data.backup.RestoreMarker
import com.enil.logez.core.data.dao.BackupDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Deletes a media file once no row points at it. The seam ViewModels depend on, so their tests
 * can record calls instead of touching a real filesystem.
 */
interface MediaFileCleaner {
    /**
     * Deletes [relativePath] (relative to filesDir, as every photo row stores it) unless a
     * progress photo or exercise still references it. Call it after the row change has committed.
     */
    suspend fun deleteIfUnreferenced(relativePath: String)

    companion object {
        val NoOp: MediaFileCleaner = object : MediaFileCleaner {
            override suspend fun deleteIfUnreferenced(relativePath: String) = Unit
        }
    }
}

/**
 * Keeps `progress_photos/` and `exercise_media/` in step with the rows that reference them
 * (Play-readiness audit, 2026-09-25). Deleting or replacing a progress photo used to remove only
 * its database row, so the JPEG stayed on disk forever and every backup zipped it up again: a body
 * photo the user had deleted still left the device inside each backup they shared.
 *
 * Two entry points:
 * - [deleteIfUnreferenced] runs right after a delete or replace, for the one file involved.
 * - [sweepOrphans] runs once per launch and clears files left behind by older builds, by an image
 *   picked in the exercise editor and then abandoned, or by a crash between a copy and its insert.
 *
 * Both re-read the references from the database rather than trusting the caller, so a file two
 * rows share is never deleted while one still uses it.
 */
@Singleton
class MediaFileJanitor(
    private val filesDir: File,
    private val referencedPaths: suspend () -> Set<String>,
    private val nowMillis: () -> Long,
    private val logger: AppLogger,
    /** Where the camera screen writes its temporary full-size captures; null skips that sweep. */
    private val cacheDir: File? = null,
) : MediaFileCleaner {
    @Inject constructor(
        @ApplicationContext context: Context,
        backupDao: BackupDao,
        logger: AppLogger,
    ) : this(context.filesDir, { backupDao.referencedMediaPaths().toSet() }, System::currentTimeMillis, logger, context.cacheDir)

    override suspend fun deleteIfUnreferenced(relativePath: String) {
        withContext(Dispatchers.IO) {
            val file = resolveMediaFile(relativePath) ?: return@withContext
            if (relativePath in referencedPaths()) return@withContext
            if (file.isFile && !file.delete()) logger.e(TAG, "Could not delete $relativePath", null)
        }
    }

    /**
     * Deletes every media file no row references, except files younger than [GRACE_MILLIS]: a
     * photo is written to disk before its row is inserted, and a sweep landing in that window must
     * not delete a capture that is still being saved. Does nothing while a restore is mid-way,
     * because its staged rows and files are not live yet. Returns how many files it deleted.
     */
    suspend fun sweepOrphans(): Int = withContext(Dispatchers.IO) {
        if (RestoreMarker(filesDir).read() != null) return@withContext 0
        val referenced = referencedPaths()
        val cutoff = nowMillis() - GRACE_MILLIS
        var deleted = 0
        BackupWriter.mediaDirectories(filesDir).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                val relative = "${dir.name}/${file.name}"
                if (file.isFile && relative !in referenced && file.lastModified() < cutoff && file.delete()) deleted++
            }
        }
        // Full-size camera captures wait in the cache until they are copied or discarded; a process
        // death during the "replace today's photo?" dialog orphaned them there.
        cacheDir?.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith(CAMERA_CAPTURE_PREFIX) && file.lastModified() < cutoff && file.delete()) deleted++
        }
        if (deleted > 0) logger.e(TAG, "Removed $deleted unreferenced media file(s)", null)
        deleted
    }

    /** Null for anything that is not a plain file name inside one of the two media directories. */
    private fun resolveMediaFile(relativePath: String): File? {
        val parts = relativePath.split('/')
        if (parts.size != 2) return null
        val (dirName, fileName) = parts
        if (dirName !in MEDIA_DIRS || fileName.isEmpty() || fileName == "." || fileName == "..") return null
        return File(File(filesDir, dirName), fileName)
    }

    companion object {
        private const val TAG = "MediaFileJanitor"
        private val MEDIA_DIRS = setOf(BackupWriter.PROGRESS_PHOTOS_DIR, BackupWriter.EXERCISE_MEDIA_DIR)

        /** Must match the temp-file name CameraCaptureScreen writes. */
        const val CAMERA_CAPTURE_PREFIX = "progress_photo_capture_"

        /** An hour comfortably covers a capture, its copy and its insert, even on a slow phone. */
        const val GRACE_MILLIS = 60 * 60 * 1000L
    }
}
