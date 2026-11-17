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
    val autoDismiss: AutoDismissOption = AutoDismissOption.Off,
    val audioOutput: AudioOutputPreference = AudioOutputPreference.ALARM_STREAM,
    val preventPowerOff: Boolean = false,
    val preventUninstall: Boolean = false,
    val defaultRingtoneId: Long? = null,
    val onboardingDone: Boolean = false,
)
