package dev.tomng.alarme.feature.missions.camera

import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The live camera surface for the camera missions. The [controller] carries the analyser and the
 * capture use case; this composable only wires the preview and releases it with the UI.
 */
@Composable
fun MissionCameraPreview(controller: MissionCameraController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val previewView =
        remember(context) {
            PreviewView(context).apply {
                this.controller = controller.cameraController
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }
    AndroidView(factory = { previewView }, modifier = modifier.fillMaxSize())
    DisposableEffect(controller) { onDispose { controller.close() } }
}
