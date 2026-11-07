package com.enil.logez.feature.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.enil.logez.core.domain.WidgetRefresher
import dagger.hilt.android.EntryPointAccessors
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Repaints the widget when the local day rolls over: today's marker moves, and a new week may have
 * started.
 *
 * A self-rescheduling one-shot rather than periodic work — a periodic request's phase drifts and
 * cannot be pinned to local midnight, so after a timezone change it would keep firing at the old
 * wall-clock time forever.
 *
 * Plain [CoroutineWorker] with an entry point rather than `@HiltWorker`, which would need another
 * artifact, a `HiltWorkerFactory` and a `Configuration.Provider` on the Application. Introducing no
 * custom factory also means WorkManager's own startup initialization works untouched.
 */
class MidnightWidgetWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            MidnightWorkerEntryPoint::class.java,
        )
        entryPoint.widgetRefresher().refresh()
        schedule(applicationContext) // re-arm for the next midnight
        return Result.success()
    }

    companion object {
        const val UNIQUE_NAME = "logez_widget_midnight"

        /**
         * REPLACE so arming from app start, from [doWork], and from a time change all converge on
         * exactly one pending request rather than stacking.
         *
         * `getInstance` throws when WorkManager's own startup initialization has not run, which is
         * the case in a plain JVM/Robolectric environment. This is called from
         * `LogEzApplication.onCreate`, so letting that propagate would take the whole app down at
         * launch — and the worst case of swallowing it is a widget that repaints on the next app
         * interaction instead of exactly at midnight.
         */
        fun schedule(context: Context) {
            val delayMs = nextMidnightDelayMillis(ZonedDateTime.now(ZoneId.systemDefault()))
            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    UNIQUE_NAME,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<MidnightWidgetWorker>()
                        .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                        .build(),
                )
            }
        }
    }
}

/**
 * Extracted because it is the only arithmetic here worth testing.
 *
 * A minute past midnight, not midnight itself, so a slightly early wakeup does not compute the
 * previous day's window.
 */
internal fun nextMidnightDelayMillis(now: ZonedDateTime): Long {
    // atStartOfDay(zone), not LocalDate.atTime(0, 0): some zones skip 00:00 entirely on a DST
    // transition, and only the zoned overload resolves that to the day's first valid instant.
    val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusMinutes(1)
    return Duration.between(now, nextMidnight).toMillis().coerceAtLeast(0)
}

/** Separate from [WidgetEntryPoint] so the worker asks only for what it uses. */
@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface MidnightWorkerEntryPoint {
    fun widgetRefresher(): WidgetRefresher
}
