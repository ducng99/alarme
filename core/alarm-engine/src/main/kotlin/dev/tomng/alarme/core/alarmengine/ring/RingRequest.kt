package dev.tomng.alarme.core.alarmengine.ring

import dev.tomng.alarme.core.data.settings.AppSettings
import dev.tomng.alarme.core.data.settings.AudioOutputPreference
import dev.tomng.alarme.core.model.alarm.Alarm

/**
 * The complete specification of one ring. Audio/vibration semantics are resolved downstream by the
 * audio engine.
 */
data class RingRequest(
    val audioUri: String?,
    /** `true` -> STREAM_ALARM; `false` -> STREAM_MUSIC (external routing). */
    val useAlarmStream: Boolean,
    val volumeRatio: Float,
    val crescendo: Crescendo? = null,
    val backupSound: BackupSound? = null,
    val tts: Tts? = null,
    val vibrate: Boolean,
    val scheduleId: String? = null,
    val isPreview: Boolean = false,
) {
    /** Ramp the volume to full over [durationSeconds] ("gentle wake-up"). */
    data class Crescendo(val durationSeconds: Int)

    /** A second, louder sound that starts if the user is unresponsive. */
    data class BackupSound(
        val backupUri: String,
        val delaySeconds: Int,
    )

    /** Spoken announcements, repeated on [repeatIntervalMillis]. */
    data class Tts(
        val label: String,
        val speakLabel: Boolean,
        val speakTime: Boolean,
        val repeatIntervalMillis: Long,
    )
}

/**
 * Builds the [RingRequest] from the alarm + app settings + current audio routing, exactly as
 * `AlarmService.f()` computes it before the host starts.
 */
object RingRequestBuilder {

    /** System fallback when the alarm has no ringtone set (full resolver lands in M11). */
    const val DEFAULT_ALARM_URI: String = "content://settings/system/alarm_sound"

    fun build(alarm: Alarm, settings: AppSettings, hasExternalOutput: Boolean): RingRequest {
        val volume = alarm.volumeRatio
        val soundEnabled =
            (!hasExternalOutput || settings.audioOutput == AudioOutputPreference.ALARM_STREAM) &&
                volume > 0f

        return RingRequest(
            audioUri =
                when (val uri = alarm.ringtoneUri) {
                    null -> DEFAULT_ALARM_URI
                    dev.tomng.alarme.core.model.ringtone.RingtoneUris.SILENT -> null
                    else -> uri
                },
            useAlarmStream = soundEnabled,
            volumeRatio = volume,
            crescendo =
                if (alarm.crescendoOption.enabled) {
                    RingRequest.Crescendo(durationSeconds = alarm.crescendoOption.seconds)
                } else {
                    null
                },
            backupSound =
                if (alarm.backupSoundEnabled && volume > 0f) {
                    // Default: bundled loud sound after 40 s (strings
                    // "unresponsive
                    // for 40 seconds")
                    RingRequest.BackupSound(backupUri = DEFAULT_BACKUP_SOUND_URI, delaySeconds = 40)
                } else {
                    null
                },
            tts =
                if ((alarm.timeAnnouncementEnabled || alarm.memoOption.ttsEnabled) && volume > 0f) {
                    RingRequest.Tts(
                        label = alarm.memoOption.memo,
                        speakLabel = alarm.memoOption.ttsEnabled,
                        speakTime = alarm.timeAnnouncementEnabled,
                        repeatIntervalMillis = DEFAULT_TTS_REPEAT_MILLIS,
                    )
                } else {
                    null
                },
            vibrate = alarm.vibrate && volume > 0f,
        )
    }

    /** Bundled "extra loud" sound (asset lands with the ringtone catalog, M11). */
    const val DEFAULT_BACKUP_SOUND_URI: String =
        "android.resource://dev.tomng.alarme/raw/alarme_backup_loud"

    /** Default: announcements repeat once a minute. */
    const val DEFAULT_TTS_REPEAT_MILLIS: Long = 60_000L
}
