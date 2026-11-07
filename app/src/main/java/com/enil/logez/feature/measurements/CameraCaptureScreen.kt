package com.enil.logez.feature.measurements

import android.net.Uri
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Camera
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.enil.logez.R
import com.enil.logez.core.designsystem.Spacing
import java.io.File
import java.util.UUID
import com.enil.logez.core.data.media.MediaFileJanitor

/**
 * M22a: in-app camera capture for progress photos. No overlay of a previous photo this pass (a
 * deliberate scope cut — see `Plans/` in the vault) — this is a plain preview + shutter screen.
 * The caller must already hold CAMERA permission before navigating here (via
 * [rememberRequestCameraPermission]); this screen never handles a permission denial itself.
 *
 * Captures to a [android.content.Context.getCacheDir] temp file, never directly into permanent
 * storage — [onCaptured] hands the caller a `file://` [Uri] to that temp file, and it is the
 * caller's job (via `ProgressPhotoStore.copyToAppStorage`) to downsample/re-encode it into
 * permanent storage and delete the temp file afterward, success or failure.
 */
@Composable
fun CameraCaptureScreen(onCaptured: (Uri) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember { ContextCompat.getMainExecutor(context) }
    val previewView = remember { PreviewView(context) }
    val imageCaptureState = remember { mutableStateOf<ImageCapture?>(null) }

    // The classic leaked-camera bug: CameraX ties bound use cases to the lifecycle owner's own
    // STOP/DESTROY, but a NavHost pop doesn't reliably drive that lifecycle transition before this
    // composable leaves composition, so unbindAll() is called explicitly here rather than trusted
    // to happen implicitly.
    DisposableEffect(lifecycleOwner) {
        var disposed = false
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                // Guards the real race: if the user backs out before this future resolves, binding
                // now would attach a camera to a screen that already left composition, and nothing
                // would ever unbind it (onDispose below already ran).
                if (disposed) return@addListener
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val capture = ImageCapture.Builder().build()
                imageCaptureState.value = capture
                provider.unbindAll()
                runCatching { provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture) }
            },
            mainExecutor,
        )
        onDispose {
            disposed = true
            runCatching { providerFuture.get().unbindAll() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        IconButton(onClick = onCancel, modifier = Modifier.align(Alignment.TopStart).padding(Spacing.md)) {
            Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.action_cancel))
        }
        FloatingActionButton(
            onClick = {
                val capture = imageCaptureState.value ?: return@FloatingActionButton
                val tempFile = File(context.cacheDir, "${MediaFileJanitor.CAMERA_CAPTURE_PREFIX}${UUID.randomUUID()}.jpg")
                capture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(tempFile).build(),
                    mainExecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            onCaptured(Uri.fromFile(tempFile))
                        }

                        // Left on the capture screen so the user can retry — no error UI on this
                        // pass beyond "the shutter didn't visibly do anything," matching the scope
                        // cut already made for the overlay feature.
                        override fun onError(exception: ImageCaptureException) = Unit
                    },
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(Spacing.xl),
        ) {
            Icon(Icons.Outlined.Camera, contentDescription = stringResource(R.string.measurements_capture_photo))
        }
    }
}
