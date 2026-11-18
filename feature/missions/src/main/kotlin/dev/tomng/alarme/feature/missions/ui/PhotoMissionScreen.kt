package dev.tomng.alarme.feature.missions.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R
import dev.tomng.alarme.feature.missions.camera.MissionCameraPreview
import dev.tomng.alarme.feature.missions.camera.PhotoMissionUi
import dev.tomng.alarme.feature.missions.camera.ReferencePhoto
import dev.tomng.alarme.feature.missions.engine.DHash
import kotlinx.coroutines.launch

private val REFERENCE_SIZE = 112.dp

private sealed interface ReferenceState {
    data object Loading : ReferenceState

    data object Missing : ReferenceState

    class Ready(val photo: ReferencePhoto) : ReferenceState
}

private enum class CaptureResult {
    NONE,
    MATCH,
    MISMATCH,
    FAILED,
}

/**
 * The photo mission: retake the registered reference photo. The capture's dHash must be within
 * [DHash.MATCH_THRESHOLD] bits of the reference's.
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
    val reference by
        produceState<ReferenceState>(ReferenceState.Loading, mission.photoPath) {
            value =
                ui.references.load(mission.photoPath)?.let { ReferenceState.Ready(it) }
                    ?: ReferenceState.Missing
        }
    var capturing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf(CaptureResult.NONE) }

    DisposableEffect(ui) {
        onDispose {
            ui.capturer.close()
            ui.controller?.close()
        }
    }

    val message =
        when {
            reference == ReferenceState.Missing -> stringResource(R.string.mission_photo_missing)
            result == CaptureResult.MISMATCH -> stringResource(R.string.mission_photo_mismatch)
            result == CaptureResult.FAILED -> stringResource(R.string.mission_photo_failed)
            else -> stringResource(R.string.mission_photo_instruction)
        }

    Surface(
        modifier = Modifier.fillMaxSize().testTag("mission:photo"),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ui.controller?.let { MissionCameraPreview(controller = it) }
            MissionCameraChrome(
                title = stringResource(R.string.mission_photo_title),
                message = message,
                messageIsError =
                    result == CaptureResult.MISMATCH ||
                        result == CaptureResult.FAILED ||
                        reference == ReferenceState.Missing,
                headerTrailing = {
                    (reference as? ReferenceState.Ready)?.let { ReferenceThumbnail(it.photo) }
                },
                action = {
                    MissionPrimaryButton(
                        text = stringResource(R.string.mission_photo_capture),
                        enabled = reference is ReferenceState.Ready && !capturing,
                        onClick = {
                            val target = (reference as? ReferenceState.Ready)?.photo
                            if (target != null) {
                                capturing = true
                                scope.launch {
                                    val hash = ui.capturer.capture()
                                    capturing = false
                                    result =
                                        when {
                                            hash == null -> CaptureResult.FAILED
                                            DHash.matches(target.hash, hash) -> CaptureResult.MATCH
                                            else -> CaptureResult.MISMATCH
                                        }
                                    if (result == CaptureResult.MATCH) callbacks.onComplete()
                                }
                            }
                        },
                        modifier = Modifier.testTag("mission:photo:capture"),
                    )
                },
            )
        }
    }
}

@Composable
private fun ReferenceThumbnail(photo: ReferencePhoto) {
    Image(
        bitmap = photo.image,
        contentDescription = stringResource(R.string.mission_photo_reference),
        contentScale = ContentScale.Crop,
        modifier =
            Modifier.size(REFERENCE_SIZE)
                .clip(MaterialTheme.shapes.large)
                .border(2.dp, MaterialTheme.colorScheme.surface, MaterialTheme.shapes.large)
                .testTag("mission:photo:reference"),
    )
}
