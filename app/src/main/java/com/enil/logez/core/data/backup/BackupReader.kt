package com.enil.logez.core.data.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a backup archive.
 *
 * A `content://` Uri cannot be opened as a random-access zip, so both operations here are forward
 * passes over a stream — which is the other reason the manifest is the first entry: [peekManifest]
 * can stop after one small read rather than unpacking a potentially large archive just to show the
 * user what is in it.
 *
 * [stage] never touches the database or the live photo directories. Everything lands in a staging
 * directory first, so a corrupt or malicious archive fails before anything real has changed.
 */
@Singleton
class BackupReader @Inject constructor() {

    class NotABackupException(message: String) : IOException(message)

    /** Reads the manifest and stops. Cheap, and nothing is written anywhere. */
    fun peekManifest(input: InputStream): BackupManifest {
        ZipInputStream(input.buffered()).use { zip ->
            val first = zip.nextEntry
                ?: throw NotABackupException("The file is empty or is not a zip archive")
            if (first.name != BackupFormat.MANIFEST_ENTRY) {
                throw NotABackupException("This does not look like a LogEZ backup")
            }
            val text = zip.readBytes().toString(Charsets.UTF_8)
            return runCatching { BackupFormat.jsonRead.decodeFromString<BackupManifest>(text) }
                .getOrElse { throw NotABackupException("The backup's manifest could not be read") }
        }
    }

    /**
     * Unpacks the whole archive into [stagingDir], which is emptied first.
     *
     * Every entry name is resolved and checked before a byte is written: the archive is a file the
     * app did not create, and an entry aimed at the live database would otherwise be extracted
     * straight over it.
     */
    fun stage(input: InputStream, stagingDir: File, onProgress: (Int) -> Unit = {}): BackupManifest {
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()

        var manifest: BackupManifest? = null
        var entries = 0
        var totalBytes = 0L

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                ZipEntryNames.checkEntryCount(entries)

                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }

                val target = ZipEntryNames.resolveSafely(stagingDir, entry.name)
                target.parentFile?.mkdirs()

                val written = target.outputStream().buffered().use { out -> zip.copyTo(out) }
                totalBytes += written
                ZipEntryNames.checkTotalBytes(totalBytes)

                if (entry.name == BackupFormat.MANIFEST_ENTRY) {
                    manifest = runCatching {
                        BackupFormat.jsonRead.decodeFromString<BackupManifest>(target.readText())
                    }.getOrElse { throw NotABackupException("The backup's manifest could not be read") }
                }
                onProgress(entries)
                zip.closeEntry()
            }
        }

        val read = manifest ?: throw NotABackupException("This does not look like a LogEZ backup")
        // Both media folders always exist once staged, even for a backup without photos. The
        // restore's media swap relies on this: after an interruption, a staged folder that is
        // missing can only mean it was already moved into place, so resuming must leave the live
        // folder alone (2026-09-25 review: resuming used to throw the restored photos away).
        listOf(BackupWriter.PROGRESS_PHOTOS_DIR, BackupWriter.EXERCISE_MEDIA_DIR).forEach { name ->
            File(stagingDir, "${BackupFormat.MEDIA_PREFIX}$name").mkdirs()
        }
        verifyRowCounts(stagingDir, read)
        return read
    }

    /**
     * Cross-checks what the manifest promised against what actually arrived, before the database
     * is touched. A truncated archive fails here rather than halfway through a wipe.
     */
    private fun verifyRowCounts(stagingDir: File, manifest: BackupManifest) {
        manifest.tables.forEach { table ->
            val file = File(stagingDir, BackupTables.tableEntry(table.name))
            val actual = if (file.isFile) file.useLines { lines -> lines.count { it.isNotBlank() } } else 0
            if (actual != table.rowCount) {
                throw NotABackupException(
                    "The backup looks incomplete: ${table.name} says ${table.rowCount} rows but has $actual",
                )
            }
        }
    }

    fun stagedSettings(stagingDir: File): SettingsDto? {
        val file = File(stagingDir, BackupFormat.SETTINGS_ENTRY)
        if (!file.isFile) return null
        return runCatching { BackupFormat.jsonRead.decodeFromString<SettingsDto>(file.readText()) }.getOrNull()
    }

    /** Rows for one table, decoded a page at a time so no whole table is ever held. */
    fun <T> forEachPage(stagingDir: File, table: String, decode: (String) -> T, consume: (List<T>) -> Unit) {
        val file = File(stagingDir, BackupTables.tableEntry(table))
        if (!file.isFile) return
        file.bufferedReader().use { reader ->
            val page = ArrayList<T>(BackupFormat.PAGE_SIZE)
            reader.lineSequence().forEach { line ->
                if (line.isBlank()) return@forEach
                page.add(decode(line))
                if (page.size == BackupFormat.PAGE_SIZE) {
                    consume(page.toList())
                    page.clear()
                }
            }
            if (page.isNotEmpty()) consume(page.toList())
        }
    }
}
