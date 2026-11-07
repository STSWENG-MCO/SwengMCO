package com.enil.logez.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.ui.graphics.vector.ImageVector
import com.enil.logez.core.domain.model.MuscleGroup

/**
 * PHASE2_PLAN.md §7.5: a null `mediaPath` resolves to a per-muscle-group illustration at display
 * time. §7.5's real design is 20 original hand-drawn body-silhouette illustrations, authored as
 * VectorDrawables — deferred; this is a functionally-correct placeholder using Material Icons
 * (already a dependency) so the resolver mechanism, not the art, ships with M2. Swapping in the
 * real illustrations later touches zero data, exactly as §7.5 intends.
 */
fun muscleGroupIcon(group: MuscleGroup): ImageVector = when (group) {
    MuscleGroup.CARDIO -> Icons.AutoMirrored.Outlined.DirectionsRun
    MuscleGroup.FULL_BODY -> Icons.AutoMirrored.Outlined.DirectionsWalk
    MuscleGroup.OTHER -> Icons.Outlined.QuestionMark
    MuscleGroup.ABDOMINALS -> Icons.Outlined.SelfImprovement
    else -> LogEzIcons.Workout
}

/** A generic fallback used only where no muscle group is known yet (should not normally occur). */
val genericExerciseIcon: ImageVector = Icons.Outlined.Accessibility
