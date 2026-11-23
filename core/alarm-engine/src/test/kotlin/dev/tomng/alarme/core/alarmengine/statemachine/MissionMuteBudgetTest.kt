package dev.tomng.alarme.core.alarmengine.statemachine

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

/**
 * The per-session mute-during-mission budget: only mission timeouts that bring the ring back are
 * counted.
 */
class MissionMuteBudgetTest : RingReducerTestBase() {

    @Test
    fun `a mission timeout spends one mute`() {
        val mission = Mission.Math(timeLimitSeconds = 90)
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.OnMissionTimeout(now + 70.seconds))

        val ringing = t.state as RingState.Ringing
        assertThat(ringing.missionMuteLeft).isEqualTo(2)
        assertThat(ringing.silenced).isFalse()
        assertThat(t.effects).contains(RingEffect.ResumeRing)
    }

    @Test
    fun `completing missions in a chain does not spend any mute`() {
        val missions =
            listOf(Mission.Math(timeLimitSeconds = 30), Mission.Math(timeLimitSeconds = 30))
        val state =
            ringing(alarm = alarm(missions = missions), stage = missionStage(0, missions[0]))

        val t = reduce(state, RingEvent.OnMissionComplete(now + 20.seconds))

        val ringing = t.state as RingState.Ringing
        assertThat(ringing.missionMuteLeft).isEqualTo(3)
        assertThat(ringing.silenced).isTrue()
    }

    @Test
    fun `the next silence is granted while mutes are left`() {
        val missions = listOf(Mission.Math(timeLimitSeconds = 30))
        val state = ringing(alarm = alarm(missions = missions), missionMuteLeft = 1)

        val t = reduce(state, RingEvent.OnDismissClick(now))

        assertThat(t.effects).contains(RingEffect.SilenceRing)
        assertThat((t.state as RingState.Ringing).silenced).isTrue()
    }

    @Test
    fun `missions keep ringing once the mutes are spent`() {
        val missions = listOf(Mission.Math(timeLimitSeconds = 30))
        val state = ringing(alarm = alarm(missions = missions), missionMuteLeft = 0)

        val t = reduce(state, RingEvent.OnDismissClick(now))

        assertThat(t.effects).containsExactly(RingEffect.StartMissionTimer(missions[0], 30.seconds))
    }

    @Test
    fun `leaving an unsilenced mission does not resume the ring twice`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = missionStage(0, mission),
                missionMuteLeft = 0,
            )

        val t = reduce(state, RingEvent.OnMissionTimeout(now))

        assertThat(t.effects).containsExactly(RingEffect.StopMissionTimer)
    }

    @Test
    fun `snoozing from a silenced mission does not spend a mute`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.OnSnoozeClick(now + 40.seconds))

        val ringing = t.state as RingState.Ringing
        assertThat(ringing.missionMuteLeft).isEqualTo(3)
        assertThat(ringing.silenced).isFalse()
    }
}
