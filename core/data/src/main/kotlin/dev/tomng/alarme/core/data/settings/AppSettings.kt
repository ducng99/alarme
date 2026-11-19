package dev.tomng.alarme.core.data.settings

import dev.tomng.alarme.core.model.alarm.AutoDismissOption

enum class AudioOutputPreference {
    ALARM_STREAM,
    EXTERNAL,
}

/** App-level settings: mute limit, auto-dismiss, audio output, reliability toggles. */
data class AppSettings(
    val muteEnabled: Boolean = true,
    val muteLimit: Int = 3,
    val muteDuringMission: Boolean = true,
    val missionMuteMinutes: Int = DEFAULT_MISSION_MUTE_MINUTES,
    val autoDismiss: AutoDismissOption = AutoDismissOption.Off,
    val audioOutput: AudioOutputPreference = AudioOutputPreference.ALARM_STREAM,
    val preventPowerOff: Boolean = false,
    val preventUninstall: Boolean = false,
    val defaultRingtoneId: Long? = null,
    val onboardingDone: Boolean = false,
)

const val DEFAULT_MISSION_MUTE_MINUTES = 3

/** The settings slider range for the total mute-during-mission time per ring. */
val MISSION_MUTE_MINUTES_RANGE: IntRange = 1..10
