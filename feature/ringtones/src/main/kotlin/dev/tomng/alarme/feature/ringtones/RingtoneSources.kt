package dev.tomng.alarme.feature.ringtones

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import java.io.File

/** The tones shipped inside the APK; rows are seeded into the catalogue on first run. */
object BundledRingtones {

    val entries: List<Pair<String, String>> =
        listOf(
            "Gentle chime" to "alarme_gentle",
            "Radar" to "alarme_radar",
            "Sunrise" to "alarme_sunrise",
        )

    fun catalogueUris(context: Context): List<Pair<String, String>> = entries.map { (title, res) ->
        title to "android.resource://${context.packageName}/raw/$res"
    }
}

/** Reader seam over [RingtoneManager]; faked in tests. */
fun interface SystemRingtoneReader {
    fun alarmTones(): List<Pair<String, String>>
}

/** Lists the device's built-in alarm sounds without cataloguing them. */
class DeviceRingtoneSource(private val reader: SystemRingtoneReader) {

    fun alarmTones(): List<CatalogRingtone> =
        reader.alarmTones().mapIndexed { index, (title, uri) ->
            CatalogRingtone(
                id = -1L - index,
                kind = RingtoneKind.DEVICE_ALARM,
                uri = uri,
                title = title,
                createdAt = kotlin.time.Instant.fromEpochMilliseconds(0),
            )
        }
}

class RingtoneManagerReader(private val context: Context) : SystemRingtoneReader {

    override fun alarmTones(): List<Pair<String, String>> = runCatching {
        val manager = RingtoneManager(context)
        manager.setType(RingtoneManager.TYPE_ALARM)
        val cursor = manager.cursor
        buildList {
            while (cursor.moveToNext()) {
                add(
                    cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX) to
                        manager.getRingtoneUri(cursor.position).toString(),
                )
            }
        }
    }
        .getOrDefault(emptyList())
}

/** MediaMetadataRetriever-backed probe: duration + decodability of a local audio file. */
class MediaMetadataRetrieverProbe : AudioProbe {

    override fun probe(file: File): AudioProbeResult = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(file.absolutePath)
            val duration =
                retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            AudioProbeResult(durationMs = duration, decodable = duration > 0)
        }
    }
        .getOrDefault(AudioProbeResult(durationMs = 0, decodable = false))
}

private inline fun <T> MediaMetadataRetriever.use(block: (MediaMetadataRetriever) -> T): T =
    try {
        block(this)
    } finally {
        release()
    }
