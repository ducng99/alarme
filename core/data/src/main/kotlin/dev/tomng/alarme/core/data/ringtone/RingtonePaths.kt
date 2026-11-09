package dev.tomng.alarme.core.data.ringtone

import android.content.Context
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import java.io.File

/**
 * Owns the ringtone storage layout: `<files>/ringtone/music` for imported audio and
 * `<files>/ringtone/record` for voice recordings, plus file-name sanitising and collision
 * numbering.
 */
class RingtonePaths(private val context: Context) {

    fun directoryFor(kind: RingtoneKind): File =
        File(
                File(context.filesDir, BASE_DIR),
                when (kind) {
                    RingtoneKind.RECORD -> "record"
                    else -> "music"
                },
            )
            .apply { mkdirs() }

    /**
     * Strips path separators and control characters, forces a known audio extension, and appends
     * `_1`, `_2`… while a file with that name already exists.
     */
    fun targetFile(kind: RingtoneKind, rawName: String): File {
        val dir = directoryFor(kind)
        val sanitised = sanitize(rawName)
        var candidate = File(dir, sanitised)
        var n = 1
        while (candidate.exists()) {
            val base = sanitised.substringBeforeLast('.')
            val ext = sanitised.substringAfterLast('.', "")
            candidate = File(dir, "${base}_${n}.${ext}")
            n++
        }
        return candidate
    }

    fun sanitize(rawName: String): String {
        val cleaned =
            rawName.replace(Regex("[\\p{Cntrl}\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "untitled" }
        val name = cleaned.substringBeforeLast('.')
        val ext = cleaned.substringAfterLast('.', "").lowercase()
        return if (ext in KNOWN_EXTENSIONS) cleaned else "${name}.mp3"
    }

    companion object {
        private const val BASE_DIR = "ringtone"
        private val KNOWN_EXTENSIONS =
            setOf("mp3", "m4a", "aac", "ogg", "oga", "wav", "flac", "opus", "mid", "amr", "3gp")
    }
}
