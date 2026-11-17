package dev.tomng.alarme.core.alarmengine.statemachine

import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The mission chain: prepare → mission → next → dismiss, with the per-mission inactivity timer, the
 * mute-during-mission silence, and the timeout → back-to-ring failure.
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
        return RingTransition(
            state = state.copy(stage = RingStage.Ring),
            effects = listOf(RingEffect.StopMissionTimer) + resumeIfSilenced(state),
        )
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
    fun enterMission(state: RingState.Ringing, index: Int, mission: Mission): RingTransition {
        if (mission.needsPrepare()) {
            return RingTransition(
                state.copy(stage = RingStage.PrepareMission(index, mission)),
                resumeIfSilenced(state),
            )
        }
        return startMission(state, index, mission)
    }

    fun startMission(state: RingState.Ringing, index: Int, mission: Mission): RingTransition {
        val timer = timerOf(mission)
        val effects = buildList {
            add(RingEffect.StartMissionTimer(mission, timer.total))
            if (state.muteDuringMission && state.stage !is RingStage.Mission) {
                add(RingEffect.SilenceRing)
            }
        }
        return RingTransition(state.copy(stage = RingStage.Mission(index, mission, timer)), effects)
    }

    /** The ring comes back whenever a silenced mission stage is left for a non-terminal stage. */
    fun resumeIfSilenced(state: RingState.Ringing): List<RingEffect> =
        if (state.muteDuringMission && state.stage is RingStage.Mission) {
            listOf(RingEffect.ResumeRing)
        } else {
            emptyList()
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
