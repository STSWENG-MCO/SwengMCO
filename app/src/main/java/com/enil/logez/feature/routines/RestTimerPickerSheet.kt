package com.enil.logez.feature.routines

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.enil.logez.R

/** §5.1.2 rest-timer picker: "Default", "Off", then 5s steps up to 5:00. null=default, 0=off (spine). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestTimerPickerSheet(
    currentSeconds: Int?,
    defaultSeconds: Int,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            item {
                RestTimerOptionRow(
                    label = stringResource(R.string.rest_timer_default_option, formatMmSs(defaultSeconds)),
                    selected = currentSeconds == null,
                    onClick = { onSelect(null); onDismiss() },
                )
            }
            item {
                RestTimerOptionRow(
                    label = stringResource(R.string.rest_timer_off_option),
                    selected = currentSeconds == 0,
                    onClick = { onSelect(0); onDismiss() },
                )
            }
            items((5..300 step 5).toList()) { seconds ->
                RestTimerOptionRow(
                    label = formatMmSs(seconds),
                    selected = currentSeconds == seconds,
                    onClick = { onSelect(seconds); onDismiss() },
                )
            }
        }
    }
}

@Composable
private fun RestTimerOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(label) },
        trailingContent = if (selected) {
            { Icon(Icons.Outlined.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
    )
}

internal fun formatMmSs(totalSeconds: Int): String = com.enil.logez.core.designsystem.formatMmSs(totalSeconds)
