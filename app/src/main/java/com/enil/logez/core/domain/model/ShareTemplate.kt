package com.enil.logez.core.domain.model

private val STRENGTH_ONLY = setOf(WorkoutKind.STRENGTH)
private val GPS_ONLY = setOf(WorkoutKind.GPS_TRACKED)
private val BOTH_KINDS = setOf(WorkoutKind.STRENGTH, WorkoutKind.GPS_TRACKED)

enum class ShareTemplate(
    val kinds: Set<WorkoutKind>,
    val worksOnPhoto: Boolean,
) {
    STACK(BOTH_KINDS, worksOnPhoto = true),
    NUMBER(BOTH_KINDS, worksOnPhoto = true),
    RECORDS(BOTH_KINDS, worksOnPhoto = true),
    GLASS(BOTH_KINDS, worksOnPhoto = true),
    RAIL(BOTH_KINDS, worksOnPhoto = true),
    STRIP(BOTH_KINDS, worksOnPhoto = true),
    BODY_MAP(STRENGTH_ONLY, worksOnPhoto = true),
    ROUTE(GPS_ONLY, worksOnPhoto = true),
    SPLITS(GPS_ONLY, worksOnPhoto = true),
    SUMMARY(STRENGTH_ONLY, worksOnPhoto = false),
    MUSCLES(STRENGTH_ONLY, worksOnPhoto = false),
    LOG(STRENGTH_ONLY, worksOnPhoto = false),
    STREAK(BOTH_KINDS, worksOnPhoto = false),
}

enum class ShareBackground {
    DARK,
    LIME,
    LIGHT,
    CLEAR,
}

enum class ShareTextTone {
    LIGHT,
    DARK,
}

enum class ShareMetric(
    val kinds: Set<WorkoutKind>,
) {
    VOLUME(STRENGTH_ONLY),
    TIME(BOTH_KINDS),
    SETS(STRENGTH_ONLY),
    REPS(STRENGTH_ONLY),
    DISTANCE(GPS_ONLY),
    PACE(GPS_ONLY),
    AVG_BPM(GPS_ONLY),
    ;

    companion object {
        fun forKind(kind: WorkoutKind): Set<ShareMetric> = entries.filter { kind in it.kinds }.toSet()
    }
}

/** The Show tab's "Details" chips. */
enum class ShareDetail(
    val kinds: Set<WorkoutKind>,
) {
    DATE(BOTH_KINDS),
    START_TIME(BOTH_KINDS),
    RECORDS(BOTH_KINDS),
    EXERCISES(STRENGTH_ONLY),
    LOGEZ_MARK(BOTH_KINDS),
    HIDE_ROUTE_ENDS(GPS_ONLY),
    ;

    companion object {
        fun forKind(kind: WorkoutKind): Set<ShareDetail> = entries.filter { kind in it.kinds }.toSet()
    }
}
