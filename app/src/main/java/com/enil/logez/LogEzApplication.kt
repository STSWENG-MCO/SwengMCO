package com.enil.logez

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import com.enil.logez.core.data.backup.BackupRestorer
import com.enil.logez.core.data.seed.SeedManager
import com.enil.logez.feature.widget.MidnightWidgetWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.enil.logez.core.data.media.MediaFileJanitor

@HiltAndroidApp
class LogEzApplication : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var seedManager: SeedManager

    @Inject lateinit var backupRestorer: BackupRestorer

    @Inject lateinit var mediaFileJanitor: MediaFileJanitor

    /** PHASE2_PLAN.md §7.7: seeding runs from an application-scoped coroutine at startup. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            // Before seeding, deliberately: recovering a restore ends by re-running the seeder,
            // and the two must not race.
            runCatching { backupRestorer.resumeIfInterrupted(filesDir) }
            seedManager.seedIfNeeded()
            // After the restore check: the sweep must never see a half-swapped photo directory.
            runCatching { mediaFileJanitor.sweepOrphans() }
        }
        // Re-armed every launch: the request is unique and REPLACE, so this converges rather than
        // stacking, and it recovers the schedule after a reboot or a force-stop.
        MidnightWidgetWorker.schedule(this)
    }

    /**
     * M20b: the app-wide Coil `ImageLoader` for [com.enil.logez.core.designsystem.LocalImage].
     * No network fetcher is registered anywhere -- every model is a local `File` (custom-exercise
     * photos); the only network use in the app is MapLibre's map tiles, which never go through
     * Coil. No transition is set, so
     * loads render instantly with no crossfade (near-zero-motion rule).
     */
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.15).build() }
            .build()
}
