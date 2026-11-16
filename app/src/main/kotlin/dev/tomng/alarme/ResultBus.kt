package dev.tomng.alarme

import androidx.compose.runtime.Stable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A sound chosen in the picker, addressed to the [slot] that asked for it. */
internal data class SoundResult(val slot: String, val uri: String?)

/** The result slot a picker target answers to; the current selection is not part of it. */
internal val PickerTarget.slot: String
    get() =
        when (this) {
            PickerTarget.Default -> "default"
            is PickerTarget.ForAlarm -> "alarm:$alarmId"
            is PickerTarget.EditorSound -> "editor:$alarmId"
            is PickerTarget.EditorBackupSound -> "backup:$alarmId"
        }

/**
 * Hands results from a pushed screen back to the screen below it. Results wait until their consumer
 * takes them, so the consumer may be recomposed or restored in between.
 */
@Stable
internal class ResultBus {
    private val pending = MutableStateFlow<List<SoundResult>>(emptyList())
    val results: StateFlow<List<SoundResult>> = pending.asStateFlow()

    fun send(result: SoundResult) {
        pending.update { list -> list.filterNot { it.slot == result.slot } + result }
    }

    /** Removes and returns the pending result for [slot], if any. */
    fun take(slot: String): SoundResult? {
        val taken = pending.value.firstOrNull { it.slot == slot } ?: return null
        pending.update { list -> list.filterNot { it.slot == slot } }
        return taken
    }
}
