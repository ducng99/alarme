package dev.tomng.alarme.feature.missions.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R
import dev.tomng.alarme.feature.missions.camera.MissionCameraPreview
import dev.tomng.alarme.feature.missions.camera.PhotoMissionUi
import kotlinx.coroutines.launch

/**
 * The photo mission: a reference photo is shown, and any photo completes the mission (the honour
 * system - the reference is a visual aid, not a comparison target).
 */
@Composable
fun PhotoMissionScreen(
    mission: Mission.Photo,
    callbacks: MissionStageCallbacks,
    uiFactory: (android.content.Context) -> PhotoMissionUi = PhotoMissionUi::default,
) {
    val context = LocalContext.current
    val ui = remember { uiFactory(context) }
    val scope = rememberCoroutineScope()

    DisposableEffect(ui) {
        onDispose {
            ui.capturer.close()
            ui.controller?.close()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize().testTag("mission:photo"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ui.controller?.let { MissionCameraPreview(controller = it) }
            MissionCameraChrome(
                title = stringResource(R.string.mission_photo_title),
                message =
                    if (mission.photoPath.isBlank()) {
                        stringResource(R.string.mission_photo_no_reference)
                    } else {
                        stringResource(R.string.mission_photo_reference)
                    },
                action = {
                    MissionPrimaryButton(
                        text = stringResource(R.string.mission_photo_capture),
                        onClick = {
                            scope.launch { if (ui.capturer.capture()) callbacks.onComplete() }
                        },
                        modifier = Modifier.testTag("mission:photo:capture"),
                    )
                },
            )
        }
    }
}
