package dev.tomng.alarme.feature.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import dev.tomng.alarme.core.designsystem.theme.Spacing
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.feature.missions.camera.ReferencePhotoStore
import dev.tomng.alarme.feature.missions.ui.PhotoRegistrationDialog
import kotlinx.coroutines.launch

@Composable
internal fun PhotoFields(mission: Mission.Photo, onChange: (Mission.Photo) -> Unit) {
    val context = LocalContext.current
    val store = remember { ReferencePhotoStore(context) }
    val scope = rememberCoroutineScope()
    var taking by remember { mutableStateOf(false) }
    var unreadable by remember { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                scope.launch {
                    val path = store.import(uri)
                    unreadable = path == null
                    if (path != null) onChange(mission.copy(photoPath = path))
                }
            }
        }
    val preview by
        produceState<ImageBitmap?>(null, mission.photoPath) {
            value = store.load(mission.photoPath)?.image
        }

    ConfigSection(
        title = stringResource(R.string.config_photo),
        body = stringResource(R.string.config_photo_body),
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f).testTag("mission_config_photo"),
        ) {
            val image = preview
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = stringResource(R.string.config_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.clip(MaterialTheme.shapes.large),
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text =
                            stringResource(
                                if (unreadable) R.string.config_photo_unreadable
                                else R.string.config_photo_empty,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color =
                            if (unreadable) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s),
        ) {
            FilledTonalButton(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.weight(1f).testTag("mission_config_photo_pick"),
            ) {
                Icon(Icons.Outlined.PhotoLibrary, contentDescription = null)
                Text(
                    text = stringResource(R.string.config_photo_pick),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
            FilledTonalButton(
                onClick = { taking = true },
                modifier = Modifier.weight(1f).testTag("mission_config_photo_take"),
            ) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                Text(
                    text = stringResource(R.string.config_photo_take),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                )
            }
        }
    }
    if (taking) {
        PhotoRegistrationDialog(
            onCaptured = {
                taking = false
                unreadable = false
                onChange(mission.copy(photoPath = it))
            },
            onDismiss = { taking = false },
        )
    }
}
