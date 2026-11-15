package dev.tomng.alarme

import dev.tomng.alarme.core.model.alarm.Alarm

/** Which sound the picker is editing. */
internal sealed interface PickerTarget {
    data class ForAlarm(val alarm: Alarm) : PickerTarget

    data object Default : PickerTarget
}

/** The app's top-level destinations; the ring screen is a separate activity. */
internal sealed interface Screen {
    data object List : Screen

    data class Picker(val target: PickerTarget) : Screen

    data object Settings : Screen

    data object Diagnostics : Screen

    data object Optimize : Screen

    data object Backup : Screen

    data class Editor(val alarmId: Long) : Screen
}

/** Destination reached by back from [screen], or null when back leaves the app. */
internal fun parentScreen(screen: Screen): Screen? =
    when (screen) {
        Screen.List -> null
        Screen.Settings -> Screen.List
        Screen.Diagnostics -> Screen.Settings
        Screen.Optimize -> Screen.Settings
        Screen.Backup -> Screen.Settings
        is Screen.Editor -> Screen.List
        is Screen.Picker -> Screen.List
    }
