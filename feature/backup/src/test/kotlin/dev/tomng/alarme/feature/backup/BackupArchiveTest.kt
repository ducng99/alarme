package dev.tomng.alarme.feature.backup

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Test

class BackupArchiveTest {

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun `write then read round-trips json and audio`() {
        val out = ByteArrayOutputStream()

        BackupArchive.write(
            out,
            json = """{"a":1}""",
            audio = mapOf("audio/a.mp3" to byteArrayOf(1, 2, 3)),
        )
        val contents = BackupArchive.read(ByteArrayInputStream(out.toByteArray()))

        assertThat(contents.json).isEqualTo("""{"a":1}""")
        assertThat(contents.audio.keys).containsExactly("audio/a.mp3")
        assertThat(contents.audio["audio/a.mp3"]).isEqualTo(byteArrayOf(1, 2, 3))
    }

    @Test
    fun `a non-zip file is rejected`() {
        val error = runCatching {
            BackupArchive.read(ByteArrayInputStream("hello".toByteArray()))
        }
            .exceptionOrNull()

        assertThat((error as BackupFormatException).error).isEqualTo(BackupError.NOT_AN_ARCHIVE)
    }

    @Test
    fun `an archive without alarms json is rejected`() {
        val bytes = zipOf("audio/a.mp3" to byteArrayOf(1))

        val error = runCatching {
            BackupArchive.read(ByteArrayInputStream(bytes))
        }
            .exceptionOrNull()

        assertThat((error as BackupFormatException).error).isEqualTo(BackupError.MISSING_DATA)
    }

    @Test
    fun `an oversized entry is rejected`() {
        val bytes = zipOf(BackupArchive.ENTRY_JSON to ByteArray(BackupArchive.MAX_ENTRY_BYTES + 1))

        val error = runCatching {
            BackupArchive.read(ByteArrayInputStream(bytes))
        }
            .exceptionOrNull()

        assertThat((error as BackupFormatException).error).isEqualTo(BackupError.TOO_LARGE)
    }

    @Test
    fun `directories and unrelated entries are ignored`() {
        val bytes =
            zipOf(
                "audio/" to ByteArray(0),
                "readme.txt" to "hi".toByteArray(),
                BackupArchive.ENTRY_JSON to "{}".toByteArray(),
            )

        val contents = BackupArchive.read(ByteArrayInputStream(bytes))

        assertThat(contents.json).isEqualTo("{}")
        assertThat(contents.audio).isEmpty()
    }
}
