package com.enil.logez.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import com.enil.logez.R

/**
 * M20a: the one drag affordance for every reorderable row — routine builder exercises, regular-
 * workout logger exercises, and the Workout tab's folders and routines. Quiet `onSurfaceVariant`
 * glyph, always visible, no press ornament (near-zero-motion rule), inside Material's 48dp minimum
 * interactive size so a long-press lands on the whole target rather than the 24dp glyph.
 *
 * [modifier] must carry the row's `longPressDraggableHandle(...)`; it is a parameter (not created
 * here) because that modifier can only be built inside `ReorderableItem`'s content lambda, a
 * `ReorderableCollectionItemScope`. Chain order matters: the drag pointer-input node is outermost
 * so it receives touches over the full enforced size.
 *
 * [onMoveUp]/[onMoveDown] are the single-pointer alternative a drag needs (WCAG 2.5.7; TalkBack,
 * switch access and keyboards cannot long-press-drag): they surface as "Move up"/"Move down"
 * custom accessibility actions on the handle. Pass `null` at the edge of the list so a dead action
 * is not announced.
 */
@Composable
fun DragHandle(
    modifier: Modifier = Modifier,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
) {
    val moveUpLabel = stringResource(R.string.workout_move_up)
    val moveDownLabel = stringResource(R.string.workout_move_down)
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics {
                customActions = listOfNotNull(
                    onMoveUp?.let { CustomAccessibilityAction(moveUpLabel) { it(); true } },
                    onMoveDown?.let { CustomAccessibilityAction(moveDownLabel) { it(); true } },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.DragHandle,
            contentDescription = stringResource(R.string.drag_handle_content_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
