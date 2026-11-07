package com.enil.logez.core.designsystem

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * A plain title/body confirmation dialog with one real action (Confirm) and one no-op dismissal
 * (Cancel) — the shape independently hand-wired at dozens of call sites across the app (2026-09-19
 * debt audit). Every call site had already coupled dismissing the dialog with running its action
 * (`{ showX = false; action() }`); this composable does that coupling once, so a call site just
 * passes the two things that actually differ: [onDismissRequest] (how to hide the dialog) and
 * [onConfirm] (what confirming actually does).
 *
 * Not for a two-real-choices dialog (e.g. "Resume" vs. "Discard and start new," where neither
 * button is a plain no-op cancel) — that shape doesn't fit this contract and should stay a direct
 * [AlertDialog] call.
 */
@Composable
fun ConfirmDialog(
    onDismissRequest: () -> Unit,
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    dismissLabel: String,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = { onDismissRequest(); onConfirm() }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) { Text(dismissLabel) }
        },
    )
}
