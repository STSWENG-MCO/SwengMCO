package com.enil.logez.feature.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.enil.logez.R
import com.enil.logez.core.domain.calc.HeartRateZone

/**
 * A zone's display name (Warm Up ... Peak). Shared by the live tracking screen's Vitals card and
 * the walk/run summary's time-in-zones rows, so the two always name a zone the same way.
 */
@Composable
internal fun heartRateZoneLabel(zone: HeartRateZone): String = stringResource(
    when (zone) {
        HeartRateZone.ZONE_1 -> R.string.activity_tracking_zone_1
        HeartRateZone.ZONE_2 -> R.string.activity_tracking_zone_2
        HeartRateZone.ZONE_3 -> R.string.activity_tracking_zone_3
        HeartRateZone.ZONE_4 -> R.string.activity_tracking_zone_4
        HeartRateZone.ZONE_5 -> R.string.activity_tracking_zone_5
    },
)
