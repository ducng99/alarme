package dev.tomng.alarme.core.alarmengine.statemachine

import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason

/**
 * Dispatches events while an alarm is ringing to the concern-specific reducers — the equivalent of
 * a per-state handler graph. Events that do not apply to the current stage are ignored (the machine
 * never throws on a stray event).
 */
internal object RingingReducer {

    fun reduce(state: RingState.Ringing, event: RingEvent): RingTransition =
        when (event) {
            // Double delivery (retry, redelivered broadcast): keep the live session untouched.
            is RingEvent.RingSessionEvent -> RingTransition(state)

            is RingEvent.RingBlocked ->
                RingTransition(
                    RingState.Idle,
                    listOf(RingEffect.Reschedule(event.reason.toRescheduleReason())),
                )

            is RingEvent.FalseAlarmDetected ->
                RingTransition(
                    RingState.Idle,
                    listOf(RingEffect.Reschedule(RescheduleReason.FALSE_ALARM)),
                )

            is RingEvent.OnDismissClick -> DismissReducer.onDismissClick(state, event.now)

            is RingEvent.AutoDismissTimeout -> DismissReducer.onAutoDismissTimeout(state, event.now)

            is RingEvent.OnSnoozeClick -> SnoozeReducer.onSnoozeClick(state, event)

            is RingEvent.OnMissionStart -> MissionChainReducer.onMissionStart(state, event.now)

            is RingEvent.OnMissionComplete ->
                MissionChainReducer.onMissionComplete(state, event.now)

            is RingEvent.OnMissionTimeout -> MissionChainReducer.onMissionTimeout(state, event.now)

            is RingEvent.OnMissionTimerTick,
            RingEvent.OnMissionTimerReset -> MissionChainReducer.onTimerEvent(state, event)

            is RingEvent.OnEmergencyClick -> EmergencyReducer.open(state, event.now)

            is RingEvent.OnEmergencyTap -> EmergencyReducer.tap(state, event.now)

            is RingEvent.OnEmergencyCancel -> EmergencyReducer.cancel(state, event.now)

            RingEvent.MissionMuteExhausted -> MissionChainReducer.onMuteExhausted(state)

            RingEvent.VolumeMuteRequested -> MuteReducer.onMuteRequested(state)

            else -> RingTransition(state)
        }
}
