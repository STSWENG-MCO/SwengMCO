package com.enil.logez.core.domain.model

import kotlinx.serialization.Serializable

/** One row of the Warm-up Calculator's editable formula (PHASE2_PLAN.md §5.1.6, §5.2). */
@Serializable
data class WarmupStep(val percent: Double, val reps: Int)

/** PHASE2_PLAN.md §5.2 default: 40%x5 / 60%x5 / 80%x3 of the working weight. */
val defaultWarmupMethod: List<WarmupStep> = listOf(
    WarmupStep(percent = 0.40, reps = 5),
    WarmupStep(percent = 0.60, reps = 5),
    WarmupStep(percent = 0.80, reps = 3),
)
