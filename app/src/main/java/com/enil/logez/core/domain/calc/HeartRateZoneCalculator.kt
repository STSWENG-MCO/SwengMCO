package com.enil.logez.core.domain.calc

/**
 * The live GPS activity-tracking screen's heart-rate zone, standard 5-zone %-of-max-HR model
 * (Garmin/Polar-style: Zone 1 up to 60%, then 10-point bands to Zone 5 at 90%+). Requires the
 * Owner's own `UserSettings.maxHeartRateBpm` -- this app has no age/birthdate to estimate one
 * (see that field's own doc comment), so there is no default/estimated zone, only "none yet" until
 * the Owner sets it once in Settings.
 */
enum class HeartRateZone(val number: Int, val label: String, val minPercentOfMax: Double) {
    ZONE_1(1, "Warm Up", 0.0),
    ZONE_2(2, "Fat Burn", 0.6),
    ZONE_3(3, "Cardio", 0.7),
    ZONE_4(4, "Hard", 0.8),
    ZONE_5(5, "Peak", 0.9),
}

object HeartRateZoneCalculator {
    /** Null if [maxHeartRateBpm] isn't a usable basis (unset or non-positive). */
    fun zoneFor(bpm: Long, maxHeartRateBpm: Int?): HeartRateZone? {
        if (maxHeartRateBpm == null || maxHeartRateBpm <= 0) return null
        val percentOfMax = bpm.toDouble() / maxHeartRateBpm
        return HeartRateZone.entries.last { percentOfMax >= it.minPercentOfMax }
    }
}
