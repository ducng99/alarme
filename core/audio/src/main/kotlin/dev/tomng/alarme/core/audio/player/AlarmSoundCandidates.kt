package dev.tomng.alarme.core.audio.player

const val SETTINGS_ALARM_ALIAS: String = "content://settings/system/alarm_sound"

/**
 * The ordered list of sounds a ring tries until one plays. The settings alias is never opened
 * directly (Android 17 refuses it), so it is replaced by the resolved system default, and the
 * bundled tone is the last resort that always exists in the APK.
 */
fun alarmSoundCandidates(
    requested: String,
    systemDefault: String?,
    bundledFallback: String,
): List<String> {
    val resolvedDefault = systemDefault?.takeIf { it != SETTINGS_ALARM_ALIAS }
    return listOfNotNull(
            requested.takeIf { it != SETTINGS_ALARM_ALIAS },
            resolvedDefault,
            bundledFallback,
        )
        .distinct()
}
