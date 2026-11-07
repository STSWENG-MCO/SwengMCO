package com.enil.logez.core.domain.model

/**
 * Optional sub-head refinement within a [MuscleGroup] (Owner request 2026-08-26, researched
 * against bodybuilding/exercise-science sources before scoping). Deliberately NOT a split of
 * [MuscleGroup] itself — that would force every one of the 400 seeded exercises and every
 * existing custom exercise to be re-tagged with a specific head, and some exercises genuinely
 * don't favor one head over another. Instead this is a second, purely additive field: `null` for
 * every exercise that doesn't specify one (including all pre-existing exercises), meaningful only
 * for the 6 groups researched to have a real, commonly-trained head distinction, and (for
 * shoulders/triceps/calves/lats/hamstrings) matching sub-regions [BodyDiagramRegions] already has
 * traced but currently merges into one [MuscleGroup]-wide fill.
 *
 * Scope deliberately excludes biceps (long/short head) — a real, commonly-discussed split, but
 * with no traced sub-region backing it (the source anatomy data has only one combined `biceps`
 * region per side), and lower-back/forearms/glutes-medius-vs-maximus — real distinctions, but
 * less central to typical bodybuilding programming than the six covered here (glutes maximus vs.
 * medius is already covered by the existing GLUTES/ABDUCTORS split).
 */
enum class MuscleHead {
    ANTERIOR_DELTOID,
    LATERAL_DELTOID,
    POSTERIOR_DELTOID,
    UPPER_CHEST,
    LOWER_CHEST,
    TRICEPS_LATERAL_HEAD,
    TRICEPS_LONG_HEAD,
    GASTROCNEMIUS,
    SOLEUS,
    UPPER_LATS,
    MID_LATS,
    LOWER_LATS,
    LATERAL_HAMSTRING,
    MEDIAL_HAMSTRING,
}

/** Which [MuscleHead] options apply to [this] group — empty for every group with no tracked sub-head split. */
val MuscleGroup.availableHeads: List<MuscleHead>
    get() = when (this) {
        MuscleGroup.SHOULDERS -> listOf(MuscleHead.ANTERIOR_DELTOID, MuscleHead.LATERAL_DELTOID, MuscleHead.POSTERIOR_DELTOID)
        MuscleGroup.CHEST -> listOf(MuscleHead.UPPER_CHEST, MuscleHead.LOWER_CHEST)
        MuscleGroup.TRICEPS -> listOf(MuscleHead.TRICEPS_LATERAL_HEAD, MuscleHead.TRICEPS_LONG_HEAD)
        MuscleGroup.CALVES -> listOf(MuscleHead.GASTROCNEMIUS, MuscleHead.SOLEUS)
        MuscleGroup.LATS -> listOf(MuscleHead.UPPER_LATS, MuscleHead.MID_LATS, MuscleHead.LOWER_LATS)
        MuscleGroup.HAMSTRINGS -> listOf(MuscleHead.LATERAL_HAMSTRING, MuscleHead.MEDIAL_HAMSTRING)
        else -> emptyList()
    }
