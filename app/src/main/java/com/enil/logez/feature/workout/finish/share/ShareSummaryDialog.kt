package com.enil.logez.feature.workout.finish.share

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzMono
import com.enil.logez.core.designsystem.Radius
import com.enil.logez.core.designsystem.Spacing
import dagger.hilt.android.EntryPointAccessors
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

/**
 * Share flow for the post-workout summary: a centered dialog with the live preview, Square/Story
 * selection, and one Share button whose menu offers the system ACTION_SEND chooser or a
 * "Save to gallery" write into the device's Pictures library. Export and save go through
 * [WorkoutShareController]; nothing here (or in the ViewModel) performs the platform side-effects
 * itself.
 */
@Composable
fun ShareSummaryDialog(
    data: ShareCardData,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    // Fetched through a Hilt entry point rather than the ViewModel: the controller consumes an
    // android.graphics.Bitmap, which the spine testing rule keeps out of ViewModel signatures.
    val shareEntryPoint = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, WorkoutShareEntryPoint::class.java)
    }
    val shareController = remember(shareEntryPoint) { shareEntryPoint.workoutShareController() }
    val logger = remember(shareEntryPoint) { shareEntryPoint.appLogger() }
    // activeAction is deliberately NOT saveable: its coroutine dies with the activity, so restoring
    // an in-flight state would leave the button disabled behind a spinner nothing will ever clear.
    // A finished outcome (status) is real information and does survive rotation.
    var activeAction by remember { mutableStateOf<DialogAction?>(null) }
    var status by rememberSaveable { mutableStateOf(DialogStatus.NONE) }
    // Transient by design: an open menu is a tap away from being reopened, so it need not survive
    // rotation the way status does.
    var menuExpanded by remember { mutableStateOf(false) }
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    val performShare: () -> Unit = {
        scope.launch {
            activeAction = DialogAction.SHARE
            status = DialogStatus.NONE
            try {
                val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                val uri = shareController.exportPng(bitmap, ShareCardFormat.STORY)
                shareController.launchShareChooser(context, uri)
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("ShareSummaryDialog", "Share export failed", e)
                status = DialogStatus.SHARE_FAILED
            } finally {
                activeAction = null
            }
        }
    }
    val performSave: () -> Unit = {
        scope.launch {
            activeAction = DialogAction.SAVE
            status = DialogStatus.NONE
            try {
                val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                shareController.saveToPictures(bitmap, ShareCardFormat.STORY)
                status = DialogStatus.SAVED
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.e("ShareSummaryDialog", "Save-to-gallery failed", e)
                status = DialogStatus.SAVE_FAILED
            } finally {
                activeAction = null
            }
        }
    }
    // Pre-Q only: the legacy Pictures write needs WRITE_EXTERNAL_STORAGE at runtime. On Q+ this
    // launcher is never fired — saveToPictures goes through scoped MediaStore inserts instead.
    val writePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) performSave() else status = DialogStatus.SAVE_PERMISSION_DENIED
    }

    Dialog(
        onDismissRequest = onDismiss,
        // Platform default width is too narrow for the card preview; the Surface below takes 92%.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Styled like LogEzCard (surface fill, Radius.card corners, outlineVariant hairline) so the
        // dialog reads as one of the app's own cards floating over the summary.
        Surface(
            shape = RoundedCornerShape(Radius.card),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 600.dp),
        ) {
            // Scrollable: in landscape the dialog is shorter than title+preview+chips+button, and
            // the share button must stay reachable.
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.md)
                    .padding(top = Spacing.md, bottom = Spacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.share_sheet_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth(),
                )

                ScaledCardPreview(data, ShareCardFormat.STORY, graphicsLayer, Modifier.padding(top = Spacing.md))

                // Single status slot: at most one message, and the latest action's outcome wins.
                when (status) {
                    DialogStatus.NONE -> Unit
                    DialogStatus.SAVED -> StatusText(R.string.share_save_confirmation, isError = false)
                    DialogStatus.SHARE_FAILED -> StatusText(R.string.share_export_failed, isError = true)
                    DialogStatus.SAVE_FAILED -> StatusText(R.string.share_save_failed, isError = true)
                    DialogStatus.SAVE_PERMISSION_DENIED -> StatusText(R.string.share_save_permission_denied, isError = true)
                }

                // One Share button; the anchored menu carries both destinations. The in-flight
                // spinner replaces the label while either action runs.
                Box(modifier = Modifier.fillMaxWidth().padding(top = Spacing.md)) {
                    Button(
                        onClick = { menuExpanded = true },
                        enabled = activeAction == null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (activeAction != null) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.summary_share))
                        }
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_action)) },
                            onClick = {
                                menuExpanded = false
                                performShare()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.share_save_action)) },
                            onClick = {
                                menuExpanded = false
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                                    PackageManager.PERMISSION_GRANTED
                                ) {
                                    writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                                } else {
                                    performSave()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** The one platform action in flight; the share button disables while either runs. */
private enum class DialogAction { SHARE, SAVE }

/** Outcome shown in the dialog's single status slot. */
private enum class DialogStatus { NONE, SAVED, SHARE_FAILED, SAVE_FAILED, SAVE_PERMISSION_DENIED }

@Composable
private fun StatusText(@StringRes textRes: Int, isError: Boolean) {
    Text(
        stringResource(textRes),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm),
    )
}

/**
 * The preview IS the export. One [ShareCard] composition at full capture resolution (fixed
 * [ShareCardExportDensity], so its node measures 1080px wide), recorded into [graphicsLayer] on
 * every draw, then visually shrunk with a graphicsLayer scale — never re-laid-out at a smaller
 * size, which is what guarantees the shared PNG matches the preview pixel-for-pixel.
 */
@Composable
private fun ScaledCardPreview(
    data: ShareCardData,
    format: ShareCardFormat,
    graphicsLayer: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val cardWidthPx = with(ShareCardExportDensity) { format.width.toPx() }
    val cardHeightPx = with(ShareCardExportDensity) { format.height.toPx() }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(PREVIEW_AREA_HEIGHT),
        contentAlignment = Alignment.Center,
    ) {
        val scale = with(density) {
            minOf(maxWidth.toPx() / cardWidthPx, maxHeight.toPx() / cardHeightPx).coerceAtMost(1f)
        }
        val scaledSize = with(density) { DpSize((cardWidthPx * scale).toDp(), (cardHeightPx * scale).toDp()) }

        Box(
            modifier = Modifier.size(scaledSize.width, scaledSize.height),
            contentAlignment = Alignment.Center,
        ) {
            // Center-origin scale on a box the card overflows symmetrically -> the scaled card
            // lands exactly inside the sized wrapper above. wrapContentSize(unbounded = true)
            // measures the card without the preview slot's max constraints, so its requiredSize
            // reports the full capture resolution and graphicsLayer.record{} (which defaults to
            // the draw scope's size) captures the whole 1080px-wide card instead of a crop.
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .wrapContentSize(unbounded = true),
            ) {
                CompositionLocalProvider(LocalDensity provides ShareCardExportDensity) {
                    ShareCard(
                        data = data,
                        format = format,
                        modifier = Modifier.drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        },
                    )
                }
            }
        }
    }
}

// Slightly under the old sheet's 300dp so a STORY preview plus the dialog chrome still fits a
// small phone; the scale math above coerces either format into whatever height this allows.
private val PREVIEW_AREA_HEIGHT = 280.dp

