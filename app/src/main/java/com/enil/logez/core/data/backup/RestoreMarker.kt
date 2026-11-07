package com.enil.logez.core.data.backup

import java.io.File

/**
 * A file on disk recording that a restore is part-way through, and how far.
 *
 * The database commit is atomic but the photo swap that follows it is not, so a crash in between
 * would otherwise leave restored rows pointing at files that were never replaced — with nothing
 * to say so. The marker turns that into something the next launch can finish.
 */
class RestoreMarker(filesDir: File) {
    private val file = File(filesDir, FILE_NAME)

    enum class Phase {
        /** The transaction is in flight. It either commits or rolls back; nothing to repair. */
        DATABASE,

        /** The data is in. Photo directories may be half-swapped and the swap should be re-run. */
        MEDIA,
    }

    fun write(phase: Phase, stagingDir: File) {
        file.writeText("${phase.name}\n${stagingDir.absolutePath}")
    }

    fun read(): Phase? {
        if (!file.isFile) return null
        val name = file.readLines().firstOrNull()?.trim() ?: return null
        return Phase.entries.firstOrNull { it.name == name }
    }

    fun stagingDir(): File? {
        if (!file.isFile) return null
        return file.readLines().getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let(::File)
    }

    fun clear() {
        file.delete()
    }

    companion object {
        private const val FILE_NAME = "restore.marker"

        /** Where an in-flight restore unpacks to, before anything real is touched. */
        fun stagingDirIn(filesDir: File) = File(filesDir, "restore_staging")
    }
}
