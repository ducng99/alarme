package dev.tomng.alarme.feature.ringtones

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import dev.tomng.alarme.core.data.ringtone.RingtonePaths
import java.io.File
import java.io.InputStream

/** Read access to picked SAF documents; faked in tests. */
interface DocumentSource {
    fun displayName(uri: String): String?

    fun open(uri: String): InputStream?
}

class ContentResolverDocumentSource(private val resolver: ContentResolver) : DocumentSource {

    override fun displayName(uri: String): String? = runCatching {
        resolver.query(Uri.parse(uri), null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }
        .getOrNull()

    override fun open(uri: String): InputStream? = runCatching {
        resolver.openInputStream(Uri.parse(uri))
    }
        .getOrNull()
}

/** Result of probing an audio file for duration and decodability. */
data class AudioProbeResult(val durationMs: Long, val decodable: Boolean)

/** Extractor seam (MediaMetadataRetriever in production); faked in tests. */
interface AudioProbe {
    fun probe(file: File): AudioProbeResult
}

/** Outcome of a copy-import; rejected/failed imports leave nothing behind. */
sealed interface RingtoneImportResult {
    data class Imported(val uri: String, val title: String) : RingtoneImportResult

    enum class Rejected : RingtoneImportResult {
        TooLarge,
        TooLong,
        Undecodable,
    }

    data object Failed : RingtoneImportResult
}

/** Import seam over the copy pipeline. */
interface RingtoneImporter {
    suspend fun import(
        uri: String,
        kind: dev.tomng.alarme.core.model.ringtone.RingtoneKind,
    ): RingtoneImportResult

    suspend fun import(uri: String): RingtoneImportResult =
        import(uri, dev.tomng.alarme.core.model.ringtone.RingtoneKind.MUSIC)
}

/**
 * Copies a picked audio document into app storage (a persisted `content://` grant can still be
 * revoked later), validates it, and catalogues the result. Any failure deletes the partial file and
 * writes no catalogue row — a corrupt or hours-long file must not become a silent alarm.
 */
class CopyingRingtoneImporter(
    private val paths: RingtonePaths,
    private val source: DocumentSource,
    private val probe: AudioProbe,
    private val catalog: dev.tomng.alarme.core.data.repository.RingtoneRepository,
) : RingtoneImporter {
    override suspend fun import(
        uri: String,
        kind: dev.tomng.alarme.core.model.ringtone.RingtoneKind,
    ): RingtoneImportResult {
        val name = source.displayName(uri) ?: "untitled"
        val target = paths.targetFile(kind, name)
        val stream = source.open(uri) ?: return RingtoneImportResult.Failed

        var copied = 0L
        val copySucceeded = runCatching {
            stream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(COPY_BUFFER)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        copied += read
                        if (copied > MAX_BYTES) error("size cap exceeded")
                        output.write(buffer, 0, read)
                    }
                }
            }
        }
            .isSuccess
        if (!copySucceeded) {
            target.delete()
            return if (copied > MAX_BYTES) RingtoneImportResult.Rejected.TooLarge
            else RingtoneImportResult.Failed
        }

        val result = probe.probe(target)
        val rejection =
            when {
                !result.decodable -> RingtoneImportResult.Rejected.Undecodable
                result.durationMs > MAX_DURATION_MS -> RingtoneImportResult.Rejected.TooLong
                else -> null
            }
        if (rejection != null) {
            target.delete()
            return rejection
        }

        val storedUri = Uri.fromFile(target).toString()
        val title = target.nameWithoutExtension
        catalog.insert(kind, storedUri, title)
        return RingtoneImportResult.Imported(storedUri, title)
    }

    companion object {
        /** 20 MB — generous for alarm clips while excluding accidental full mixes. */
        const val MAX_BYTES: Long = 20L * 1024 * 1024

        /** 5 minutes — an alarm longer than this is a mistake, not a wake-up call. */
        const val MAX_DURATION_MS: Long = 5L * 60 * 1000

        private const val COPY_BUFFER = 2048
    }
}
