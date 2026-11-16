package dev.tomng.alarme.feature.ringtones

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomng.alarme.core.common.di.ApplicationScope
import dev.tomng.alarme.core.data.repository.RingtoneRepository
import dev.tomng.alarme.core.data.repository.SettingsRepository
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import dev.tomng.alarme.core.model.ringtone.RingtoneUris
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One-off outcomes surfaced as snackbars. */
enum class PickerMessage {
    Imported,
    RejectedTooLarge,
    RejectedTooLong,
    RejectedUndecodable,
    ImportFailed,
    Recorded,
    RecordingTooShort,
    RecordingFailed,
    Deleted,
}

data class PickerState(
    val rows: List<CatalogRingtone> = emptyList(),
    val deviceTones: List<CatalogRingtone> = emptyList(),
    val defaultUri: String? = null,
    val recording: Boolean = false,
    val previewing: String? = null,
    val pseudoChoices: List<String> = listOf(RingtoneUris.SILENT, RingtoneUris.RANDOM),
    val message: PickerMessage? = null,
)

private data class LocalState(
    val recording: Boolean = false,
    val message: PickerMessage? = null,
    val pendingDelete: CatalogRingtone? = null,
)

/**
 * The sound catalogue screen's model: seeds the bundled tones, lists rows and device tones, and
 * runs the import / record / preview / delete / set-default actions. Deletes are held back until
 * the undo window closes.
 */
@HiltViewModel
class RingtonePickerViewModel
@Inject
constructor(
    @ApplicationContext context: Context,
    private val catalog: RingtoneRepository,
    private val settings: SettingsRepository,
    private val importer: RingtoneImporter,
    private val recorder: RingtoneRecorder,
    deviceTones: DeviceRingtoneSource,
    private val preview: PreviewPlayer,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())
    private val deviceRows = deviceTones.alarmTones()

    val state: StateFlow<PickerState> =
        combine(catalog.observeAll(), settings.settings, local, preview.playing) {
                rows,
                prefs,
                localState,
                playing ->
                val pending = localState.pendingDelete?.id
                PickerState(
                    rows = rows.filter { it.kind != RingtoneKind.DEVICE_ALARM && it.id != pending },
                    deviceTones = deviceRows,
                    defaultUri = rows.firstOrNull { it.id == prefs.defaultRingtoneId }?.uri,
                    recording = localState.recording,
                    previewing = playing,
                    message = localState.message,
                )
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, PickerState())

    init {
        viewModelScope.launch { catalog.seedBundled(BundledRingtones.catalogueUris(context)) }
    }

    fun import(uris: List<String>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                val outcome =
                    when (importer.import(uri)) {
                        is RingtoneImportResult.Imported -> PickerMessage.Imported
                        RingtoneImportResult.Rejected.TooLarge -> PickerMessage.RejectedTooLarge
                        RingtoneImportResult.Rejected.TooLong -> PickerMessage.RejectedTooLong
                        RingtoneImportResult.Rejected.Undecodable ->
                            PickerMessage.RejectedUndecodable
                        RingtoneImportResult.Failed -> PickerMessage.ImportFailed
                    }
                local.update { it.copy(message = outcome) }
            }
        }
    }

    fun toggleRecording() {
        if (local.value.recording) {
            viewModelScope.launch {
                val outcome =
                    when (recorder.stop()) {
                        is RingtoneRecorder.RecordingResult.Recorded -> PickerMessage.Recorded
                        RingtoneRecorder.RecordingResult.TooShort -> PickerMessage.RecordingTooShort
                        RingtoneRecorder.RecordingResult.Failed -> PickerMessage.RecordingFailed
                        RingtoneRecorder.RecordingResult.NotRecording -> null
                    }
                local.update { it.copy(recording = false, message = outcome) }
            }
        } else {
            stopPreview()
            runCatching { recorder.start() }
                .onFailure { local.update { it.copy(message = PickerMessage.RecordingFailed) } }
                .onSuccess { local.update { it.copy(recording = true) } }
        }
    }

    fun preview(uri: String) = preview.play(uri)

    fun togglePreview(uri: String) {
        if (preview.playing.value == uri) preview.stop() else preview.play(uri)
    }

    fun stopPreview() = preview.stop()

    /**
     * Hides [row] at once; it is removed for good by [commitDelete] unless [undoDelete] runs first.
     */
    fun delete(row: CatalogRingtone) {
        commitDelete()
        if (preview.playing.value == row.uri) preview.stop()
        local.update { it.copy(pendingDelete = row, message = PickerMessage.Deleted) }
    }

    fun undoDelete() {
        local.update { it.copy(pendingDelete = null) }
    }

    fun commitDelete() {
        val row = local.value.pendingDelete ?: return
        local.update { it.copy(pendingDelete = null) }
        appScope.launch {
            catalog.delete(row.id)
            if (settings.settings.first().defaultRingtoneId == row.id) {
                settings.setDefaultRingtoneId(null)
            }
            if (row.uri.startsWith("file://")) {
                java.io.File(row.uri.removePrefix("file://")).delete()
            }
        }
    }

    /**
     * Device tones are not catalogued until they become the default, since the setting stores a row
     * id.
     */
    fun setDefault(row: CatalogRingtone) {
        viewModelScope.launch {
            val id =
                if (row.kind == RingtoneKind.DEVICE_ALARM) {
                    catalog.getAll().firstOrNull { it.uri == row.uri }?.id
                        ?: catalog.insert(RingtoneKind.DEVICE_ALARM, row.uri, row.title)
                } else {
                    row.id
                }
            settings.setDefaultRingtoneId(id)
        }
    }

    fun consumeMessage() {
        local.update { it.copy(message = null) }
    }

    override fun onCleared() {
        preview.stop()
        commitDelete()
    }
}
