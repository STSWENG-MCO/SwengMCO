package com.enil.logez.core.data.backup

import kotlinx.serialization.json.Json

/**
 * The backup archive's layout and version contract.
 *
 * The archive is a zip whose first entry is always the manifest, so a restore can show real counts
 * after reading one small entry rather than unpacking a potentially large file first. Table rows
 * are JSON Lines — one row per line — so neither writing nor reading has to hold a whole table in
 * memory, which a single JSON document would force.
 */
object BackupFormat {
    /** Bumped only for a change that an older app genuinely cannot read. */
    const val SCHEMA_VERSION = 1

    const val MANIFEST_ENTRY = "manifest.json"
    const val SETTINGS_ENTRY = "settings.json"
    const val TABLES_PREFIX = "tables/"
    const val MEDIA_PREFIX = "media/"

    const val MIME_TYPE = "application/zip"

    /** Rows are read and written a page at a time rather than a table at a time. */
    const val PAGE_SIZE = 1000

    /** Nothing omitted: the archive is meant to be readable and diffable on its own. */
    val jsonWrite = Json {
        prettyPrint = false
        explicitNulls = true
        encodeDefaults = true
    }

    /**
     * Tolerant on purpose. A backup written by a newer app may carry columns this one has never
     * heard of, and one written by an older app will be missing columns it has — the DTO defaults
     * cover the second case and [Json.ignoreUnknownKeys] the first.
     */
    val jsonRead = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    }
}
