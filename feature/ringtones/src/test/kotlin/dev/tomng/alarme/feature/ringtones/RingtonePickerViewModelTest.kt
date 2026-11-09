package dev.tomng.alarme.feature.ringtones

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.data.repository.SettingsRepository
import dev.tomng.alarme.core.data.ringtone.RingtonePaths
import dev.tomng.alarme.core.data.settings.AppSettings
import dev.tomng.alarme.core.data.settings.AudioOutputPreference
import dev.tomng.alarme.core.model.alarm.AutoDismissOption
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import dev.tomng.alarme.core.model.ringtone.RingtoneUris
import java.io.File
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class RingtonePickerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val catalog = FakeRingtoneCatalog()
    private val settings = RecordingSettings()
    private val engine = RecordingEngine()
    private val probe = StubProbe(AudioProbeResult(5_000, decodable = true))
    private val importer = StubImporter()
    private val preview = RecordingPreview()
    private lateinit var viewModel: RingtonePickerViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel =
            RingtonePickerViewModel(
                context = context,
                catalog = catalog,
                settings = settings,
                importer = importer,
                recorder = RingtoneRecorder(RingtonePaths(context), engine, probe, catalog),
                deviceTones =
                    DeviceRingtoneSource {
                        listOf("Over the horizon" to "content://media/alarms/1")
                    },
                preview = preview,
            )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `bundled tones are seeded and listed first`() =
        runTest(dispatcher) {
            runCurrent()

            val rows = viewModel.state.value.rows
            assertThat(rows.map { it.kind }.distinct()).containsExactly(RingtoneKind.BUNDLED)
            assertThat(rows.map { it.title })
                .containsExactly("Gentle chime", "Radar", "Sunrise")
                .inOrder()
        }

    @Test
    fun `device tones are exposed separately`() =
        runTest(dispatcher) {
            runCurrent()

            assertThat(viewModel.state.value.deviceTones.single().title)
                .isEqualTo("Over the horizon")
        }

    @Test
    fun `importing a valid document routes through the importer`() =
        runTest(dispatcher) {
            runCurrent()
            importer.nextResult = RingtoneImportResult.Imported("file:///x/song.mp3", "song")

            viewModel.import(listOf("content://picked/1"))
            runCurrent()

            assertThat(importer.uris).containsExactly("content://picked/1")
            assertThat(viewModel.state.value.message).isEqualTo(PickerMessage.Imported)
        }

    @Test
    fun `rejections surface their own message`() =
        runTest(dispatcher) {
            runCurrent()
            importer.nextResult = RingtoneImportResult.Rejected.TooLarge

            viewModel.import(listOf("content://picked/1"))
            runCurrent()

            assertThat(viewModel.state.value.message).isEqualTo(PickerMessage.RejectedTooLarge)
        }

    @Test
    fun `recording round-trips into the catalogue`() =
        runTest(dispatcher) {
            runCurrent()

            viewModel.toggleRecording()
            viewModel.toggleRecording()
            runCurrent()

            assertThat(viewModel.state.value.rows.map { it.kind }).contains(RingtoneKind.RECORD)
            assertThat(viewModel.state.value.message).isEqualTo(PickerMessage.Recorded)
        }

    @Test
    fun `preview plays through the seam`() =
        runTest(dispatcher) {
            runCurrent()

            viewModel.preview("file:///x/song.mp3")

            assertThat(preview.played).containsExactly("file:///x/song.mp3")
        }

    @Test
    fun `deleting a row clears a dangling default`() =
        runTest(dispatcher) {
            runCurrent()
            val id = catalog.insert(RingtoneKind.MUSIC, "file:///x/song.mp3", "song")
            settings.current = AppSettings(defaultRingtoneId = id)
            runCurrent()

            viewModel.delete(row(id, "file:///x/song.mp3"))
            runCurrent()

            assertThat(catalog.rows.map { it.id }).doesNotContain(id)
            assertThat(settings.savedDefaultId).isNull()
        }

    @Test
    fun `the default preference persists`() =
        runTest(dispatcher) {
            runCurrent()
            val id = catalog.insert(RingtoneKind.MUSIC, "file:///x/song.mp3", "song")
            runCurrent()

            viewModel.setDefault(row(id, "file:///x/song.mp3"))
            runCurrent()

            assertThat(settings.savedDefaultId).isEqualTo(id)
        }

    @Test
    fun `silent and random participate as choices`() =
        runTest(dispatcher) {
            runCurrent()

            assertThat(viewModel.state.value.pseudoChoices)
                .containsExactly(RingtoneUris.SILENT, RingtoneUris.RANDOM)
                .inOrder()
        }

    private fun row(id: Long, uri: String) =
        CatalogRingtone(id, RingtoneKind.MUSIC, uri, "t$id", Instant.fromEpochMilliseconds(id))
}

private class StubImporter : RingtoneImporter {
    var nextResult: RingtoneImportResult = RingtoneImportResult.Failed
    val uris = mutableListOf<String>()

    override suspend fun import(uri: String, kind: RingtoneKind): RingtoneImportResult {
        uris += uri
        return nextResult
    }
}

private class RecordingEngine : RecorderEngine {
    val files = mutableListOf<File>()

    override fun start(file: File) {
        files += file
        file.writeBytes(ByteArray(16))
    }

    override fun stop() {
        files.lastOrNull()?.writeBytes(ByteArray(64))
    }
}

private class RecordingPreview : PreviewPlayer {
    val played = mutableListOf<String>()

    override fun play(uri: String) {
        played += uri
    }

    override fun stop() = Unit
}

private class RecordingSettings : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())

    var current: AppSettings
        get() = state.value
        set(value) {
            state.value = value
        }

    var savedDefaultId: Long? = 99

    override val settings: Flow<AppSettings> = state

    override suspend fun setMute(enabled: Boolean, limit: Int) = Unit

    override suspend fun setAutoDismiss(option: AutoDismissOption) = Unit

    override suspend fun setAudioOutput(output: AudioOutputPreference) = Unit

    override suspend fun setPreventPowerOff(enabled: Boolean) = Unit

    override suspend fun setPreventUninstall(enabled: Boolean) = Unit

    override suspend fun setDefaultRingtoneId(id: Long?) {
        savedDefaultId = id
    }

    override suspend fun deviceId(): String = "dev"
}
