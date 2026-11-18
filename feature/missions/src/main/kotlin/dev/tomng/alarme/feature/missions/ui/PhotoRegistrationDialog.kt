package dev.tomng.alarme.feature.missions.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.feature.missions.R
import dev.tomng.alarme.feature.missions.camera.MissionCameraController
import dev.tomng.alarme.feature.missions.camera.MissionCameraPreview
import dev.tomng.alarme.feature.missions.camera.ReferencePhotoStore
import kotlinx.coroutines.launch

/** Full-screen camera dialog used by the alarm editor to take the photo mission's reference. */
@Composable
fun PhotoRegistrationDialog(onCaptured: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
        }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize().testTag("photo_registration")) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (granted) {
                    CaptureContent(onCaptured)
                } else {
                    Text(
                        text = stringResource(R.string.photo_register_permission),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                    Text(stringResource(R.string.photo_register_cancel))
                }
            }
        }
    }
}

@Composable
private fun CaptureContent(onCaptured: (String) -> Unit) {
    val context = LocalContext.current
    val controller = remember { MissionCameraController(context) }
    val store = remember { ReferencePhotoStore(context) }
    val scope = rememberCoroutineScope()
    var capturing by remember { mutableStateOf(false) }
    DisposableEffect(controller) { onDispose { controller.close() } }

    Box(modifier = Modifier.fillMaxSize()) {
        MissionCameraPreview(controller = controller)
        MissionPrimaryButton(
            text = stringResource(R.string.photo_register_capture),
            enabled = !capturing,
            onClick = {
                capturing = true
                scope.launch {
                    val path = controller.captureBitmap()?.let { store.save(it) }
                    capturing = false
                    if (path != null) onCaptured(path)
                }
            },
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .padding(Spacing.xl)
                    .testTag("photo_registration_capture"),
        )
    }
}
