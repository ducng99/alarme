package dev.tomng.alarme.feature.missions.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import dev.tomng.alarme.feature.missions.R
import dev.tomng.alarme.feature.missions.camera.BarcodeMissionUi
import dev.tomng.alarme.feature.missions.camera.MissionCameraPreview

/** Full-screen camera dialog used by the alarm editor to register the barcode by scanning it. */
@Composable
fun BarcodeRegistrationDialog(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
    uiFactory: (android.content.Context) -> BarcodeMissionUi = BarcodeMissionUi::default,
) {
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
        Surface(modifier = Modifier.fillMaxSize().testTag("barcode_registration")) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (granted) {
                    ScannerContent(uiFactory, onScanned)
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
) {
    val context = LocalContext.current
    val ui = remember { uiFactory(context) }
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
