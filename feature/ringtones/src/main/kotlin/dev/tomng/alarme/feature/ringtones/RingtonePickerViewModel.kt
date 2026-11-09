package dev.tomng.alarme.feature.ringtones

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomng.alarme.core.data.repository.RingtoneRepository
import dev.tomng.alarme.core.data.repository.SettingsRepository
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneUris
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
}

data class PickerState(
    val rows: List<CatalogRingtone> = emptyList(),
    val deviceTones: List<CatalogRingtone> = emptyList(),
    val defaultRingtoneId: Long? = null,
    val recording: Boolean = false,
    val pseudoChoices: List<String> = listOf(RingtoneUris.SILENT, RingtoneUris.RANDOM),
    val message: PickerMessage? = null,
)

/**
 * The sound catalogue screen's model: seeds the bundled tones, lists rows + device tones, and runs
 * the import / record / preview / delete / set-default actions.
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
) : ViewModel() {

    private val message = MutableStateFlow<PickerMessage?>(null)
    private val recording = MutableStateFlow(false)
    private val deviceRows = deviceTones.alarmTones()

    val state: StateFlow<PickerState> =
        combine(catalog.observeAll(), settings.settings, recording, message) {
                rows,
                prefs,
                isRecording,
                msg ->
                PickerState(
                    rows = rows,
                    deviceTones = deviceRows,
                    defaultRingtoneId = prefs.defaultRingtoneId,
                    recording = isRecording,
                    message = msg,
                )
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, PickerState())

    init {
        viewModelScope.launch { catalog.seedBundled(BundledRingtones.catalogueUris(context)) }
    }

    fun import(uris: List<String>) {
        viewModelScope.launch {
            uris.forEach { uri ->
                message.value =
                    when (importer.import(uri)) {
                        is RingtoneImportResult.Imported -> PickerMessage.Imported
                        RingtoneImportResult.Rejected.TooLarge -> PickerMessage.RejectedTooLarge
                        RingtoneImportResult.Rejected.TooLong -> PickerMessage.RejectedTooLong
                        RingtoneImportResult.Rejected.Undecodable ->
                            PickerMessage.RejectedUndecodable
                        RingtoneImportResult.Failed -> PickerMessage.ImportFailed
                    }
            }
        }
    }

    fun toggleRecording() {
        if (recording.value) {
            viewModelScope.launch {
                message.value =
                    when (recorder.stop()) {
                        is RingtoneRecorder.RecordingResult.Recorded -> PickerMessage.Recorded
                        RingtoneRecorder.RecordingResult.TooShort -> PickerMessage.RecordingTooShort
                        RingtoneRecorder.RecordingResult.Failed -> PickerMessage.RecordingFailed
                        RingtoneRecorder.RecordingResult.NotRecording -> null
                    }
                recording.value = false
            }
        } else {
            runCatching { recorder.start() }
                .onFailure { message.value = PickerMessage.RecordingFailed }
                .onSuccess { recording.value = true }
        }
    }

    fun preview(uri: String) = preview.play(uri)

    fun stopPreview() = preview.stop()

    fun delete(row: CatalogRingtone) {
        viewModelScope.launch {
            catalog.delete(row.id)
            if (state.value.defaultRingtoneId == row.id) settings.setDefaultRingtoneId(null)
            if (row.uri.startsWith("file://")) {
                java.io.File(row.uri.removePrefix("file://")).delete()
            }
        }
    }

    fun setDefault(row: CatalogRingtone) {
        viewModelScope.launch { settings.setDefaultRingtoneId(row.id) }
    }

    fun consumeMessage() {
        message.value = null
    }
}
