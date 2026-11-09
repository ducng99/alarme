package dev.tomng.alarme.feature.ringtones

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.data.ringtone.RingtonePaths
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingtoneRecorderTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var paths: RingtonePaths
    private lateinit var engine: FakeRecorderEngine
    private lateinit var probe: StubProbe
    private lateinit var repo: FakeRingtoneCatalog
    private lateinit var recorder: RingtoneRecorder

    @Before
    fun setUp() {
        paths = RingtonePaths(context)
        engine = FakeRecorderEngine()
        probe = StubProbe(AudioProbeResult(3_000, decodable = true))
        repo = FakeRingtoneCatalog()
        recorder = RingtoneRecorder(paths, engine, probe, repo)
    }

    @Test
    fun `a recording lands in the record dir and is catalogued`() = runTest {
        probe.result = AudioProbeResult(3_000, decodable = true)
        recorder.start()

        val result = recorder.stop()

        val recorded = result as RingtoneRecorder.RecordingResult.Recorded
        assertThat(recorded.uri).contains("/ringtone/record/")
        assertThat(recorded.uri).endsWith(".m4a")
        assertThat(File(recorded.uri.removePrefix("file://"))).isEqualTo(engine.lastFile)
        assertThat(repo.rows.single().kind).isEqualTo(RingtoneKind.RECORD)
        assertThat(repo.rows.single().title).isEqualTo(recorded.title)
    }

    @Test
    fun `tap-length recordings are discarded`() = runTest {
        probe.result = AudioProbeResult(RingtoneRecorder.MIN_DURATION_MS - 1, decodable = true)
        recorder.start()

        val result = recorder.stop()

        assertThat(result).isEqualTo(RingtoneRecorder.RecordingResult.TooShort)
        assertThat(repo.rows).isEmpty()
        assertThat(paths.directoryFor(RingtoneKind.RECORD).listFiles()).isEmpty()
    }

    @Test
    fun `stop without start is a no-op`() = runTest {
        assertThat(recorder.stop()).isEqualTo(RingtoneRecorder.RecordingResult.NotRecording)
        assertThat(repo.rows).isEmpty()
    }

    @Test
    fun `a failed recording engine surfaces an error and leaves nothing`() = runTest {
        engine.failOnStop = true
        recorder.start()

        val result = recorder.stop()

        assertThat(result).isEqualTo(RingtoneRecorder.RecordingResult.Failed)
        assertThat(repo.rows).isEmpty()
        assertThat(paths.directoryFor(RingtoneKind.RECORD).listFiles()).isEmpty()
    }

    @Test
    fun `titles do not collide across recordings`() = runTest {
        recorder.start()
        val first = recorder.stop() as RingtoneRecorder.RecordingResult.Recorded
        recorder.start()
        val second = recorder.stop() as RingtoneRecorder.RecordingResult.Recorded

        assertThat(first.uri).isNotEqualTo(second.uri)
        assertThat(repo.rows).hasSize(2)
    }
}

internal class StubProbe(var result: AudioProbeResult) : AudioProbe {
    override fun probe(file: File): AudioProbeResult = result
}

class FakeRecorderEngine : RecorderEngine {
    var failOnStop = false
    var lastFile: File? = null

    override fun start(file: File) {
        lastFile = file
        file.writeBytes(ByteArray(16))
    }

    override fun stop() {
        if (failOnStop) {
            lastFile?.delete()
            error("recorder failed")
        }
        lastFile?.writeBytes(ByteArray(64))
    }
}
