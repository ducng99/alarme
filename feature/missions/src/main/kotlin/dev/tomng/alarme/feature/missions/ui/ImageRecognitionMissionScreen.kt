package dev.tomng.alarme.feature.missions.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.model.mission.ImageRecognitionObject
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R
import dev.tomng.alarme.feature.missions.camera.LabelMissionUi
import dev.tomng.alarme.feature.missions.camera.MissionCameraPreview
import dev.tomng.alarme.feature.missions.engine.ImageRecognitionVerifier

/**
 * The image-recognition mission: the configured object must be recognised inside the target box
 * with confidence >= 0.7. While a match is being confirmed the mission timer pauses.
 */
@Composable
fun ImageRecognitionMissionScreen(
    mission: Mission.ImageRecognition,
    callbacks: MissionStageCallbacks,
    uiFactory: (android.content.Context) -> LabelMissionUi = LabelMissionUi::default,
) {
    val context = LocalContext.current
    val ui = remember { uiFactory(context) }
    val labels by ui.scanner.labels.collectAsState()
    var confirming by remember { mutableStateOf(false) }

    DisposableEffect(ui) {
        onDispose {
            ui.scanner.close()
            ui.controller?.close()
        }
    }

    val targets = mission.objects.toSet()
    val matched = ImageRecognitionVerifier.match(labels, targets)

    LaunchedEffect(matched) {
        if (matched != null && !confirming) {
            confirming = true
            // A match is being confirmed: don't let the countdown kill the attempt.
            callbacks.onTimerStop()
            kotlinx.coroutines.delay(CONFIRM_MILLIS)
            callbacks.onComplete()
        }
    }

    Box(modifier = Modifier.fillMaxSize().testTag("mission:image")) {
        ui.controller?.let { MissionCameraPreview(controller = it) }
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text =
                    when {
                        targets.isEmpty() -> stringResource(R.string.mission_image_no_targets)
                        confirming -> stringResource(R.string.mission_image_hold_it)
                        else ->
                            stringResource(
                                R.string.mission_image_instruction,
                                targets.joinToString { it.displayName() },
                            )
                    },
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

private fun ImageRecognitionObject.displayName(): String = mlKitLabel

private const val CONFIRM_MILLIS = 600L
