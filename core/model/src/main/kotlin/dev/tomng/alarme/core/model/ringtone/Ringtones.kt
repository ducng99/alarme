package dev.tomng.alarme.core.model.ringtone

import dev.tomng.alarme.core.model.serialization.InstantIsoSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** Where a selectable sound lives. Persisted as the `Ringtone.type` string. */
@Serializable
enum class RingtoneKind {
    /** Synthesised tone shipped in the APK (`android.resource://`). */
    BUNDLED,

    /** System alarm tone (`content://`), catalogued when picked. */
    DEVICE_ALARM,

    /** User-imported audio, copied into app storage (`file://`). */
    MUSIC,

    /** User voice recording, captured into app storage (`file://`). */
    RECORD,

    /** No sound — pseudo-URI, never stored. */
    SILENT,

    /** Random pick across the catalogue at ring time — pseudo-URI, never stored. */
    RANDOM,
}

/** Pseudo-URI constants that survive the round-trip through `Alarm.ringtoneUri`. */
object RingtoneUris {
    const val SILENT: String = "uri_silent_ringtone"
    const val RANDOM: String = "uri_random_ringtone"
}

/** One catalogue row; `SILENT`/`RANDOM` never materialise as rows. */
@Serializable
data class CatalogRingtone(
    val id: Long,
    val kind: RingtoneKind,
    val uri: String,
    val title: String,
    @Serializable(with = InstantIsoSerializer::class) val createdAt: Instant,
)
