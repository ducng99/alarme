package dev.tomng.alarme.core.alarmengine.statemachine

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

/**
 * The mission chain, timers, emergency and mute limit (prepare stage, +10 s, timer reset/stop, 40 s
 * emergency, mute policy).
 */
class MissionChainReducerTest : RingReducerTestBase() {

    // ---------------------------------------------------------------- mission chain

    @Test
    fun `dismiss with missions starts the first mission directly when it needs no prepare`() {
        val missions =
            listOf(Mission.Math(timeLimitSeconds = 30), Mission.Typing(timeLimitSeconds = 20))
        val state = ringing(alarm = alarm(missions = missions))

        val t = reduce(state, RingEvent.OnDismissClick(now))

        val ringing = t.state as RingState.Ringing
        assertThat(ringing.stage)
            .isEqualTo(RingStage.Mission(0, missions[0], MissionTimer(30.seconds, 30.seconds)))
        assertThat(t.effects).containsExactly(RingEffect.StartMissionTimer(missions[0], 30.seconds))
    }

    @Test
    fun `dismiss with an untimed mission starts it without a timer`() {
        val missions = listOf(Mission.Math())
        val state = ringing(alarm = alarm(missions = missions))

        val t = reduce(state, RingEvent.OnDismissClick(now))

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(0, missions[0], null))
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `step and squat missions get a prepare stage first`() {
        for (mission in listOf(Mission.Step(), Mission.Squat())) {
            val state = ringing(alarm = alarm(missions = listOf(mission)))

            val t = reduce(state, RingEvent.OnDismissClick(now))

            assertThat((t.state as RingState.Ringing).stage)
                .isEqualTo(RingStage.PrepareMission(0, mission))
            assertThat(t.effects).isEmpty()
        }
    }

    @Test
    fun `the prepare stage starts the mission on request`() {
        val mission = Mission.Step(timeLimitSeconds = 30)
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = RingStage.PrepareMission(0, mission),
            )

        val t = reduce(state, RingEvent.OnMissionStart)

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(0, mission, MissionTimer(30.seconds, 30.seconds)))
        assertThat(t.effects).containsExactly(RingEffect.StartMissionTimer(mission, 30.seconds))
    }

    @Test
    fun `completing a mission advances to the next`() {
        val missions =
            listOf(Mission.Math(timeLimitSeconds = 30), Mission.Typing(timeLimitSeconds = 20))
        val state =
            ringing(alarm = alarm(missions = missions), stage = missionStage(0, missions[0]))

        val t = reduce(state, RingEvent.OnMissionComplete(now))

        val ringing = t.state as RingState.Ringing
        assertThat(ringing.stage)
            .isEqualTo(RingStage.Mission(1, missions[1], MissionTimer(20.seconds, 20.seconds)))
        assertThat(t.effects)
            .containsExactly(
                RingEffect.StopMissionTimer,
                RingEffect.StartMissionTimer(missions[1], 20.seconds),
            )
            .inOrder()
    }

    @Test
    fun `the next mission prepare stage gets no timer`() {
        val missions =
            listOf(Mission.Math(timeLimitSeconds = 30), Mission.Step(timeLimitSeconds = 30))
        val state =
            ringing(alarm = alarm(missions = missions), stage = missionStage(0, missions[0]))

        val t = reduce(state, RingEvent.OnMissionComplete(now))

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.PrepareMission(1, missions[1]))
        assertThat(t.effects).containsExactly(RingEffect.StopMissionTimer)
    }

    @Test
    fun `completing the last mission dismisses normally`() {
        val missions = listOf(Mission.Math(timeLimitSeconds = 30))
        val state =
            ringing(alarm = alarm(missions = missions), stage = missionStage(0, missions[0]))

        val t = reduce(state, RingEvent.OnMissionComplete(now))

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects)
            .containsExactly(
                RingEffect.StopMissionTimer,
                RingEffect.Dismiss(alarm(missions = missions), DismissReason.NORMAL),
                RingEffect.ResetSnooze,
                RingEffect.StopAutoDismissTimer,
            )
    }

    @Test
    fun `completing the last mission arms the wake-up check when configured`() {
        val missions = listOf(Mission.Math(timeLimitSeconds = 30))
        val alarm = alarm(missions = missions, wucMinutes = 5)
        val state = ringing(alarm = alarm, stage = missionStage(0, missions[0]))

        val t = reduce(state, RingEvent.OnMissionComplete(now))

        assertThat(t.state).isEqualTo(RingState.WakeUpCheckPending(alarm, now + 300.seconds))
        assertThat(t.effects).contains(RingEffect.ScheduleWakeUpCheck(alarm, now + 300.seconds))
    }

    @Test
    fun `dismiss during a mission is ignored`() {
        val mission = Mission.Shake()
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.OnDismissClick(now))

        assertThat(t.state).isEqualTo(state)
        assertThat(t.effects).isEmpty()
    }

    // ---------------------------------------------------------------- timers

    @Test
    fun `a mission timeout escalates to the 40 second emergency`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.OnMissionTimeout)

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Emergency(0, mission, RingStateMachine.EMERGENCY_TIMEOUT))
        assertThat(t.effects)
            .containsExactly(
                RingEffect.StopMissionTimer,
                RingEffect.StartEmergencyTimer(RingStateMachine.EMERGENCY_TIMEOUT),
            )
            .inOrder()
    }

    @Test
    fun `plus ten seconds extends the mission timer`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.OnAddMissionTimeClick)

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(0, mission, MissionTimer(40.seconds, 30.seconds)))
        assertThat(t.effects)
            .containsExactly(RingEffect.ExtendMissionTime(RingStateMachine.ADD_MISSION_TIME))
    }

    @Test
    fun `plus ten seconds on an untimed mission is a no-op`() {
        val mission = Mission.Math()
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.OnAddMissionTimeClick)

        assertThat(t.state).isEqualTo(state)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `mission timer ticks update the remaining time`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.OnMissionTimerTick(7.seconds))

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(0, mission, MissionTimer(7.seconds, 30.seconds)))
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `user activity resets the mission timer to full`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = RingStage.Mission(0, mission, MissionTimer(3.seconds, 30.seconds)),
            )

        val t = reduce(state, RingEvent.OnMissionTimerReset)

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(0, mission, MissionTimer(30.seconds, 30.seconds)))
        assertThat(t.effects).containsExactly(RingEffect.StartMissionTimer(mission, 30.seconds))
    }

    @Test
    fun `a confirmed match pauses the mission timer`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.OnMissionTimerStop)

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(0, mission, null))
        assertThat(t.effects).containsExactly(RingEffect.StopMissionTimer)
    }

    // ---------------------------------------------------------------- emergency

    @Test
    fun `completing the emergency dismisses with the emergency reason`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = RingStage.Emergency(0, mission, 12.seconds),
            )

        val t = reduce(state, RingEvent.OnEmergencyComplete(now))

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects)
            .containsExactly(
                RingEffect.StopMissionTimer,
                RingEffect.Dismiss(alarm(missions = listOf(mission)), DismissReason.EMERGENCY),
                RingEffect.ResetSnooze,
                RingEffect.StopAutoDismissTimer,
            )
    }

    @Test
    fun `cancelling the emergency returns to the mission with a fresh timer`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = RingStage.Emergency(0, mission, 12.seconds),
            )

        val t = reduce(state, RingEvent.OnEmergencyCancel)

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(0, mission, MissionTimer(30.seconds, 30.seconds)))
        assertThat(t.effects).containsExactly(RingEffect.StartMissionTimer(mission, 30.seconds))
    }

    @Test
    fun `emergency timer ticks update the countdown`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = RingStage.Emergency(0, mission, 40.seconds),
            )

        val t = reduce(state, RingEvent.OnEmergencyTimerTick(12.seconds, now))

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Emergency(0, mission, 12.seconds))
    }

    @Test
    fun `the emergency expiring dismisses with the emergency reason`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = RingStage.Emergency(0, mission, 1.seconds),
            )

        val t = reduce(state, RingEvent.OnEmergencyTimerTick(Duration.ZERO, now))

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects)
            .contains(
                RingEffect.Dismiss(alarm(missions = listOf(mission)), DismissReason.EMERGENCY),
            )
    }

    // ---------------------------------------------------------------- mute limit

    @Test
    fun `a mute request increments the count and mutes`() {
        val t = reduce(ringing(), RingEvent.VolumeMuteRequested)

        assertThat((t.state as RingState.Ringing).mute.count).isEqualTo(1)
        assertThat(t.effects).containsExactly(RingEffect.MuteAlarm)
    }

    @Test
    fun `a mute request at the limit is denied`() {
        val state = ringing().let { it.copy(mute = it.mute.copy(count = 3)) }

        val t = reduce(state, RingEvent.VolumeMuteRequested)

        assertThat((t.state as RingState.Ringing).mute.count).isEqualTo(3)
        assertThat(t.effects).containsExactly(RingEffect.MuteDenied)
    }

    @Test
    fun `a mute request is denied when the setting is off`() {
        val state = ringing().let { it.copy(mute = it.mute.copy(enabled = false)) }

        val t = reduce(state, RingEvent.VolumeMuteRequested)

        assertThat(t.effects).containsExactly(RingEffect.MuteDenied)
    }

    @Test
    fun `word beat missions may always be muted - the mic is the pointer`() {
        val mission = Mission.WordBeat()
        val state =
            ringing(alarm = alarm(missions = listOf(mission))).let {
                it.copy(
                    mute = it.mute.copy(count = 3, enabled = false),
                    stage = missionStage(0, mission),
                )
            }

        val t = reduce(state, RingEvent.VolumeMuteRequested)

        assertThat((t.state as RingState.Ringing).mute.count).isEqualTo(4)
        assertThat(t.effects).containsExactly(RingEffect.MuteAlarm)
    }
}
