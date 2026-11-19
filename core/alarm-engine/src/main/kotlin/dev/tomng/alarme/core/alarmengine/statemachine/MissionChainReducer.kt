package dev.tomng.alarme.core.alarmengine.statemachine

import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * The mission chain: prepare → mission → next → dismiss, with the per-mission inactivity timer, the
 * budgeted mute-during-mission silence, and the timeout → back-to-ring failure.
 */
internal object MissionChainReducer {

    fun onMissionStart(state: RingState.Ringing, now: Instant): RingTransition =
        when (val stage = state.stage) {
            // The prepare screen already ran: start the mission itself, no second prepare.
            is RingStage.PrepareMission -> startMission(state, stage.index, stage.mission, now)
            else -> RingTransition(state)
        }

    fun onMissionComplete(state: RingState.Ringing, now: Instant): RingTransition {
        val stage = state.stage as? RingStage.Mission ?: return RingTransition(state)
        val next = stage.index + 1
        val missions = state.alarm.missions
        return if (next < missions.size) {
            val entering = enterMission(state, next, missions[next], now)
            RingTransition(entering.state, listOf(RingEffect.StopMissionTimer) + entering.effects)
        } else {
            DismissReducer.complete(state, DismissReason.NORMAL, now, stopMissionTimer = true)
        }
    }

    /** A failed mission returns to the loud ring; the next dismiss restarts the whole chain. */
    fun onMissionTimeout(state: RingState.Ringing, now: Instant): RingTransition {
        if (state.stage !is RingStage.Mission) return RingTransition(state)
        val resumed = MissionMute.end(state, now)
        return RingTransition(
            state = resumed.state.copy(stage = RingStage.Ring),
            effects = listOf(RingEffect.StopMissionTimer) + resumed.effects,
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
    fun enterMission(
        state: RingState.Ringing,
        index: Int,
        mission: Mission,
        now: Instant,
    ): RingTransition {
        if (mission.needsPrepare()) {
            val resumed = MissionMute.end(state, now)
            return RingTransition(
                resumed.state.copy(stage = RingStage.PrepareMission(index, mission)),
                resumed.effects,
            )
        }
        return startMission(state, index, mission, now)
    }

    fun startMission(
        state: RingState.Ringing,
        index: Int,
        mission: Mission,
        now: Instant,
    ): RingTransition {
        val timer = timerOf(mission)
        val silenced = MissionMute.start(state, now)
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

    fun onMuteExhausted(state: RingState.Ringing): RingTransition {
        if (state.silencedSince == null) return RingTransition(state)
        return RingTransition(
            state.copy(missionMuteLeft = Duration.ZERO, silencedSince = null),
            listOf(RingEffect.ResumeRing),
        )
    }

    private fun Mission.needsPrepare(): Boolean = this is Mission.Step || this is Mission.Squat
}

/**
 * The mute-during-mission budget: silence is only granted while [RingState.Ringing.missionMuteLeft]
 * lasts, and every silent stretch is charged against it when it ends.
 */
internal object MissionMute {

    data class Step(val state: RingState.Ringing, val effects: List<RingEffect> = emptyList())

    fun start(state: RingState.Ringing, now: Instant): Step {
        val canSilence =
            state.muteDuringMission &&
                state.silencedSince == null &&
                state.missionMuteLeft > Duration.ZERO
        if (!canSilence) return Step(state)
        return Step(
            state.copy(silencedSince = now),
            listOf(RingEffect.SilenceRing(state.missionMuteLeft)),
        )
    }

    /** Charges the silent stretch; [resume] is false when the sound stays off anyway (snooze). */
    fun end(state: RingState.Ringing, now: Instant, resume: Boolean = true): Step {
        val since = state.silencedSince ?: return Step(state)
        val left = (state.missionMuteLeft - (now - since)).coerceAtLeast(Duration.ZERO)
        return Step(
            state.copy(missionMuteLeft = left, silencedSince = null),
            if (resume) listOf(RingEffect.ResumeRing) else emptyList(),
        )
    }
}
