package dev.tomng.alarme.feature.ringtones

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Shuffle
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.tomng.alarme.core.designsystem.component.GroupCard
import dev.tomng.alarme.core.designsystem.component.GroupDivider
import dev.tomng.alarme.core.designsystem.component.LeadingIcon
import dev.tomng.alarme.core.designsystem.component.ScreenHeader
import dev.tomng.alarme.core.designsystem.component.SectionLabel
import dev.tomng.alarme.core.designsystem.component.SettingRow
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import dev.tomng.alarme.core.model.ringtone.RingtoneUris

/** Whether the picker edits one alarm's sound or the app-wide default. */
enum class PickerMode {
    Alarm,
    Default,
}

/** The picker's user intents; hosts persist the selection. */
class RingtonePickerCallbacks(
    val onSelect: (uri: String?, title: String) -> Unit,
    val onSetDefault: (row: CatalogRingtone) -> Unit,
    val onImportClick: () -> Unit,
    val onRecordToggle: () -> Unit,
    val onPreview: (uri: String) -> Unit,
    val onDelete: (row: CatalogRingtone) -> Unit,
    val onDone: () -> Unit,
)

@Composable
fun RingtonePickerScreen(
    mode: PickerMode,
    selectedUri: String?,
    onPersist: (uri: String?, title: String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RingtonePickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val messageText = state.message?.let { stringResource(it.textRes()) }

    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            viewModel.import(uris.map { it.toString() })
        }

    RingtonePickerContent(
        state = state,
        mode = mode,
        selectedUri = selectedUri,
        snackbarHostState = snackbar,
        callbacks =
            RingtonePickerCallbacks(
                onSelect = { uri, title ->
                    if (uri != null && uri != RingtoneUris.SILENT && uri != RingtoneUris.RANDOM) {
                        viewModel.preview(uri)
                    }
                    onPersist(uri, title)
                },
                onSetDefault = viewModel::setDefault,
                onImportClick = { importLauncher.launch(arrayOf("audio/*")) },
                onRecordToggle = viewModel::toggleRecording,
                onPreview = viewModel::preview,
                onDelete = viewModel::delete,
                onDone = onDone,
            ),
        modifier = modifier,
    )
}

@Composable
fun RingtonePickerContent(
    state: PickerState,
    mode: PickerMode,
    selectedUri: String?,
    callbacks: RingtonePickerCallbacks,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { PickerTopBar(mode = mode, onDone = callbacks.onDone) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).testTag("ringtone:list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (mode == PickerMode.Alarm) {
                item { SectionLabel(stringResource(R.string.ringtone_section_quick)) }
                item {
                    GroupCard {
                        PseudoChoice(
                            labelRes = R.string.ringtone_silent,
                            uri = RingtoneUris.SILENT,
                            selectedUri = selectedUri,
                            icon = Icons.Outlined.VolumeOff,
                            callbacks = callbacks,
                        )
                        GroupDivider()
                        PseudoChoice(
                            labelRes = R.string.ringtone_random,
                            uri = RingtoneUris.RANDOM,
                            selectedUri = selectedUri,
                            icon = Icons.Outlined.Shuffle,
                            callbacks = callbacks,
                        )
                    }
                }
            }
            section(
                R.string.ringtone_section_bundled,
                state.rows.filter { it.kind == RingtoneKind.BUNDLED },
                mode,
                selectedUri,
                callbacks,
                defaultId = state.defaultRingtoneId,
            )
            section(
                R.string.ringtone_section_device,
                state.deviceTones,
                mode,
                selectedUri,
                callbacks,
                defaultId = state.defaultRingtoneId,
            )
            section(
                R.string.ringtone_section_mine,
                state.rows.filter {
                    it.kind == RingtoneKind.MUSIC || it.kind == RingtoneKind.RECORD
                },
                mode,
                selectedUri,
                callbacks,
                defaultId = state.defaultRingtoneId,
                deletable = true,
            )
            item { SectionLabel(stringResource(R.string.ringtone_section_add)) }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = callbacks.onImportClick,
                        modifier = Modifier.weight(1f).testTag("ringtone:import"),
                    ) {
                        Icon(
                            Icons.Outlined.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(stringResource(R.string.ringtone_import), maxLines = 1)
                    }
                    Button(
                        onClick = callbacks.onRecordToggle,
                        modifier = Modifier.weight(1f).testTag("ringtone:record"),
                    ) {
                        Icon(
                            Icons.Outlined.Mic,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                        Text(
                            text =
                                stringResource(
                                    if (state.recording) R.string.ringtone_record_stop
                                    else R.string.ringtone_record,
                                ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerTopBar(mode: PickerMode, onDone: () -> Unit) {
    ScreenHeader(
        title =
            stringResource(
                if (mode == PickerMode.Default) R.string.ringtone_title_default
                else R.string.ringtone_title_alarm,
            ),
        navigation = {
            IconButton(onClick = onDone, modifier = Modifier.testTag("ringtone:done")) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.ringtone_done),
                )
            }
        },
        modifier = Modifier.statusBarsPadding().testTag("ringtone:title"),
    )
}

@Composable
private fun PseudoChoice(
    labelRes: Int,
    uri: String,
    selectedUri: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    callbacks: RingtonePickerCallbacks,
) {
    val selected = selectedUri == uri
    SettingRow(
        title = stringResource(labelRes),
        leading = { LeadingIcon(icon) },
        onClick = { callbacks.onSelect(uri, "") },
        trailing = { RadioButton(selected = selected, onClick = null) },
        modifier =
            Modifier.testTag(
                if (uri == RingtoneUris.SILENT) "ringtone:silent" else "ringtone:random",
            ),
    )
}

private fun LazyListScope.section(
    labelRes: Int,
    rows: List<CatalogRingtone>,
    mode: PickerMode,
    selectedUri: String?,
    callbacks: RingtonePickerCallbacks,
    defaultId: Long? = null,
    deletable: Boolean = false,
) {
    if (rows.isEmpty()) return
    item { SectionLabel(stringResource(labelRes)) }
    item {
        GroupCard {
            rows.forEachIndexed { index, row ->
                if (index > 0) GroupDivider()
                RingtoneRow(
                    row = row,
                    mode = mode,
                    selected = selectedUri == row.uri,
                    defaultId = defaultId,
                    deletable = deletable,
                    callbacks = callbacks,
                )
            }
        }
    }
}

@Composable
private fun RingtoneRow(
    row: CatalogRingtone,
    mode: PickerMode,
    selected: Boolean,
    defaultId: Long?,
    deletable: Boolean,
    callbacks: RingtonePickerCallbacks,
) {
    SettingRow(
        title = row.title,
        supporting =
            stringResource(
                if (row.kind == RingtoneKind.RECORD) R.string.ringtone_kind_recording
                else R.string.ringtone_kind_sound,
            ),
        leading = {
            LeadingIcon(
                if (row.kind == RingtoneKind.RECORD) Icons.Outlined.Mic
                else Icons.Outlined.MusicNote,
            )
        },
        onClick = {
            if (mode == PickerMode.Default) callbacks.onSetDefault(row)
            else callbacks.onSelect(row.uri, row.title)
        },
        trailing = {
            if (deletable) {
                IconButton(
                    onClick = { callbacks.onDelete(row) },
                    modifier = Modifier.testTag("ringtone:delete:${row.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.ringtone_delete),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RadioButton(
                selected = if (mode == PickerMode.Default) row.id == defaultId else selected,
                onClick = null,
                modifier = Modifier.testTag("ringtone:default:${row.id}"),
            )
        },
        modifier = Modifier.testTag("ringtone:row:${row.id}"),
    )
}

private fun PickerMessage.textRes(): Int =
    when (this) {
        PickerMessage.Imported -> R.string.ringtone_msg_imported
        PickerMessage.RejectedTooLarge -> R.string.ringtone_msg_too_large
        PickerMessage.RejectedTooLong -> R.string.ringtone_msg_too_long
        PickerMessage.RejectedUndecodable -> R.string.ringtone_msg_undecodable
        PickerMessage.ImportFailed -> R.string.ringtone_msg_import_failed
        PickerMessage.Recorded -> R.string.ringtone_msg_recorded
        PickerMessage.RecordingTooShort -> R.string.ringtone_msg_recording_too_short
        PickerMessage.RecordingFailed -> R.string.ringtone_msg_recording_failed
    }
