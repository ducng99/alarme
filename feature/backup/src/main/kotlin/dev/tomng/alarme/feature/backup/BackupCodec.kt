package dev.tomng.alarme.feature.backup

import dev.tomng.alarme.core.model.backup.BackupPayload
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The `alarms.json` half of a backup: encode on export, decode + validate on import. Unknown fields
 * are ignored so a file written by a slightly newer build still imports; a *newer format version*
 * is rejected instead, because its semantics may differ.
 */
class BackupCodec(
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) {

    fun encode(payload: BackupPayload): String = json.encodeToString(payload)

    fun decode(text: String): BackupPayload {
        val payload =
            try {
                json.decodeFromString<BackupPayload>(text)
            } catch (e: SerializationException) {
                throw BackupFormatException(BackupError.INVALID_DATA, e)
            } catch (e: IllegalArgumentException) {
                // Domain invariants (hour range, snooze limits, …) reject corrupt values.
                throw BackupFormatException(BackupError.INVALID_DATA, e)
            }
        validate(payload)
        return payload
    }

    private fun validate(payload: BackupPayload) {
        if (payload.formatVersion > BackupPayload.CURRENT_VERSION) {
            throw BackupFormatException(BackupError.NEWER_VERSION)
        }
        if (payload.alarms.size > MAX_ALARMS) {
            throw BackupFormatException(BackupError.TOO_MANY_ALARMS)
        }
    }

    companion object {
        /** Sanity cap: a real alarm list is nowhere near this, a hostile file can be. */
        const val MAX_ALARMS = 500
    }
}
