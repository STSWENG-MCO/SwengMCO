package com.enil.logez.feature.workout.finish

import androidx.annotation.StringRes
import com.enil.logez.R
import com.enil.logez.core.domain.model.PrType

/** Display labels for the 9 §8.4 PrTypes — shared by the live banner and the summary's medals. */
@StringRes
internal fun PrType.labelRes(): Int = when (this) {
    PrType.HEAVIEST_WEIGHT -> R.string.pr_type_heaviest_weight
    PrType.BEST_1RM -> R.string.pr_type_best_1rm
    PrType.BEST_SET_VOLUME -> R.string.pr_type_best_set_volume
    PrType.BEST_SESSION_VOLUME -> R.string.pr_type_best_session_volume
    PrType.MOST_REPS_SET -> R.string.pr_type_most_reps_set
    PrType.MOST_SESSION_REPS -> R.string.pr_type_most_session_reps
    PrType.LONGEST_DISTANCE -> R.string.pr_type_longest_distance
    PrType.LONGEST_TIME -> R.string.pr_type_longest_time
    PrType.BEST_TIME -> R.string.pr_type_best_time
}
