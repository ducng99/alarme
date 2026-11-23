package dev.tomng.alarme.core.alarmengine.statemachine

import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The output of one reduction: the next state plus the effects the executor must run, in order. */
data class RingTransition(
    val state: RingState,
    val effects: List<RingEffect> = emptyList(),
)

/**
 * The pure ring reducer. `(RingState, RingEvent) -> RingTransition` with no clock, no Android, no
 * side effects: everything time-dependent arrives inside the event, and everything asynchronous
 * leaves as an effect the executor feeds back as events.
 *
 * The work is split by concern, mirroring a per-state handler graph: [RingingReducer] dispatches to
 * [DismissReducer], [SnoozeReducer], [MissionChainReducer], [EmergencyReducer] and [MuteReducer];
 * [WakeUpCheckReducer] owns the post-dismiss check.
 *
 * Rules summary:
 * * a second ring request while ringing is dropped (retry/redelivery dedupe);
 * * dismiss only completes the ring when the mission chain is finished — otherwise it *starts* the
 *   chain (Step/Squat via a prepare stage); dismissing from the snooze stage also starts the chain
 *   (the alarm rings again), so snoozing is never a way around the missions;
 * * with mute-during-mission on, the sound is silenced while a mission is being solved and comes
 *   back whenever the mission stage is left (prepare screen, emergency, failure); the silence is
 *   budgeted per session (3 mission timeouts by default) and the ring stays loud once they are
 *   spent;
 * * every mission has an inactivity limit (its own, or 60 s); running out fails back to the ring
 *   stage and the next dismiss restarts the chain from the first mission;
 * * the emergency (100 taps, sound on) is always available from a mission and dismisses the ring;
 * * snooze is bounded by the alarm's limit (UNLIMITED = -1), forbidden during emergency/snooze, and
 *   possible from the wake-up-check with a fixed 180 s;
 * * every dismissal (normal/auto/emergency) arms the wake-up check when configured; a failed check
 *   emits [RingEffect.ReRing] explicitly;
 * * the mute limit counts volume-down presses; WordBeat missions are exempt (mic = pointer);
 * * blocked/false-alarm deliveries return to idle and re-arm the next occurrence.
 */
object RingStateMachine {

    /** Taps the emergency screen needs before it dismisses the ring. */
    const val EMERGENCY_TAPS = 100

    /** The inactivity limit for missions without their own time limit. */
    val DEFAULT_MISSION_TIME_LIMIT: Duration = 60.seconds

    /** The fixed snooze offered from the wake-up-check. */
    val WAKE_UP_CHECK_SNOOZE: Duration = 180.seconds

    /** The confirmation window: miss it and the alarm rings again. */
    val WAKE_UP_CHECK_WINDOW: Duration = 100.seconds

    /**
     * How many times the ring may come back after a mission timeout and still be silenced again.
     */
    const val DEFAULT_MISSION_MUTE_LIMIT = 3

    /** The app-level default mute limit: limit 3, enabled. */
    const val DEFAULT_MUTE_LIMIT = 3

    fun reduce(state: RingState, event: RingEvent): RingTransition =
        when (state) {
            is RingState.Idle -> reduceIdle(event)
            // A fresh delivery replaces a live ringing session (the snooze re-ring, or another
            // alarm winning the race) — but an exact replay of the live delivery is dropped, so
            // a redelivered service intent can never re-ring an already ringing alarm.
            is RingState.Ringing ->
                when {
                    event !is RingEvent.AlarmRingRequest -> RingingReducer.reduce(state, event)
                    isLiveReplay(state, event) -> RingTransition(state)
                    else -> reduceIdle(event)
                }
            is RingState.WakeUpCheck ->
                // A new alarm firing during the check preempts it: the ring wins, the check dies.
                if (event is RingEvent.AlarmRingRequest) preemptCheck(event, stopWindow = true)
                else WakeUpCheckReducer.reduce(state, event)

            is RingState.WakeUpCheckPending ->
                if (event is RingEvent.AlarmRingRequest) preemptCheck(event, stopWindow = false)
                else WakeUpCheckReducer.reducePending(state, event)
        }

    private fun isLiveReplay(
        state: RingState.Ringing,
        event: RingEvent.AlarmRingRequest,
    ): Boolean =
        state.scheduleId == event.scheduleId &&
            state.trigger == event.trigger &&
            state.ringEventId == event.ringEventId

    private fun preemptCheck(event: RingEvent.AlarmRingRequest, stopWindow: Boolean) =
        RingTransition(
            state = ringingFrom(event),
            effects =
                buildList {
                    if (stopWindow) add(RingEffect.StopWakeUpCheckTimer)
                    add(RingEffect.CancelWakeUpCheck)
                    add(RingEffect.Ring(event.alarm))
                    event.autoDismiss?.let { add(RingEffect.StartAutoDismissTimer(it)) }
                },
        )

    private fun reduceIdle(event: RingEvent): RingTransition =
        when (event) {
            is RingEvent.AlarmRingRequest ->
                RingTransition(
                    state = ringingFrom(event),
                    effects =
                        buildList {
                            add(RingEffect.Ring(event.alarm))
                            event.autoDismiss?.let { add(RingEffect.StartAutoDismissTimer(it)) }
                        },
                )

            is RingEvent.AlarmRingRestored ->
                RingTransition(
                    state = ringingFrom(event),
                    effects = listOf(RingEffect.RestoreRing(event.alarm)),
                )

            is RingEvent.WakeUpCheckRestored ->
                RingTransition(
                    state = RingState.WakeUpCheckPending(event.alarm, event.checkAt),
                    effects = listOf(RingEffect.ScheduleWakeUpCheck(event.alarm, event.checkAt)),
                )

            // The check service fired into a fresh session (process death since the dismiss):
            // enter the check directly instead of dropping it.
            is RingEvent.WakeUpCheckStarted ->
                RingTransition(
                    state = RingState.WakeUpCheck(event.alarm, event.startTime),
                    effects =
                        listOf(
                            RingEffect.StartWakeUpCheckTimer(RingStateMachine.WAKE_UP_CHECK_WINDOW),
                        ),
                )

            is RingEvent.RingBlocked ->
                RingTransition(
                    state = RingState.Idle,
                    effects = listOf(RingEffect.Reschedule(event.reason.toRescheduleReason())),
                )

            else -> RingTransition(state = RingState.Idle)
        }

    private fun ringingFrom(event: RingEvent.RingSessionEvent): RingState.Ringing =
        RingState.Ringing(
            alarm = event.alarm,
            scheduleId = event.scheduleId,
            trigger = event.trigger,
            ringEventId = event.ringEventId,
            startedAt = event.startedAt,
            autoDismiss = event.autoDismiss,
            mute = MuteState(count = 0, limit = DEFAULT_MUTE_LIMIT),
            snooze = SnoozeState(count = event.snoozeCount, limit = event.alarm.snoozeOption.limit),
            stage = RingStage.Ring,
            muteDuringMission = event.muteDuringMission,
            missionMuteLeft = event.missionMuteLimit,
        )
}

internal fun BlockedRingReason.toRescheduleReason(): RescheduleReason =
    when (this) {
        BlockedRingReason.INVALID_ALARM -> RescheduleReason.INVALID_ALARM
        BlockedRingReason.EXPIRED_ALARM -> RescheduleReason.EXPIRED_ALARM
    }
