package dev.tomng.alarme.feature.missions.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import dev.tomng.alarme.feature.missions.R
import dev.tomng.alarme.feature.missions.camera.BarcodeMissionUi
import dev.tomng.alarme.feature.missions.camera.MissionCameraPreview

internal const val SCAN_BOX_FRACTION = 0.7f

/**
 * Full-screen camera dialog used by the alarm editor to register the barcode inside a centered scan
 * box.
 */
@Composable
fun BarcodeRegistrationDialog(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
    uiFactory: (android.content.Context) -> BarcodeMissionUi = BarcodeMissionUi::default,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
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
        Surface(modifier = Modifier.fillMaxSize().testTag("barcode_registration")) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val widthPx = with(density) { maxWidth.roundToPx() }
                val heightPx = with(density) { maxHeight.roundToPx() }
                val boxSidePx = (minOf(widthPx, heightPx) * SCAN_BOX_FRACTION).toInt()
                if (granted) {
                    ScannerContent(uiFactory, onScanned, widthPx, heightPx, boxSidePx)
                    ScanBoxOverlay(boxSidePx.toFloat())
                } else {
                    Text(
                        text = stringResource(R.string.barcode_register_permission),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                    Text(stringResource(R.string.barcode_register_cancel))
                }
                Text(
                    text = stringResource(R.string.barcode_register_instruction),
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun ScannerContent(
    uiFactory: (android.content.Context) -> BarcodeMissionUi,
    onScanned: (String) -> Unit,
    viewWidthPx: Int,
    viewHeightPx: Int,
    boxSidePx: Int,
) {
    val context = LocalContext.current
    val ui = remember { uiFactory(context) }
    LaunchedEffect(ui, viewWidthPx, viewHeightPx, boxSidePx) {
        ui.scanner.setScanArea(viewWidthPx, viewHeightPx, boxSidePx)
    }
    val scanned by ui.scanner.scannedValue.collectAsState()
    DisposableEffect(ui) {
        onDispose {
            ui.scanner.close()
            ui.controller?.close()
        }
    }
    LaunchedEffect(scanned) { scanned?.let(onScanned) }
    ui.controller?.let { MissionCameraPreview(controller = it) }
}

@Composable
internal fun ScanBoxOverlay(boxSidePx: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val box =
            Rect(
                offset = Offset((size.width - boxSidePx) / 2f, (size.height - boxSidePx) / 2f),
                size = Size(boxSidePx, boxSidePx),
            )
        val corner = CornerRadius(16.dp.toPx())
        val hole = Path().apply { addRoundRect(RoundRect(box, corner)) }
        clipPath(hole, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.6f))
        }
        drawRoundRect(
            color = Color.White,
            topLeft = box.topLeft,
            size = box.size,
            cornerRadius = corner,
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}
