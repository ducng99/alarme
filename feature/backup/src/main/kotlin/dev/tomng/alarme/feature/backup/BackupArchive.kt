package dev.tomng.alarme.feature.backup

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The decoded contents of a backup ZIP. */
data class ArchiveContents(val json: String, val audio: Map<String, ByteArray>)

/**
 * The ZIP container: one `alarms.json` document plus `audio/<name>` entries for the user's own
 * sounds. Reading is defensive — a truncated or hostile archive must fail with a readable message,
 * never with an OOM or a half-written database.
 */
object BackupArchive {

    const val ENTRY_JSON = "alarms.json"
    const val AUDIO_PREFIX = "audio/"

    /** Per-entry and total caps; a 5-minute recording is a few MB, so these are generous. */
    const val MAX_ENTRY_BYTES = 25 * 1024 * 1024

    const val MAX_TOTAL_AUDIO_BYTES = 200 * 1024 * 1024

    fun write(out: OutputStream, json: String, audio: Map<String, ByteArray>) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_JSON))
            zip.write(json.toByteArray())
            zip.closeEntry()
            for ((name, bytes) in audio) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }

    fun read(input: InputStream): ArchiveContents {
        val collected =
            try {
                collect(input)
            } catch (e: BackupFormatException) {
                throw e
            } catch (e: Exception) {
                throw BackupFormatException(BackupError.NOT_AN_ARCHIVE, e)
            }
        return collected.toContents()
    }

    private fun collect(input: InputStream): Collected {
        val collected = Collected()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                collected.accept(zip, entry)
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return collected
    }

    /** Accumulates entries as they stream past; validation happens once the stream ends. */
    private class Collected {
        private var json: String? = null
        private val audio = mutableMapOf<String, ByteArray>()
        private var totalAudio = 0L
        private var sawEntry = false

        fun accept(zip: ZipInputStream, entry: ZipEntry) {
            sawEntry = true
            if (entry.name == ENTRY_JSON) {
                json = zip.readCapped(MAX_ENTRY_BYTES).decodeToString()
                return
            }
            if (!entry.name.startsWith(AUDIO_PREFIX) || entry.isDirectory) return
            val bytes = zip.readCapped(MAX_ENTRY_BYTES)
            totalAudio += bytes.size
            if (totalAudio > MAX_TOTAL_AUDIO_BYTES)
                throw BackupFormatException(BackupError.TOO_LARGE)
            audio[entry.name] = bytes
        }

        fun toContents(): ArchiveContents {
            // A stream that yields no entries at all is not a ZIP (ZipInputStream is lenient about
            // garbage); a real archive that lacks the document is a different, more specific error.
            if (!sawEntry) throw BackupFormatException(BackupError.NOT_AN_ARCHIVE)
            val document = json ?: throw BackupFormatException(BackupError.MISSING_DATA)
            return ArchiveContents(document, audio)
        }
    }

    private fun ZipInputStream.readCapped(limit: Int): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(chunk)
            if (read <= 0) break
            buffer.write(chunk, 0, read)
            if (buffer.size() > limit) throw BackupFormatException(BackupError.TOO_LARGE)
        }
        return buffer.toByteArray()
    }
}
