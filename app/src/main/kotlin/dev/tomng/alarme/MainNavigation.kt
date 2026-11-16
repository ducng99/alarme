package dev.tomng.alarme

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import java.net.URLDecoder
import java.net.URLEncoder

/** Which sound the picker is editing. */
internal sealed interface PickerTarget {
    /** The sound of a saved alarm, written straight to the database. */
    data class ForAlarm(val alarmId: Long) : PickerTarget

    /** The editor's draft sound for [alarmId]; the result goes back to the editor. */
    data class EditorSound(val alarmId: Long, val currentUri: String? = null) : PickerTarget

    /** The editor's draft backup sound for [alarmId]. */
    data class EditorBackupSound(val alarmId: Long, val currentUri: String? = null) : PickerTarget

    data object Default : PickerTarget
}

/** The app's destinations; the ring screen is a separate activity. */
internal sealed interface Screen {
    data object List : Screen

    data object Onboarding : Screen

    data class Picker(val target: PickerTarget) : Screen

    data object Settings : Screen

    data object Permissions : Screen

    data object Diagnostics : Screen

    data object Optimize : Screen

    data object Backup : Screen

    data class Editor(val alarmId: Long) : Screen
}

/** A stable, saveable identity for a destination. */
internal val Screen.key: String
    get() =
        when (this) {
            Screen.List -> "list"
            Screen.Onboarding -> "onboarding"
            Screen.Settings -> "settings"
            Screen.Permissions -> "permissions"
            Screen.Diagnostics -> "diagnostics"
            Screen.Optimize -> "optimize"
            Screen.Backup -> "backup"
            is Screen.Editor -> "editor:$alarmId"
            is Screen.Picker ->
                when (val t = target) {
                    PickerTarget.Default -> "picker:default"
                    is PickerTarget.ForAlarm -> "picker:alarm:${t.alarmId}"
                    is PickerTarget.EditorSound ->
                        "picker:editor:${t.alarmId}:${encode(t.currentUri)}"
                    is PickerTarget.EditorBackupSound ->
                        "picker:backup:${t.alarmId}:${encode(t.currentUri)}"
                }
        }

private fun screenOf(key: String): Screen? {
    val parts = key.split(':')
    return when (parts[0]) {
        "list" -> Screen.List
        "onboarding" -> Screen.Onboarding
        "settings" -> Screen.Settings
        "permissions" -> Screen.Permissions
        "diagnostics" -> Screen.Diagnostics
        "optimize" -> Screen.Optimize
        "backup" -> Screen.Backup
        "editor" -> parts.getOrNull(1)?.toLongOrNull()?.let { Screen.Editor(it) }
        "picker" -> pickerOf(parts)?.let { Screen.Picker(it) }
        else -> null
    }
}

private fun pickerOf(parts: kotlin.collections.List<String>): PickerTarget? {
    if (parts.getOrNull(1) == "default") return PickerTarget.Default
    val id = parts.getOrNull(2)?.toLongOrNull() ?: return null
    return when (parts[1]) {
        "alarm" -> PickerTarget.ForAlarm(id)
        "editor" -> PickerTarget.EditorSound(id, decode(parts.getOrNull(3)))
        "backup" -> PickerTarget.EditorBackupSound(id, decode(parts.getOrNull(3)))
        else -> null
    }
}

private fun encode(uri: String?): String = uri?.let { URLEncoder.encode(it, Charsets.UTF_8) } ?: ""

private fun decode(value: String?): String? =
    value?.takeIf { it.isNotEmpty() }?.let { URLDecoder.decode(it, Charsets.UTF_8) }

/** The back stack. The list is always at the bottom; back from it leaves the app. */
internal class NavState(initial: kotlin.collections.List<Screen> = listOf(Screen.List)) {
    val stack = mutableStateListOf<Screen>().apply { addAll(initial) }

    val current: Screen
        get() = stack.last()

    val previous: Screen?
        get() = stack.getOrNull(stack.lastIndex - 1)

    val canPop: Boolean
        get() = stack.size > 1

    /** Opens [screen]; when it is already in the stack, returns to it instead of duplicating. */
    fun push(screen: Screen) {
        val existing = stack.indexOfFirst { it.key == screen.key }
        if (existing >= 0) {
            while (stack.lastIndex > existing) stack.removeAt(stack.lastIndex)
            stack[existing] = screen
        } else {
            stack.add(screen)
        }
    }

    fun pop(): Boolean {
        if (!canPop) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun replaceTop(screen: Screen) {
        val existing = stack.indexOf(screen)
        if (existing in 0 until stack.lastIndex) {
            stack.removeRange(existing + 1, stack.size)
        } else {
            stack[stack.lastIndex] = screen
        }
    }

    fun save(): ArrayList<String> = ArrayList(stack.map { it.key })

    companion object {
        fun restore(keys: kotlin.collections.List<String>): NavState {
            val screens = keys.mapNotNull(::screenOf)
            return NavState(screens.ifEmpty { listOf(Screen.List) })
        }

        val Saver: Saver<NavState, ArrayList<String>> =
            Saver(save = { it.save() }, restore = { restore(it) })
    }
}
