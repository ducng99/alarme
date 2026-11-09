package dev.tomng.alarme.core.model.backup

import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.serialization.InstantIsoSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * The `alarms.json` document inside an export ZIP: everything needed to rebuild the alarm list and
 * the user's own sounds. Settings and ring history are deliberately not part of it — the plan's
 * backup is "alarms and their audio", not a whole-device image.
 */
@Serializable
data class BackupPayload(
    val formatVersion: Int = CURRENT_VERSION,
    @Serializable(with = InstantIsoSerializer::class) val exportedAt: Instant,
    val alarms: List<Alarm> = emptyList(),
    val ringtones: List<CatalogRingtone> = emptyList(),
    /** Sound rows whose bytes travel in the ZIP's `audio/` directory. */
    val audio: List<BackupAudioEntry> = emptyList(),
) {
    companion object {
        /** Bumped only for incompatible changes; readers reject versions above their own. */
        const val CURRENT_VERSION = 1
    }
}

/**
 * Binds a catalogue row ([uri]) to the ZIP entry that carries its bytes ([entryName]), so import
 * never has to guess paths from the URI.
 */
@Serializable data class BackupAudioEntry(val uri: String, val entryName: String)

/** What a snapshot/restore deals with: the full alarm list plus the user's sound catalogue. */
data class BackupSnapshot(
    val alarms: List<Alarm> = emptyList(),
    val ringtones: List<CatalogRingtone> = emptyList(),
)

/** Result of a restore: the freshly persisted alarms and how many rows were written. */
data class RestoreResult(
    val alarms: List<Alarm>,
    val ringtoneRowsAdded: Int,
    val audioFilesRestored: Int = 0,
)
