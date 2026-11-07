package com.enil.logez.core.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The archive's first entry: enough to decide whether a restore can proceed, and to show the user
 * real numbers, without reading anything else.
 *
 * Two versions rather than one. [backupSchemaVersion] is the archive's own layout, bumped only
 * when an older app genuinely cannot read it. [roomSchemaVersion] is the database the rows came
 * from — an older one is fine, because every DTO field has a default, while a newer one is not,
 * because columns may exist that this app cannot place.
 */
@Serializable
data class BackupManifest(
    @SerialName("backup_schema_version") val backupSchemaVersion: Int = BackupFormat.SCHEMA_VERSION,
    @SerialName("room_schema_version") val roomSchemaVersion: Int = 0,
    @SerialName("app_version_name") val appVersionName: String = "",
    @SerialName("app_version_code") val appVersionCode: Long = 0,
    /**
     * Which seed library the rows came from. Informational: a restore resets seeding regardless,
     * because a backup taken at an older seed version would otherwise leave the device convinced
     * it was already up to date and strand the library permanently.
     */
    @SerialName("seed_version") val seedVersion: Int = 0,
    @SerialName("exported_at_epoch_millis") val exportedAtEpochMillis: Long = 0,
    @SerialName("exported_at_zone_id") val exportedAtZoneId: String = "",
    /** Row counts per table, and the single source of truth for the confirm dialog's numbers. */
    @SerialName("tables") val tables: List<TableEntry> = emptyList(),
    @SerialName("media_file_count") val mediaFileCount: Int = 0,
) {
    @Serializable
    data class TableEntry(
        @SerialName("name") val name: String = "",
        @SerialName("row_count") val rowCount: Int = 0,
    )

    fun rowCount(table: String): Int = tables.firstOrNull { it.name == table }?.rowCount ?: 0

    /** What the destructive confirm shows, so the user sees what they are replacing. */
    val workoutCount: Int get() = rowCount(BackupTables.WORKOUTS)
    val exerciseCount: Int get() = rowCount(BackupTables.EXERCISES)
    val routineCount: Int get() = rowCount(BackupTables.ROUTINES)
    val measurementCount: Int get() = rowCount(BackupTables.BODY_MEASUREMENTS)
    val goalCount: Int get() = rowCount(BackupTables.GOAL_DEFINITIONS)

    sealed interface Compatibility {
        data object Ok : Compatibility

        /** Written by a newer LogEZ than this one. */
        data object TooNew : Compatibility
    }

    fun compatibility(currentRoomVersion: Int): Compatibility = when {
        backupSchemaVersion > BackupFormat.SCHEMA_VERSION -> Compatibility.TooNew
        roomSchemaVersion > currentRoomVersion -> Compatibility.TooNew
        else -> Compatibility.Ok
    }
}
