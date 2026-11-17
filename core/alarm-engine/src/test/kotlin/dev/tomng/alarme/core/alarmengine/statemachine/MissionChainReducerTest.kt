package dev.tomng.alarme.core.alarmengine.statemachine

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

/**
 * The mission chain, timers, emergency and mute limit (prepare stage, timer reset/stop, the
 * mute-during-mission silence, the 100-tap emergency, mute policy).
 */
class MissionChainReducerTest : RingReducerTestBase() {

    // ---------------------------------------------------------------- mission chain

    @Test
    fun `dismiss with missions starts the first mission and silences the ring`() {
        val missions =
            listOf(Mission.Math(timeLimitSeconds = 30), Mission.Typing(timeLimitSeconds = 20))
        val state = ringing(alarm = alarm(missions = missions))

        val t = reduce(state, RingEvent.OnDismissClick(now))

        val ringing = t.state as RingState.Ringing
        assertThat(ringing.stage)
            .isEqualTo(RingStage.Mission(0, missions[0], MissionTimer(30.seconds, 30.seconds)))
        assertThat(t.effects)
            .containsExactly(
                RingEffect.StartMissionTimer(missions[0], 30.seconds),
                RingEffect.SilenceRing,
            )
            .inOrder()
    }

    @Test
    fun `the ring keeps sounding during a mission when mute during mission is off`() {
        val missions = listOf(Mission.Math(timeLimitSeconds = 30))
        val state = ringing(alarm = alarm(missions = missions), muteDuringMission = false)

        val t = reduce(state, RingEvent.OnDismissClick(now))

        assertThat(t.effects).containsExactly(RingEffect.StartMissionTimer(missions[0], 30.seconds))
    }

    @Test
    fun `a mission without a time limit gets the default inactivity limit`() {
        val missions = listOf(Mission.Math())
        val state = ringing(alarm = alarm(missions = missions))

        val t = reduce(state, RingEvent.OnDismissClick(now))

        val limit = RingStateMachine.DEFAULT_MISSION_TIME_LIMIT
        assertThat(limit).isEqualTo(60.seconds)
        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(0, missions[0], MissionTimer(limit, limit)))
        assertThat(t.effects).contains(RingEffect.StartMissionTimer(missions[0], limit))
    }

    @Test
    fun `step and squat missions get a prepare stage first and keep ringing there`() {
        for (mission in listOf(Mission.Step(), Mission.Squat())) {
            val state = ringing(alarm = alarm(missions = listOf(mission)))

            val t = reduce(state, RingEvent.OnDismissClick(now))

            assertThat((t.state as RingState.Ringing).stage)
                .isEqualTo(RingStage.PrepareMission(0, mission))
            assertThat(t.effects).isEmpty()
        }
    }

    @Test
    fun `the prepare stage starts the mission on request and silences the ring`() {
        val mission = Mission.Step(timeLimitSeconds = 30)
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = RingStage.PrepareMission(0, mission),
            )

        val t = reduce(state, RingEvent.OnMissionStart)

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(0, mission, MissionTimer(30.seconds, 30.seconds)))
        assertThat(t.effects)
            .containsExactly(
                RingEffect.StartMissionTimer(mission, 30.seconds),
                RingEffect.SilenceRing,
            )
            .inOrder()
    }

    @Test
    fun `completing a mission advances to the next and stays silent`() {
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
    fun `the next mission prepare stage resumes the ring`() {
        val missions =
            listOf(Mission.Math(timeLimitSeconds = 30), Mission.Step(timeLimitSeconds = 30))
        val state =
            ringing(alarm = alarm(missions = missions), stage = missionStage(0, missions[0]))

        val t = reduce(state, RingEvent.OnMissionComplete(now))

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.PrepareMission(1, missions[1]))
        assertThat(t.effects)
            .containsExactly(RingEffect.StopMissionTimer, RingEffect.ResumeRing)
            .inOrder()
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
    fun `a mission timeout fails back to the ring stage and resumes the ring`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.OnMissionTimeout)

        assertThat((t.state as RingState.Ringing).stage).isEqualTo(RingStage.Ring)
        assertThat(t.effects)
            .containsExactly(RingEffect.StopMissionTimer, RingEffect.ResumeRing)
            .inOrder()
    }

    @Test
    fun `a mission timeout does not resume a ring that was never silenced`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = missionStage(0, mission),
                muteDuringMission = false,
            )

        val t = reduce(state, RingEvent.OnMissionTimeout)

        assertThat((t.state as RingState.Ringing).stage).isEqualTo(RingStage.Ring)
        assertThat(t.effects).containsExactly(RingEffect.StopMissionTimer)
    }

    @Test
    fun `dismissing after a failed mission restarts the chain from the first mission`() {
        val missions =
            listOf(Mission.Math(timeLimitSeconds = 30), Mission.Typing(timeLimitSeconds = 20))
        val state =
            ringing(alarm = alarm(missions = missions), stage = missionStage(1, missions[1]))

        val failed = reduce(state, RingEvent.OnMissionTimeout)
        val t = reduce(failed.state, RingEvent.OnDismissClick(now))

        assertThat((t.state as RingState.Ringing).stage).isEqualTo(missionStage(0, missions[0]))
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

    // ---------------------------------------------------------------- emergency

    @Test
    fun `emergency from a mission stops the timer and resumes the ring`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.OnEmergencyClick)

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Emergency(0, mission, taps = 0))
        assertThat(t.effects)
            .containsExactly(RingEffect.StopMissionTimer, RingEffect.ResumeRing)
            .inOrder()
    }

    @Test
    fun `emergency is available from the prepare stage`() {
        val mission = Mission.Step()
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = RingStage.PrepareMission(0, mission),
            )

        val t = reduce(state, RingEvent.OnEmergencyClick)

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Emergency(0, mission, taps = 0))
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `emergency is not offered from the ring stage`() {
        val state = ringing(alarm = alarm(missions = listOf(Mission.Math())))

        val t = reduce(state, RingEvent.OnEmergencyClick)

        assertThat(t.state).isEqualTo(state)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `each emergency tap counts toward the goal`() {
        val mission = Mission.Math()
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = RingStage.Emergency(0, mission, taps = 41),
            )

        val t = reduce(state, RingEvent.OnEmergencyTap(now))

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Emergency(0, mission, taps = 42))
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `the hundredth emergency tap dismisses with the emergency reason`() {
        val mission = Mission.Math()
        assertThat(RingStateMachine.EMERGENCY_TAPS).isEqualTo(100)
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = RingStage.Emergency(0, mission, taps = 99),
            )

        val t = reduce(state, RingEvent.OnEmergencyTap(now))

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects)
            .containsExactly(
                RingEffect.Dismiss(alarm(missions = listOf(mission)), DismissReason.EMERGENCY),
                RingEffect.ResetSnooze,
                RingEffect.StopAutoDismissTimer,
            )
    }

    @Test
    fun `cancelling the emergency returns to the mission with a fresh timer and silences again`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = RingStage.Emergency(0, mission, taps = 12),
            )

        val t = reduce(state, RingEvent.OnEmergencyCancel)

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(0, mission, MissionTimer(30.seconds, 30.seconds)))
        assertThat(t.effects)
            .containsExactly(
                RingEffect.StartMissionTimer(mission, 30.seconds),
                RingEffect.SilenceRing,
            )
            .inOrder()
    }

    @Test
    fun `cancelling the emergency of a step mission returns to its prepare stage`() {
        val mission = Mission.Step()
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = RingStage.Emergency(0, mission, taps = 3),
            )

        val t = reduce(state, RingEvent.OnEmergencyCancel)

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.PrepareMission(0, mission))
        assertThat(t.effects).isEmpty()
    }

    // ---------------------------------------------------------------- snooze while silenced

    @Test
    fun `snoozing from a silenced mission does not resume the ring`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.OnSnoozeClick(now))

        assertThat(t.effects).doesNotContain(RingEffect.ResumeRing)
    }

    @Test
    fun `resuming from snooze into a mission silences after the ring restarts`() {
        val missions = listOf(Mission.Math(timeLimitSeconds = 30))
        val state =
            ringing(
                alarm = alarm(missions = missions),
                stage = RingStage.Snoozing(now + 300.seconds),
            )

        val t = reduce(state, RingEvent.OnDismissClick(now))

        assertThat(t.effects)
            .containsExactly(
                RingEffect.ResumeFromSnooze(state.alarm),
                RingEffect.StartMissionTimer(missions[0], 30.seconds),
                RingEffect.SilenceRing,
            )
            .inOrder()
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
