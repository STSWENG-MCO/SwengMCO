package com.enil.logez.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable

/**
 * M18 uniform boxed cells: an `outlineVariant` hairline border (including when disabled, i.e.
 * completed-locked rows) so the routine builder's fields and the live logger's boxed grid
 * (SET/PREVIOUS/RPE) render as one visual system. Focus keeps the default primary outline.
 * Shared here (2026-09-19 debt audit) rather than duplicated per-screen, since the whole point is
 * that these two screens' cells match — a future border-color change only needs to happen once.
 */
@Composable
fun boxedFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
)
