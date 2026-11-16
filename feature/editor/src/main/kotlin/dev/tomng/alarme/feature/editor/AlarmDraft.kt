package dev.tomng.alarme.feature.editor

import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.AlarmType
import dev.tomng.alarme.core.model.alarm.CrescendoOption
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import dev.tomng.alarme.core.model.alarm.MemoOption
import dev.tomng.alarme.core.model.alarm.SnoozeOption
import dev.tomng.alarme.core.model.alarm.WakeUpCheck
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.MissionError
import dev.tomng.alarme.core.model.mission.MissionValidator
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek

/**
 * The editable form of an alarm. Kept separate from [Alarm] so the UI can hold half-typed values
 * (an empty name, a mission without its payload) without violating the domain invariants.
 */
data class AlarmDraft(
    val id: Long = Alarm.NEW_ID,
    val type: AlarmType = AlarmType.NORMAL,
    val emoji: String = Alarm.DEFAULT_EMOJI,
    val name: String = "",
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = 0,
    val days: Set<DayOfWeek> = emptySet(),
    val enabled: Boolean = true,
    val missions: List<Mission> = emptyList(),
    val ringtoneUri: String? = null,
    val volumeRatio: Float = 1f,
    val vibrate: Boolean = false,
    val memo: String = "",
    val ttsEnabled: Boolean = false,
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = SnoozeOption.DEFAULT_MINUTES,
    val snoozeLimit: Int = SnoozeOption.DEFAULT_LIMIT,
    val crescendoEnabled: Boolean = false,
    val crescendoSeconds: Int = CrescendoOption.DEFAULT_SECONDS,
    val backupSoundEnabled: Boolean = false,
    val backupSoundUri: String? = null,
    val timeAnnouncementEnabled: Boolean = false,
    val wakeUpCheckMinutes: Int = 0,
    val isSkipped: Boolean = false,
    val createdAt: Instant? = null,
) {

    val isNew: Boolean
        get() = id == Alarm.NEW_ID

    val canAddMission: Boolean
        get() = missions.size < Mission.MAX_PER_ALARM

    /** Problems that block saving; empty means the draft is saveable. */
    fun errors(): List<DraftError> {
        val errors = mutableListOf<DraftError>()
        if (missions.size > Mission.MAX_PER_ALARM) errors += DraftError.TooManyMissions
        if (MissionValidator.validate(missions).contains(MissionError.InvalidPayload)) {
            errors += DraftError.IncompleteMission
        }
        return errors
    }

    fun toAlarm(now: Instant): Alarm =
        Alarm(
            id = id,
            type = type,
            emoji = emoji,
            name = name.trim(),
            hour = hour,
            minute = minute,
            daysOfWeek = DaysOfWeek.ofSet(days),
            enabled = enabled,
            missions = missions,
            ringtoneUri = ringtoneUri,
            volumeRatio = volumeRatio,
            vibrate = vibrate,
            memoOption = MemoOption(memo = memo.trim(), ttsEnabled = ttsEnabled),
            snoozeOption =
                SnoozeOption(
                    enabled = snoozeEnabled,
                    minutes = snoozeMinutes,
                    limit = snoozeLimit,
                ),
            crescendoOption =
                CrescendoOption(enabled = crescendoEnabled, seconds = crescendoSeconds),
            backupSoundEnabled = backupSoundEnabled,
            backupSoundUri = backupSoundUri,
            timeAnnouncementEnabled = timeAnnouncementEnabled,
            wakeUpCheck = WakeUpCheck(minutes = wakeUpCheckMinutes),
            isSkipped = isSkipped,
            createdAt = createdAt ?: now,
            updatedAt = now,
        )

    companion object {
        const val DEFAULT_HOUR = 7

        fun fromAlarm(alarm: Alarm): AlarmDraft =
            AlarmDraft(
                id = alarm.id,
                type = alarm.type,
                emoji = alarm.emoji,
                name = alarm.name,
                hour = alarm.hour,
                minute = alarm.minute,
                days = alarm.daysOfWeek.toSet(),
                enabled = alarm.enabled,
                missions = alarm.missions,
                ringtoneUri = alarm.ringtoneUri,
                volumeRatio = alarm.volumeRatio,
                vibrate = alarm.vibrate,
                memo = alarm.memoOption.memo,
                ttsEnabled = alarm.memoOption.ttsEnabled,
                snoozeEnabled = alarm.snoozeOption.enabled,
                snoozeMinutes = alarm.snoozeOption.minutes,
                snoozeLimit = alarm.snoozeOption.limit,
                crescendoEnabled = alarm.crescendoOption.enabled,
                crescendoSeconds = alarm.crescendoOption.seconds,
                backupSoundEnabled = alarm.backupSoundEnabled,
                backupSoundUri = alarm.backupSoundUri,
                timeAnnouncementEnabled = alarm.timeAnnouncementEnabled,
                wakeUpCheckMinutes = alarm.wakeUpCheck.minutes,
                isSkipped = alarm.isSkipped,
                createdAt = alarm.createdAt,
            )
    }
}

/** Why a draft cannot be saved. */
enum class DraftError {
    TooManyMissions,
    IncompleteMission,
}
