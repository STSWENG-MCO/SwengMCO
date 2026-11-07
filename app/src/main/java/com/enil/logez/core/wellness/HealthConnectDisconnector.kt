package com.enil.logez.core.wellness

import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.domain.WidgetRefresher
import com.enil.logez.core.domain.repository.WellnessRepository
import com.enil.logez.core.domain.repository.WorkoutHeartRateSampleRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Settings > Data's "Disconnect Health Connect and delete its data" (Play-readiness audit,
 * 2026-09-25). Before this, LogEZ kept a copy of everything it read from Health Connect, the daily
 * step/calorie totals and each workout's heart-rate samples, with no way to delete it short of
 * clearing the whole app, and the privacy policy said revoking access deleted nothing.
 *
 * Two halves, in this order:
 * 1. Revoke every Health Connect permission, so nothing is read again.
 * 2. Delete both local copies, then repaint the widget, whose steps line reads the daily cache.
 *
 * The revoke is best-effort. Health Connect may be uninstalled, disabled, or refuse the call, and
 * none of those should stop the user deleting data that lives in this app. The deletes always run.
 */
class HealthConnectDisconnector @Inject constructor(
    private val healthMetricsSource: HealthMetricsSource,
    private val wellnessRepository: WellnessRepository,
    private val heartRateSampleRepository: WorkoutHeartRateSampleRepository,
    private val widgetRefresher: WidgetRefresher,
    private val logger: AppLogger,
) {
    suspend fun disconnectAndDelete() {
        try {
            healthMetricsSource.revokeAllPermissions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(TAG, "Revoking Health Connect permissions failed; deleting the local copies anyway", e)
        }
        wellnessRepository.deleteAll()
        heartRateSampleRepository.deleteAll()
        widgetRefresher.refresh()
    }

    private companion object {
        const val TAG = "HealthConnectDisconnector"
    }
}
