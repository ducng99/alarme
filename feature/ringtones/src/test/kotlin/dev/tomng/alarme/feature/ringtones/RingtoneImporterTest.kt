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
class RingtoneImporterTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var paths: RingtonePaths
    private lateinit var source: FakeDocumentSource
    private lateinit var probe: FakeAudioProbe
    private lateinit var repo: FakeRingtoneCatalog
    private lateinit var importer: RingtoneImporter

    @Before
    fun setUp() {
        paths = RingtonePaths(context)
        source = FakeDocumentSource()
        probe = FakeAudioProbe()
        repo = FakeRingtoneCatalog()
        importer = CopyingRingtoneImporter(paths, source, probe, repo)
    }

    private fun music(bytes: Long = 256, durationMs: Long = 5_000, name: String = "song.mp3") {
        source.documents["content://picked/$name"] = FakeDocument(name, ByteArray(bytes.toInt()))
        probe.durations[name] = durationMs
    }

    @Test
    fun `imports a valid file into the music dir and catalogues it`() = runTest {
        music()

        val result = importer.import("content://picked/song.mp3")

        assertThat(result).isInstanceOf(RingtoneImportResult.Imported::class.java)
        val imported = result as RingtoneImportResult.Imported
        assertThat(imported.uri).startsWith("file://")
        assertThat(File(imported.uri.removePrefix("file://")).exists()).isTrue()
        assertThat(imported.uri).contains("/ringtone/music/song.mp3")
        assertThat(repo.rows.single().let { it.kind to it.title })
            .isEqualTo(RingtoneKind.MUSIC to "song")
    }

    @Test
    fun `a missing display name falls back to untitled`() = runTest {
        source.documents["content://picked/42"] = FakeDocument(null, ByteArray(64))
        probe.durations["untitled.mp3"] = 4_000

        val result = importer.import("content://picked/42")

        assertThat(result).isInstanceOf(RingtoneImportResult.Imported::class.java)
        assertThat((result as RingtoneImportResult.Imported).uri).endsWith("/untitled.mp3")
    }

    @Test
    fun `hostile names are sanitised and keep a sane extension`() = runTest {
        source.documents["content://x"] = FakeDocument("a/b\\c:con*t?.mp\"3<>|", ByteArray(64))
        probe.durations["a_b_c_con_t_.mp3"] = 4_000

        val result = importer.import("content://x")

        val uri = (result as RingtoneImportResult.Imported).uri
        assertThat(uri.substringAfterLast('/')).isEqualTo("a_b_c_con_t_.mp3")
        assertThat(uri).doesNotContain("..")
    }

    @Test
    fun `collisions get numbered suffixes instead of overwriting`() = runTest {
        music()
        importer.import("content://picked/song.mp3")
        source.documents["content://picked/song2.mp3"] = FakeDocument("song.mp3", ByteArray(32))
        probe.durations["song_1.mp3"] = 4_000

        val second = importer.import("content://picked/song2.mp3") as RingtoneImportResult.Imported

        assertThat(second.uri.substringAfterLast('/')).isEqualTo("song_1.mp3")
    }

    @Test
    fun `oversized files are rejected, deleted and never catalogued`() = runTest {
        music(bytes = CopyingRingtoneImporter.MAX_BYTES + 1)

        val result = importer.import("content://picked/song.mp3")

        assertThat(result).isEqualTo(RingtoneImportResult.Rejected.TooLarge)
        assertThat(repo.rows).isEmpty()
        assertThat(File(paths.directoryFor(RingtoneKind.MUSIC), "song.mp3").exists()).isFalse()
    }

    @Test
    fun `overlong audio is rejected, deleted and never catalogued`() = runTest {
        music(durationMs = CopyingRingtoneImporter.MAX_DURATION_MS + 1)

        val result = importer.import("content://picked/song.mp3")

        assertThat(result).isEqualTo(RingtoneImportResult.Rejected.TooLong)
        assertThat(repo.rows).isEmpty()
        assertThat(File(paths.directoryFor(RingtoneKind.MUSIC), "song.mp3").exists()).isFalse()
    }

    @Test
    fun `undecodable audio is rejected, deleted and never catalogued`() = runTest {
        music(durationMs = 0)

        val result = importer.import("content://picked/song.mp3")

        assertThat(result).isEqualTo(RingtoneImportResult.Rejected.Undecodable)
        assertThat(repo.rows).isEmpty()
        assertThat(File(paths.directoryFor(RingtoneKind.MUSIC), "song.mp3").exists()).isFalse()
    }

    @Test
    fun `an unreadable document reports a failure without leaving files behind`() = runTest {
        val result = importer.import("content://gone")

        assertThat(result).isEqualTo(RingtoneImportResult.Failed)
        assertThat(repo.rows).isEmpty()
        assertThat(paths.directoryFor(RingtoneKind.MUSIC).listFiles()).isEmpty()
    }

    @Test
    fun `recordings land in their own directory`() = runTest {
        source.documents["content://rec"] = FakeDocument("memo.m4a", ByteArray(64))
        probe.durations["memo.m4a"] = 3_000

        val result =
            importer.import("content://rec", kind = RingtoneKind.RECORD)
                as RingtoneImportResult.Imported

        assertThat(result.uri).contains("/ringtone/record/memo.m4a")
        assertThat(repo.rows.single().kind).isEqualTo(RingtoneKind.RECORD)
    }
}

data class FakeDocument(val name: String?, val bytes: ByteArray)

class FakeDocumentSource : DocumentSource {
    val documents = mutableMapOf<String, FakeDocument>()

    override fun displayName(uri: String): String? = documents[uri]?.name

    override fun open(uri: String): java.io.InputStream? =
        documents[uri]?.bytes?.let { java.io.ByteArrayInputStream(it) }
}

class FakeAudioProbe : AudioProbe {
    val durations = mutableMapOf<String, Long>()

    override fun probe(file: File): AudioProbeResult {
        val duration = durations[file.name] ?: 0L
        return if (duration > 0) AudioProbeResult(durationMs = duration, decodable = true)
        else AudioProbeResult(durationMs = 0, decodable = false)
    }
}
