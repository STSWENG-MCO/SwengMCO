package com.enil.logez.core.data.backup

import java.io.File

/**
 * Validation for entry names read out of a backup archive.
 *
 * The archive is a file the app did not write — the user picks it, and it may have been edited,
 * corrupted, or built by something else entirely. An entry named `../../databases/logez.db` would
 * otherwise be extracted straight over the live database, so every name is resolved and checked to
 * land inside the staging directory before a single byte is written.
 *
 * The count and size caps are the other half of the same idea: an archive that expands to
 * something enormous should fail rather than fill the device.
 */
object ZipEntryNames {
    const val MAX_ENTRIES = 50_000
    const val MAX_TOTAL_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB

    class UnsafeEntryException(name: String) :
        IllegalArgumentException("Backup contains an unsafe file path: $name")

    class ArchiveTooLargeException(message: String) : IllegalArgumentException(message)

    /**
     * Resolves [entryName] inside [stagingDir], or throws if it would escape.
     *
     * Checked on the canonical paths rather than by looking for `..` in the string: a name can
     * escape through a symlink or an unusual encoding without containing the obvious marker.
     */
    fun resolveSafely(stagingDir: File, entryName: String): File {
        if (entryName.isBlank()) throw UnsafeEntryException(entryName)
        if (entryName.startsWith("/") || entryName.contains("\\")) throw UnsafeEntryException(entryName)

        val target = File(stagingDir, entryName)
        val root = stagingDir.canonicalPath
        val resolved = target.canonicalPath
        // The separator matters: without it, a sibling directory whose name merely starts with the
        // staging directory's name would pass.
        if (resolved != root && !resolved.startsWith(root + File.separator)) {
            throw UnsafeEntryException(entryName)
        }
        return target
    }

    fun checkEntryCount(count: Int) {
        if (count > MAX_ENTRIES) {
            throw ArchiveTooLargeException("Backup contains more than $MAX_ENTRIES files")
        }
    }

    fun checkTotalBytes(bytes: Long) {
        if (bytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
            throw ArchiveTooLargeException("Backup expands to more than $MAX_TOTAL_UNCOMPRESSED_BYTES bytes")
        }
    }
}
