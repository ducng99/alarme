package dev.tomng.alarme.feature.backup

import android.content.ContentResolver
import android.net.Uri
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.data.repository.BackupRepository
import dev.tomng.alarme.core.data.ringtone.RingtonePaths
import dev.tomng.alarme.core.model.backup.BackupAudioEntry
import dev.tomng.alarme.core.model.backup.BackupPayload
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import java.io.File
import java.io.IOException

/** What an export wrote, for the confirmation message. */
data class ExportSummary(val alarmCount: Int, val audioFileCount: Int)

/** What an import restored, for the confirmation message. */
data class ImportSummary(
    val alarmCount: Int,
    val audioFileCount: Int,
    val ringtoneRowCount: Int,
)

/** The two operations the UI drives; [BackupManager] is the production implementation. */
interface BackupOperations {
    suspend fun export(uri: Uri): ExportSummary

    suspend fun import(uri: Uri): ImportSummary
}

/**
 * Export/import of the alarm list as a single ZIP. Export copies the user's own sounds into the
 * archive (a backup that only references `file://` paths would be useless on another device);
 * import writes them back under fresh, collision-free names and rewrites every reference, then
 * replaces the alarm list and re-arms the scheduler.
 */
class BackupManager(
    private val contentResolver: ContentResolver,
    private val repository: BackupRepository,
    private val codec: BackupCodec,
    private val paths: RingtonePaths,
    private val clock: AppClock,
    private val scheduler: AlarmScheduler,
) : BackupOperations {

    override suspend fun export(uri: Uri): ExportSummary {
        val snapshot = repository.snapshot()
        val audio = mutableMapOf<String, ByteArray>()
        val entries = mutableListOf<BackupAudioEntry>()
        val localRingtones =
            snapshot.ringtones.filter {
                it.kind == RingtoneKind.MUSIC || it.kind == RingtoneKind.RECORD
            }
        for (ringtone in localRingtones) {
            val file = localFile(ringtone.uri)?.takeIf { it.isFile } ?: continue
            val entryName = uniqueEntryName(audio.keys, file.name)
            audio[entryName] = file.readBytes()
            entries += BackupAudioEntry(uri = ringtone.uri, entryName = entryName)
        }
        val payload =
            BackupPayload(
                exportedAt = clock.now(),
                alarms = snapshot.alarms,
                ringtones = snapshot.ringtones,
                audio = entries,
            )
        writeArchive(uri, codec.encode(payload), audio)
        return ExportSummary(alarmCount = snapshot.alarms.size, audioFileCount = audio.size)
    }

    override suspend fun import(uri: Uri): ImportSummary {
        val contents = readArchive(uri)
        val payload = codec.decode(contents.json)
        val remap = restoreAudio(payload, contents.audio)
        val ringtones =
            payload.ringtones.map { ringtone ->
                remap[ringtone.uri]?.let { ringtone.copy(uri = it) } ?: ringtone
            }
        val alarms =
            payload.alarms.map { alarm ->
                alarm.copy(
                    ringtoneUri = alarm.ringtoneUri?.let { remap[it] ?: it },
                    backupSoundUri = alarm.backupSoundUri?.let { remap[it] ?: it },
                )
            }
        val result = repository.restore(alarms, ringtones)
        scheduler.rearm(RescheduleReason.ADD_ALARM)
        return ImportSummary(
            alarmCount = result.alarms.size,
            audioFileCount = remap.size,
            ringtoneRowCount = result.ringtoneRowsAdded,
        )
    }

    /** Writes each archived sound to app storage; returns old URI -> new URI. */
    private fun restoreAudio(
        payload: BackupPayload,
        audio: Map<String, ByteArray>,
    ): Map<String, String> {
        val remap = mutableMapOf<String, String>()
        for (entry in payload.audio) {
            val bytes =
                audio[entry.entryName] ?: throw BackupFormatException(BackupError.MISSING_AUDIO)
            val kind =
                payload.ringtones.firstOrNull { it.uri == entry.uri }?.kind ?: RingtoneKind.MUSIC
            val target = paths.targetFile(kind, entry.entryName.substringAfterLast('/'))
            try {
                target.writeBytes(bytes)
            } catch (e: IOException) {
                throw BackupFormatException(BackupError.CANNOT_WRITE, e)
            }
            remap[entry.uri] = Uri.fromFile(target).toString()
        }
        return remap
    }

    private fun writeArchive(uri: Uri, json: String, audio: Map<String, ByteArray>) {
        val stream =
            try {
                contentResolver.openOutputStream(uri)
            } catch (e: Exception) {
                throw BackupFormatException(BackupError.CANNOT_OPEN, e)
            } ?: throw BackupFormatException(BackupError.CANNOT_OPEN)
        stream.use { BackupArchive.write(it, json, audio) }
    }

    private fun readArchive(uri: Uri): ArchiveContents {
        val stream =
            try {
                contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                throw BackupFormatException(BackupError.CANNOT_OPEN, e)
            } ?: throw BackupFormatException(BackupError.CANNOT_OPEN)
        return stream.use { BackupArchive.read(it) }
    }

    private fun localFile(uri: String): File? {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != "file") return null
        val path = parsed.path ?: return null
        return File(path)
    }

    private fun uniqueEntryName(taken: Set<String>, fileName: String): String {
        var candidate = BackupArchive.AUDIO_PREFIX + fileName
        var n = 1
        while (candidate in taken) {
            val base = fileName.substringBeforeLast('.')
            val ext = fileName.substringAfterLast('.', "")
            candidate = BackupArchive.AUDIO_PREFIX + "${base}_${n}.${ext}"
            n++
        }
        return candidate
    }
}
