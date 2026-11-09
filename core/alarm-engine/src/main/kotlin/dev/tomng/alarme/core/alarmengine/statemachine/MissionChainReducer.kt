package dev.tomng.alarme.core.alarmengine.statemachine

import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The mission chain: prepare → mission → next → dismiss, with the per-mission timer, the +10 s
 * mercy button, and the timeout → emergency escalation.
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

    fun onMissionTimeout(state: RingState.Ringing): RingTransition {
        val stage = state.stage as? RingStage.Mission ?: return RingTransition(state)
        val timeout = RingStateMachine.EMERGENCY_TIMEOUT
        return RingTransition(
            state = state.copy(stage = RingStage.Emergency(stage.index, stage.mission, timeout)),
            effects = listOf(RingEffect.StopMissionTimer, RingEffect.StartEmergencyTimer(timeout)),
        )
    }

    fun onTimerEvent(state: RingState.Ringing, event: RingEvent): RingTransition {
        val stage = state.stage as? RingStage.Mission ?: return RingTransition(state)
        return when (event) {
            is RingEvent.OnMissionTimerTick -> {
                val timer = stage.timer ?: return RingTransition(state)
                RingTransition(
                    state.copy(stage = stage.copy(timer = timer.copy(remaining = event.remaining))),
                )
            }

            // User activity: don't time out mid-attempt, restart the full countdown.
            RingEvent.OnMissionTimerReset -> {
                val timer = stage.timer ?: return RingTransition(state)
                RingTransition(
                    state.copy(stage = stage.copy(timer = timer.copy(remaining = timer.total))),
                    listOf(RingEffect.StartMissionTimer(stage.mission, timer.total)),
                )
            }

            // A match is being confirmed: pause the countdown.
            RingEvent.OnMissionTimerStop -> {
                if (stage.timer == null) return RingTransition(state)
                RingTransition(
                    state.copy(stage = stage.copy(timer = null)),
                    listOf(RingEffect.StopMissionTimer),
                )
            }

            RingEvent.OnAddMissionTimeClick -> {
                val timer = stage.timer ?: return RingTransition(state)
                val extension = RingStateMachine.ADD_MISSION_TIME
                RingTransition(
                    state.copy(
                        stage =
                            stage.copy(timer = timer.copy(remaining = timer.remaining + extension)),
                    ),
                    listOf(RingEffect.ExtendMissionTime(extension)),
                )
            }

            else -> RingTransition(state)
        }
    }

    /** Starts mission [index]: PrepareMission for Step/Squat, Mission otherwise. */
    fun enterMission(state: RingState.Ringing, index: Int, mission: Mission): RingTransition {
        if (mission.needsPrepare()) {
            return RingTransition(state.copy(stage = RingStage.PrepareMission(index, mission)))
        }
        return startMission(state, index, mission)
    }

    fun startMission(state: RingState.Ringing, index: Int, mission: Mission): RingTransition {
        val timer = timerOf(mission)
        return RingTransition(
            state = state.copy(stage = RingStage.Mission(index, mission, timer)),
            effects =
                timer?.let { listOf(RingEffect.StartMissionTimer(mission, it.total)) }
                    ?: emptyList(),
        )
    }

    fun timerOf(mission: Mission): MissionTimer? =
        mission.timeLimitSeconds.takeIf { it > 0 }?.let { MissionTimer(it.seconds, it.seconds) }

    private fun Mission.needsPrepare(): Boolean = this is Mission.Step || this is Mission.Squat
}
