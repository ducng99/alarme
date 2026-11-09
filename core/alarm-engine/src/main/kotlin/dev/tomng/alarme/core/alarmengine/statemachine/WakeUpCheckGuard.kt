package dev.tomng.alarme.core.alarmengine.statemachine

/**
 * Editing is blocked while an alarm's wake-up check is live (`wakeup_check_warning_ineditable`): an
 * edit could delete or reschedule the very alarm the check is about to re-ring.
 */
object WakeUpCheckGuard {

    fun isEditingBlocked(state: RingState, alarmId: Long): Boolean =
        when (state) {
            is RingState.WakeUpCheck -> state.alarm.id == alarmId
            is RingState.WakeUpCheckPending -> state.alarm.id == alarmId
            else -> false
        }
}
