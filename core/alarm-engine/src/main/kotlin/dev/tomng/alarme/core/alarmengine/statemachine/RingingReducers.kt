package dev.tomng.alarme.core.alarmengine.statemachine

import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Stages that own a mission countdown. */
internal val RingStage.usesMissionTimer: Boolean
    get() = this is RingStage.Mission

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

            is RingStage.Snoozing -> {
                val missions = state.alarm.missions
                if (missions.isEmpty()) {
                    complete(state, DismissReason.NORMAL, now, stopMissionTimer = false)
                } else {
                    resumeIntoMissions(state, missions)
                }
            }

            // Missions must be finished; the emergency branch ignores the dismiss button.
            else -> RingTransition(state)
        }

    /** Dismissing a snooze never skips the missions: the alarm rings again and the chain starts. */
    private fun resumeIntoMissions(
        state: RingState.Ringing,
        missions: List<Mission>,
    ): RingTransition {
        val entering = MissionChainReducer.enterMission(state, index = 0, mission = missions[0])
        val effects = buildList {
            add(RingEffect.ResumeFromSnooze(state.alarm))
            state.autoDismiss?.let { add(RingEffect.StartAutoDismissTimer(it)) }
            addAll(entering.effects)
        }
        return RingTransition(entering.state, effects)
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

/**
 * The emergency way out of a mission the user cannot solve: always reachable from the mission (and
 * its prepare screen), it rings at full volume and dismisses after the emergency tap goal.
 */
internal object EmergencyReducer {

    fun open(state: RingState.Ringing): RingTransition =
        when (val stage = state.stage) {
            is RingStage.Mission ->
                RingTransition(
                    state.copy(stage = RingStage.Emergency(stage.index, stage.mission, taps = 0)),
                    listOf(RingEffect.StopMissionTimer) +
                        MissionChainReducer.resumeIfSilenced(state),
                )

            is RingStage.PrepareMission ->
                RingTransition(
                    state.copy(stage = RingStage.Emergency(stage.index, stage.mission, taps = 0)),
                )

            else -> RingTransition(state)
        }

    fun tap(state: RingState.Ringing, now: Instant): RingTransition {
        val stage = state.stage as? RingStage.Emergency ?: return RingTransition(state)
        val taps = stage.taps + 1
        return if (taps >= RingStateMachine.EMERGENCY_TAPS) {
            DismissReducer.complete(state, DismissReason.EMERGENCY, now, stopMissionTimer = false)
        } else {
            RingTransition(state.copy(stage = stage.copy(taps = taps)))
        }
    }

    fun cancel(state: RingState.Ringing): RingTransition {
        val stage = state.stage as? RingStage.Emergency ?: return RingTransition(state)
        return MissionChainReducer.enterMission(state, stage.index, stage.mission)
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
