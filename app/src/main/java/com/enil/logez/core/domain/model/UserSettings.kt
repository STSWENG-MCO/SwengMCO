package com.enil.logez.core.domain.model

import java.time.DayOfWeek

/** Every value in PHASE2_PLAN.md §5.2's Settings tree, aggregated for a single observable read. Theme is dark-only (Owner directive) — no theme-mode setting. */
data class UserSettings(
    // Preferences
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    /** Body-measurement circumference display (Measurements screen) — canonical storage is always cm. */
    val lengthUnit: LengthUnit = LengthUnit.CM,
    /** Which body the muscle diagram (Analytics/Profile/Workout recap/Share) renders. Male default
     * matches the single body type every prior version of the diagram implicitly rendered. */
    val muscleDiagramVariant: MuscleDiagramVariant = MuscleDiagramVariant.MALE,
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val perExerciseUnitOverrides: Map<String, WeightUnit> = emptyMap(),

    // Workouts
    val defaultRestTimerSeconds: Int = 90,
    val timerSound: Int = 1,
    // Owner, 2026-09-03: every audio setting is a continuous 0f-1f slider now, not a discrete
    // Off/Quiet/Normal/Loud step — 0f means silent (the same "off" the old enum's OFF meant).
    val timerVolume: Float = 0.66f,
    val setCompleteVolume: Float = 0.66f,
    val prVolume: Float = 0.66f,
    val previousValuesMode: PreviousValuesMode = PreviousValuesMode.ANY_WORKOUT,
    val warmupCalculatorEnabled: Boolean = true,
    val warmupMethod: List<WarmupStep> = defaultWarmupMethod,
    val includeWarmupsInStats: Boolean = false,
    val keepAwake: Boolean = true,
    val plateCalculatorEnabled: Boolean = true,
    val plateEquipment: PlateEquipment = defaultPlateEquipment,
    val rpeTrackingEnabled: Boolean = false,
    val smartSupersetScrolling: Boolean = true,
    val inlineTimerEnabled: Boolean = true,
    val livePrNotificationEnabled: Boolean = true,
    /** Basis for the GPS activity-tracking screen's live heart-rate zone. Null (never set) means
     * zones don't render at all -- same graceful-degrade rule as every other Health Connect-backed
     * display in this app -- rather than guess from an age this app has never collected (it has no
     * account and no server; asking for a birthdate to estimate this would be a bigger ask than
     * asking for the number directly). */
    val maxHeartRateBpm: Int? = null,
    /** Owner, 2026-09-03: hides the Workout tab's Progress heatmap when off. Default on — nothing is hidden today. */
    val showHeatmap: Boolean = true,
    /** Owner, 2026-09-03: hides the Workout tab's Goals card when off. Default on — nothing is hidden today. */
    val showGoals: Boolean = true,
    /** Owner, 2026-09-21: which metrics the Measurements screen shows for entry/display — edited
     * from that screen itself, not this Settings tree. Defaults to COMPLETE (preserves what
     * shipped the day before this setting existed; no surprise regression for an existing user). */
    val measurementsTrackingMode: MeasurementsTrackingMode = MeasurementsTrackingMode.COMPLETE,
    /** How many days a week the home-screen widget counts toward. Deliberately not the WEEKLY /
     * WORKOUT_COUNT goal: goals are user-authored and usually absent, so most widgets would have
     * no denominator, and that metric counts sessions rather than days. */
    val weeklyActiveDayTarget: Int = 4,
)
