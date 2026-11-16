package dev.tomng.alarme.core.model.alarm

import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.serialization.InstantIsoSerializer
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * The alarm aggregate. Wall-clock semantics: [hour]/[minute] are interpreted in the device's
 * current timezone at scheduling time (see `NextOccurrenceCalculator`).
 */
@Serializable
data class Alarm(
    val id: Long,
    val type: AlarmType = AlarmType.NORMAL,
    val emoji: String = DEFAULT_EMOJI,
    val name: String,
    val hour: Int,
    val minute: Int,
    val daysOfWeek: DaysOfWeek = DaysOfWeek.EVERY_DAY,
    val enabled: Boolean = true,
    val missions: List<Mission> = emptyList(),
    val ringtoneUri: String? = null,
    val volumeRatio: Float = 1f,
    val vibrate: Boolean = false,
    val memoOption: MemoOption = MemoOption(),
    val snoozeOption: SnoozeOption = SnoozeOption(),
    val crescendoOption: CrescendoOption = CrescendoOption(),
    val backupSoundEnabled: Boolean = false,
    /** The loud sound played when the user is unresponsive; null uses the system alarm sound. */
    val backupSoundUri: String? = null,
    val timeAnnouncementEnabled: Boolean = false,
    val wakeUpCheck: WakeUpCheck = WakeUpCheck(),
    /** Skip-once: the next occurrence is dropped (repeating alarms only; reset after it passes). */
    val isSkipped: Boolean = false,
    /** Denormalised next trigger instant, recomputed on save by the repository. */
    @Transient val nextOccurrence: Instant? = null,
    @Serializable(with = InstantIsoSerializer::class) val createdAt: Instant,
    @Serializable(with = InstantIsoSerializer::class) val updatedAt: Instant,
) {
    init {
        require(hour in 0..23) { "hour must be in 0..23" }
        require(minute in 0..59) { "minute must be in 0..59" }
        require(volumeRatio in VOLUME_MIN..VOLUME_MAX) {
            "volumeRatio must be in $VOLUME_MIN..$VOLUME_MAX"
        }
    }

    companion object {
        const val DEFAULT_EMOJI = "⏰"
        const val VOLUME_MIN = 0f
        const val VOLUME_MAX = 1f

        /** Id sentinel for an alarm that has not been persisted yet. */
        const val NEW_ID = 0L
    }
}
