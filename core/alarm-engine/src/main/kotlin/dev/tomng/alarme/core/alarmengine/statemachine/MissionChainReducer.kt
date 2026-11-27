package dev.tomng.alarme.core.alarmengine.statemachine

import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The mission chain: prepare → mission → next → dismiss, with the per-mission inactivity timer, the
 * budgeted mute-during-mission silence, and the timeout → back-to-ring failure.
 */
internal object MissionChainReducer {

    fun onMissionStart(state: RingState.Ringing): RingTransition =
        when (val stage = state.stage) {
            // The prepare screen already ran: start the mission itself, no second prepare.
            is RingStage.PrepareMission -> startMission(state, stage.index, stage.mission)
            else -> RingTransition(state)
        }

    fun onMissionComplete(state: RingState.Ringing, now: Instant): RingTransition {
        val stage = state.stage as? RingStage.Mission ?: return RingTransition(state)
        val next = stage.index + 1
        val missions = state.alarm.missions
        return if (next < missions.size) {
            val entering = enterMission(state, next, missions[next])
            RingTransition(entering.state, listOf(RingEffect.StopMissionTimer) + entering.effects)
        } else {
            DismissReducer.complete(state, DismissReason.NORMAL, now, stopMissionTimer = true)
        }
    }

    /** A failed mission returns to the loud ring; the next dismiss restarts the whole chain. */
    fun onMissionTimeout(state: RingState.Ringing): RingTransition {
        if (state.stage !is RingStage.Mission) return RingTransition(state)
        val resumed = MissionMute.end(state, charge = true)
        return RingTransition(
            state = resumed.state.copy(stage = RingStage.Ring),
            effects = listOf(RingEffect.StopMissionTimer) + resumed.effects,
        )
    }

    /** Back abandons the mission like a timeout; from the emergency it returns to the mission. */
    fun onBack(state: RingState.Ringing): RingTransition =
        when (state.stage) {
            is RingStage.Mission -> onMissionTimeout(state)
            is RingStage.PrepareMission -> RingTransition(state.copy(stage = RingStage.Ring))
            is RingStage.Emergency -> EmergencyReducer.cancel(state)
            else -> RingTransition(state)
        }

    fun onTimerEvent(state: RingState.Ringing, event: RingEvent): RingTransition {
        val stage = state.stage as? RingStage.Mission ?: return RingTransition(state)
        val timer = stage.timer
        return when (event) {
            is RingEvent.OnMissionTimerTick ->
                RingTransition(
                    state.copy(stage = stage.copy(timer = timer.copy(remaining = event.remaining))),
                )

            // User activity: don't time out mid-attempt, restart the full countdown.
            RingEvent.OnMissionTimerReset ->
                RingTransition(
                    state.copy(stage = stage.copy(timer = timer.copy(remaining = timer.total))),
                    listOf(RingEffect.StartMissionTimer(stage.mission, timer.total)),
                )

            else -> RingTransition(state)
        }
    }

    /** Starts mission [index]: PrepareMission for Step/Squat, Mission otherwise. */
    fun enterMission(
        state: RingState.Ringing,
        index: Int,
        mission: Mission,
    ): RingTransition {
        if (mission.needsPrepare()) {
            val resumed = MissionMute.end(state)
            return RingTransition(
                resumed.state.copy(stage = RingStage.PrepareMission(index, mission)),
                resumed.effects,
            )
        }
        return startMission(state, index, mission)
    }

    fun startMission(
        state: RingState.Ringing,
        index: Int,
        mission: Mission,
    ): RingTransition {
        val timer = timerOf(mission)
        val silenced = MissionMute.start(state)
        return RingTransition(
            silenced.state.copy(stage = RingStage.Mission(index, mission, timer)),
            listOf(RingEffect.StartMissionTimer(mission, timer.total)) + silenced.effects,
        )
    }

    fun timerOf(mission: Mission): MissionTimer {
        val limit =
            if (mission.timeLimitSeconds > 0) {
                mission.timeLimitSeconds.seconds
            } else {
                RingStateMachine.DEFAULT_MISSION_TIME_LIMIT
            }
        return MissionTimer(limit, limit)
    }

    private fun Mission.needsPrepare(): Boolean = this is Mission.Step || this is Mission.Squat
}

/**
 * The mute-during-mission budget: silence is only granted while [RingState.Ringing.missionMuteLeft]
 * is above zero. Starting a mission is free; each time the ring comes back after a mission timed
 * out costs one.
 */
internal object MissionMute {

    data class Step(val state: RingState.Ringing, val effects: List<RingEffect> = emptyList())

    fun start(state: RingState.Ringing): Step {
        val canSilence = state.muteDuringMission && !state.silenced && state.missionMuteLeft > 0
        if (!canSilence) return Step(state)
        return Step(state.copy(silenced = true), listOf(RingEffect.SilenceRing))
    }

    /**
     * Ends the silence; [charge] spends one resume, [resume] is false when the sound stays off
     * anyway (snooze).
     */
    fun end(state: RingState.Ringing, charge: Boolean = false, resume: Boolean = true): Step {
        if (!state.silenced) return Step(state)
        val left = if (charge) state.missionMuteLeft - 1 else state.missionMuteLeft
        return Step(
            state.copy(missionMuteLeft = left, silenced = false),
            if (resume) listOf(RingEffect.ResumeRing) else emptyList(),
        )
    }
}
