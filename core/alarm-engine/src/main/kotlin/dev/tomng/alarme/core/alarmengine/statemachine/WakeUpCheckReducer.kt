package dev.tomng.alarme.core.alarmengine.statemachine

/**
 * The wake-up check: pending → started → complete/cancelled/failed. A failed check emits
 * [RingEffect.ReRing] — an explicit re-ring request instead of implicit state collapse the executor
 * might miss.
 */
internal object WakeUpCheckReducer {

    fun reduce(state: RingState.WakeUpCheck, event: RingEvent): RingTransition =
        when (event) {
            RingEvent.OnWakeUpCheckComplete,
            RingEvent.OnWakeUpCheckCancelled ->
                RingTransition(
                    RingState.Idle,
                    listOf(RingEffect.StopWakeUpCheckTimer, RingEffect.CancelWakeUpCheck),
                )

            RingEvent.OnWakeUpCheckFailed ->
                RingTransition(
                    RingState.Idle,
                    listOf(
                        RingEffect.StopWakeUpCheckTimer,
                        RingEffect.CancelWakeUpCheck,
                        RingEffect.ReRing(state.alarm),
                    ),
                )

            // The fixed 180 s snooze from the check screen.
            is RingEvent.OnSnoozeClick -> {
                val checkAt = event.now + RingStateMachine.WAKE_UP_CHECK_SNOOZE
                RingTransition(
                    state = RingState.WakeUpCheckPending(state.alarm, checkAt),
                    effects =
                        listOf(
                            RingEffect.StopWakeUpCheckTimer,
                            RingEffect.CancelWakeUpCheck,
                            RingEffect.ScheduleWakeUpCheck(state.alarm, checkAt),
                        ),
                )
            }

            else -> RingTransition(state)
        }

    fun reducePending(state: RingState.WakeUpCheckPending, event: RingEvent): RingTransition =
        when (event) {
            is RingEvent.WakeUpCheckStarted ->
                RingTransition(
                    RingState.WakeUpCheck(event.alarm, event.startTime),
                    listOf(
                        RingEffect.StartWakeUpCheckTimer(RingStateMachine.WAKE_UP_CHECK_WINDOW),
                    ),
                )

            else -> RingTransition(state)
        }
}
