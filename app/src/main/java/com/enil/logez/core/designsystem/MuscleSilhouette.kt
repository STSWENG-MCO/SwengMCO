package com.enil.logez.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.enil.logez.R
import com.enil.logez.core.common.muscleGroupLabel
import com.enil.logez.core.domain.calc.WorkoutMuscleTargetCalculator
import com.enil.logez.core.domain.model.MuscleDiagramVariant
import com.enil.logez.core.domain.model.MuscleGroup

/**
 * Trained-muscle body map: the shared [BodyDiagram] plus a wrapped text list of the muscles under it.
 * Renders nothing when no mappable muscle was trained. It sizes itself from the width it is given
 * (no scrolling, no fixed height), so the summary column and a future pager slide can both host it.
 * Labels sit below the figure, never on it, so dense workouts cannot overlap the diagram.
 */
@Composable
fun MuscleSilhouette(
    intensity: Map<MuscleGroup, Float>,
    variant: MuscleDiagramVariant,
    modifier: Modifier = Modifier,
) {
    val trained = WorkoutMuscleTargetCalculator.trainedGroups(intensity)
    if (trained.isEmpty()) return
    val names = trained.joinToString(", ") { muscleGroupLabel(it) }
    val summary = stringResource(R.string.muscle_silhouette_description, names)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        BodyDiagram(
            intensity = intensity,
            variant = variant,
            modifier = Modifier.clearAndSetSemantics { contentDescription = summary },
        )
        // Same words as the description above, so TalkBack shouldn't read them twice.
        Text(
            text = names,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.xs).clearAndSetSemantics {},
        )
    }
}
