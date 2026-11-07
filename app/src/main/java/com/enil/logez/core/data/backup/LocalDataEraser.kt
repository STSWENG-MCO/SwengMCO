package com.enil.logez.core.data.backup

import android.content.Context
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.data.dao.BackupDao
import com.enil.logez.core.data.seed.SeedManager
import com.enil.logez.core.domain.WidgetRefresher
import com.enil.logez.core.domain.model.UserSettings
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.TransactionRunner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import org.maplibre.android.MapLibre
import org.maplibre.android.offline.OfflineManager

/**
 * Settings > Data > "Delete all data" (Play-readiness audit, 2026-09-25). Until this existed the
 * only way to erase everything was Android's own "Clear storage" or uninstalling, and the privacy
 * policy now names this action as the in-app deletion route, so it must actually leave nothing.
 *
 * Mirrors the first half of [BackupRestorer] and nothing more:
 * 1. The seed marker is cleared FIRST, so that if anything after the wipe fails or the process
 *    dies, the next launch re-seeds the exercise library instead of leaving it empty forever
 *    (2026-09-25 review). A re-seed after a rolled-back wipe is harmless.
 * 2. Every backed-up table is emptied in one transaction, children first ([BackupTables.WIPE_ORDER]),
 *    so a failure leaves the data exactly as it was.
 * 3. The photo directories, any staged or half-swapped restore, and the cache (shared summary
 *    images, camera temp files) are deleted, and MapLibre's tile cache is reset: it holds map
 *    images of the areas the user's routes covered.
 * 4. Settings go back to their defaults, and the library is re-seeded.
 * 5. The widget is repainted so it stops showing the erased week.
 *
 * Runs NonCancellable: once the wipe has committed, leaving the screen must not stop it halfway.
 *
 * The caller refuses to run while a workout is in progress, as restore does: an active session
 * holds a foreground service and in-memory state pointing at rows this would delete. Files the
 * user already exported, or images saved to Pictures, live outside the app and are not touched.
 */
@Singleton
class LocalDataEraser @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backupDao: BackupDao,
    private val transactionRunner: TransactionRunner,
    private val settingsRepository: SettingsRepository,
    private val seedManager: SeedManager,
    private val widgetRefresher: WidgetRefresher,
    private val logger: AppLogger,
) {
    suspend fun eraseEverything() = withContext(NonCancellable) {
        seedManager.resetSeedVersion()

        transactionRunner.runInTransaction {
            BackupTables.WIPE_ORDER.forEach { backupDao.wipe(it) }
        }

        // Past the commit nothing may throw out of here: the caller would report "nothing was
        // changed" about data that is already gone. Each remaining step is logged and skipped.
        runCatching { withContext(Dispatchers.IO) { deleteMediaAndCache(context.filesDir, context.cacheDir) } }
            .onFailure { logger.e(TAG, "Deleting media and cache after the wipe failed", it) }
        resetMapCache()
        runCatching { settingsRepository.replaceAll(UserSettings()) }
            .onFailure { logger.e(TAG, "Resetting settings after the wipe failed", it) }
        runCatching { seedManager.seedIfNeeded() }
            .onFailure { logger.e(TAG, "Re-seeding after deleting all data failed", it) }
        runCatching { widgetRefresher.refresh() }
            .onFailure { logger.e(TAG, "Widget refresh after deleting all data failed", it) }
    }

    /**
     * MapLibre keeps downloaded tiles in its own database under filesDir. Its API needs the
     * library initialised on the main thread and answers through a callback. Best effort: a
     * failure is logged, never allowed to fail the erase, and bounded so a stuck callback cannot
     * hang it.
     */
    private suspend fun resetMapCache() {
        val done = withTimeoutOrNull(MAP_CACHE_TIMEOUT_MS) {
            withContext(Dispatchers.Main) {
                MapLibre.getInstance(context)
                suspendCancellableCoroutine { cont ->
                    OfflineManager.getInstance(context).resetDatabase(object : OfflineManager.FileSourceCallback {
                        override fun onSuccess() { if (cont.isActive) cont.resume(Unit) }
                        override fun onError(message: String) {
                            logger.e(TAG, "Resetting the map tile cache failed: $message", null)
                            if (cont.isActive) cont.resume(Unit)
                        }
                    })
                }
            }
        }
        if (done == null) logger.e(TAG, "Resetting the map tile cache timed out", null)
    }

    private companion object {
        const val TAG = "LocalDataEraser"
        const val MAP_CACHE_TIMEOUT_MS = 10_000L
    }
}

/**
 * Deletes both photo directories, their half-swapped ".old" copies, any staged restore (a full
 * copy of a backup) with its marker, and everything in the cache. Split out so it is testable on
 * plain files.
 */
internal fun deleteMediaAndCache(filesDir: File, cacheDir: File) {
    listOf(BackupWriter.PROGRESS_PHOTOS_DIR, BackupWriter.EXERCISE_MEDIA_DIR).forEach { name ->
        File(filesDir, name).deleteRecursively()
        File(filesDir, "$name.old").deleteRecursively()
    }
    RestoreMarker.stagingDirIn(filesDir).deleteRecursively()
    RestoreMarker(filesDir).clear()
    cacheDir.listFiles()?.forEach { it.deleteRecursively() }
}
