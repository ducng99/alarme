package dev.tomng.alarme.feature.ringtones

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text =
                            stringResource(
                                if (mode == PickerMode.Default) R.string.ringtone_title_default
                                else R.string.ringtone_title_alarm,
                            ),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).testTag("ringtone:title"),
                    )
                    TextButton(
                        onClick = callbacks.onDone,
                        modifier = Modifier.testTag("ringtone:done"),
                    ) {
                        Text(stringResource(R.string.ringtone_done))
                    }
                }
            }
            if (mode == PickerMode.Alarm) {
                item {
                    PseudoChoice(
                        R.string.ringtone_silent,
                        RingtoneUris.SILENT,
                        selectedUri,
                        callbacks,
                    )
                }
                item {
                    PseudoChoice(
                        R.string.ringtone_random,
                        RingtoneUris.RANDOM,
                        selectedUri,
                        callbacks,
                    )
                }
            }
            section(
                R.string.ringtone_section_bundled,
                state.rows.filter { it.kind == RingtoneKind.BUNDLED },
                mode,
                selectedUri,
                callbacks,
            )
            section(
                R.string.ringtone_section_device,
                state.deviceTones,
                mode,
                selectedUri,
                callbacks,
            )
            section(
                R.string.ringtone_section_mine,
                state.rows.filter {
                    it.kind == RingtoneKind.MUSIC || it.kind == RingtoneKind.RECORD
                },
                mode,
                selectedUri,
                callbacks,
                deletable = true,
            )
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = callbacks.onImportClick,
                        modifier = Modifier.weight(1f).testTag("ringtone:import"),
                    ) {
                        Text(stringResource(R.string.ringtone_import))
                    }
                    Button(
                        onClick = callbacks.onRecordToggle,
                        modifier = Modifier.weight(1f).testTag("ringtone:record"),
                    ) {
                        Text(
                            stringResource(
                                if (state.recording) R.string.ringtone_record_stop
                                else R.string.ringtone_record,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PseudoChoice(
    labelRes: Int,
    uri: String,
    selectedUri: String?,
    callbacks: RingtonePickerCallbacks,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable { callbacks.onSelect(uri, "") }
                .testTag(if (uri == RingtoneUris.SILENT) "ringtone:silent" else "ringtone:random"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selectedUri == uri, onClick = null)
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    @Suppress("UNUSED_PARAMETER") labelRes: Int,
    rows: List<CatalogRingtone>,
    mode: PickerMode,
    selectedUri: String?,
    callbacks: RingtonePickerCallbacks,
    deletable: Boolean = false,
) {
    if (rows.isEmpty()) return
    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
    item {
        Text(
            stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
    items(rows, key = { "${it.kind}:${it.id}:${it.uri}" }) { row ->
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .clickable { callbacks.onSelect(row.uri, row.title) }
                    .testTag("ringtone:row:${row.id}"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selectedUri == row.uri, onClick = null)
            Text(
                row.title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (mode == PickerMode.Default) {
                TextButton(
                    onClick = { callbacks.onSetDefault(row) },
                    modifier = Modifier.testTag("ringtone:default:${row.id}"),
                ) {
                    Text(stringResource(R.string.ringtone_set_default))
                }
            }
            if (deletable) {
                TextButton(
                    onClick = { callbacks.onDelete(row) },
                    modifier = Modifier.testTag("ringtone:delete:${row.id}"),
                ) {
                    Text(stringResource(R.string.ringtone_delete))
                }
            }
        }
    }
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
