package dev.tomng.alarme.feature.missions.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R
import dev.tomng.alarme.feature.missions.camera.BarcodeMissionUi
import dev.tomng.alarme.feature.missions.camera.MissionCameraPreview
import dev.tomng.alarme.feature.missions.engine.BarcodeMatcher

/**
 * The barcode mission: scan until the scanned value equals the registered one - an exact match,
 * never a containment match.
 */
@Composable
fun BarcodeMissionScreen(
    mission: Mission.Barcode,
    callbacks: MissionStageCallbacks,
    uiFactory: (android.content.Context) -> BarcodeMissionUi = BarcodeMissionUi::default,
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

    val registered = mission.barcodeValue
    val scannedValue = scanned
    val matched = scannedValue != null && BarcodeMatcher.matches(registered, scannedValue)
    LaunchedEffect(matched) { if (matched) callbacks.onComplete() }

    val message =
        when {
            registered.isEmpty() -> stringResource(R.string.mission_barcode_none_registered)
            scannedValue != null && !matched -> stringResource(R.string.mission_barcode_mismatch)
            else -> stringResource(R.string.mission_barcode_instruction)
        }

    Surface(
        modifier = Modifier.fillMaxSize().testTag("mission:barcode"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ui.controller?.let { MissionCameraPreview(controller = it) }
            MissionCameraChrome(
                title = stringResource(R.string.mission_barcode_title),
                message = message,
                messageIsError = scannedValue != null && !matched,
            )
        }
    }
}
