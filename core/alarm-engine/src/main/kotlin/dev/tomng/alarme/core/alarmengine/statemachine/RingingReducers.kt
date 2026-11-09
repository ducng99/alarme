package dev.tomng.alarme.core.alarmengine.statemachine

import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Stages that own a mission countdown (the emergency timer is the same timer, re-armed). */
internal val RingStage.usesMissionTimer: Boolean
    get() = this is RingStage.Mission || this is RingStage.Emergency

/**
 * The terminal path shared by every dismissal: stop the timers, dismiss, reset the snooze counters,
 * and arm the wake-up check when the alarm asks for one.
 */
internal object DismissReducer {

    fun onDismissClick(state: RingState.Ringing, now: Instant): RingTransition =
        when (val stage = state.stage) {
            is RingStage.Ring -> {
                val missions = state.alarm.missions
                if (missions.isEmpty()) {
                    complete(state, DismissReason.NORMAL, now, stopMissionTimer = false)
                } else {
                    MissionChainReducer.enterMission(state, index = 0, mission = missions[0])
                }
            }

            // Turning the alarm off from the snooze screen cancels the pending snooze.
            is RingStage.Snoozing ->
                complete(state, DismissReason.NORMAL, now, stopMissionTimer = false)

            // Missions must be finished; the emergency branch ignores the dismiss button.
            else -> RingTransition(state)
        }

    fun onAutoDismissTimeout(state: RingState.Ringing, now: Instant): RingTransition =
        if (state.stage is RingStage.Snoozing) {
            RingTransition(state) // the snooze timer governs; auto-dismiss was stopped on snooze
        } else {
            complete(
                state,
                DismissReason.AUTO,
                now,
                stopMissionTimer = state.stage.usesMissionTimer,
            )
        }

    fun complete(
        state: RingState.Ringing,
        reason: DismissReason,
        now: Instant,
        stopMissionTimer: Boolean,
    ): RingTransition {
        val alarm = state.alarm
        val wucMinutes = alarm.wakeUpCheck.minutes
        val checkAt = now + (wucMinutes * 60).seconds
        val effects = buildList {
            if (stopMissionTimer || state.stage.usesMissionTimer) {
                add(RingEffect.StopMissionTimer)
            }
            add(RingEffect.Dismiss(alarm, reason))
            add(RingEffect.ResetSnooze)
            add(RingEffect.StopAutoDismissTimer)
            if (wucMinutes > 0) add(RingEffect.ScheduleWakeUpCheck(alarm, checkAt))
        }
        val next =
            if (wucMinutes > 0) {
                RingState.WakeUpCheckPending(alarm, checkAt)
            } else {
                RingState.Idle
            }
        return RingTransition(next, effects)
    }
}

/** Snooze handling: bounded by the alarm's limit, re-rings on finish. */
internal object SnoozeReducer {

    fun onSnoozeClick(state: RingState.Ringing, event: RingEvent.OnSnoozeClick): RingTransition {
        val option = state.alarm.snoozeOption
        val allowed =
            state.stage !is RingStage.Snoozing &&
                state.stage !is RingStage.Emergency &&
                option.enabled &&
                !state.snooze.exhausted
        if (!allowed) return RingTransition(state)

        val duration = event.duration ?: (option.minutes * 60).seconds
        val finishAt = event.now + duration
        return RingTransition(
            state =
                state.copy(
                    stage = RingStage.Snoozing(finishAt),
                    snooze = state.snooze.copy(count = state.snooze.count + 1),
                ),
            effects =
                listOf(
                    RingEffect.StopMissionTimer,
                    RingEffect.StopAutoDismissTimer,
                    RingEffect.Snooze(state.alarm, finishAt, duration, state.snooze.count + 1),
                ),
        )
    }
}

/** The 40 s emergency branch: complete dismisses, cancel retries the mission. */
internal object EmergencyReducer {

    fun complete(state: RingState.Ringing, now: Instant): RingTransition =
        if (state.stage is RingStage.Emergency) {
            DismissReducer.complete(state, DismissReason.EMERGENCY, now, stopMissionTimer = true)
        } else {
            RingTransition(state)
        }

    fun cancel(state: RingState.Ringing): RingTransition {
        val stage = state.stage as? RingStage.Emergency ?: return RingTransition(state)
        val timer = MissionChainReducer.timerOf(stage.mission)
        return RingTransition(
            state.copy(stage = RingStage.Mission(stage.index, stage.mission, timer)),
            timer?.let { listOf(RingEffect.StartMissionTimer(stage.mission, it.total)) }
                ?: emptyList(),
        )
    }

    fun tick(state: RingState.Ringing, event: RingEvent.OnEmergencyTimerTick): RingTransition {
        val stage = state.stage as? RingStage.Emergency ?: return RingTransition(state)
        return if (event.remaining <= kotlin.time.Duration.ZERO) {
            DismissReducer.complete(
                state,
                DismissReason.EMERGENCY,
                event.now,
                stopMissionTimer = true,
            )
        } else {
            RingTransition(state.copy(stage = stage.copy(remaining = event.remaining)))
        }
    }
}

/** The mute limit: WordBeat is always mutable — the mic, not the volume, is the pointer. */
internal object MuteReducer {

    fun onMuteRequested(state: RingState.Ringing): RingTransition {
        val mission =
            (state.stage as? RingStage.Mission)?.mission
                ?: (state.stage as? RingStage.Emergency)?.mission
        return if (state.mute.canMute(mission)) {
            RingTransition(
                state.copy(mute = state.mute.copy(count = state.mute.count + 1)),
                listOf(RingEffect.MuteAlarm),
            )
        } else {
            RingTransition(state, listOf(RingEffect.MuteDenied))
        }
    }
}
