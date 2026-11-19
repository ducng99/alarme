package dev.tomng.alarme.core.alarmengine.statemachine

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

/** The per-session mute-during-mission budget: silence is charged and runs out. */
class MissionMuteBudgetTest : RingReducerTestBase() {

    @Test
    fun `leaving a silenced mission charges the silent time against the budget`() {
        val mission = Mission.Math(timeLimitSeconds = 90)
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.OnMissionTimeout(now + 70.seconds))

        val ringing = t.state as RingState.Ringing
        assertThat(ringing.missionMuteLeft).isEqualTo(110.seconds)
        assertThat(ringing.silencedSince).isNull()
    }

    @Test
    fun `the next silence only gets what is left of the budget`() {
        val missions = listOf(Mission.Math(timeLimitSeconds = 30))
        val state = ringing(alarm = alarm(missions = missions), missionMuteLeft = 110.seconds)

        val t = reduce(state, RingEvent.OnDismissClick(now))

        assertThat(t.effects).contains(RingEffect.SilenceRing(110.seconds))
        assertThat((t.state as RingState.Ringing).silencedSince).isEqualTo(now)
    }

    @Test
    fun `an exhausted budget brings the ring back for the rest of the mission`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.MissionMuteExhausted)

        val ringing = t.state as RingState.Ringing
        assertThat(ringing.stage).isEqualTo(state.stage)
        assertThat(ringing.missionMuteLeft).isEqualTo(Duration.ZERO)
        assertThat(ringing.silencedSince).isNull()
        assertThat(t.effects).containsExactly(RingEffect.ResumeRing)
    }

    @Test
    fun `missions keep ringing once the budget is spent`() {
        val missions = listOf(Mission.Math(timeLimitSeconds = 30))
        val state = ringing(alarm = alarm(missions = missions), missionMuteLeft = Duration.ZERO)

        val t = reduce(state, RingEvent.OnDismissClick(now))

        assertThat(t.effects).containsExactly(RingEffect.StartMissionTimer(missions[0], 30.seconds))
    }

    @Test
    fun `leaving a mission after the budget ran out does not resume the ring twice`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(
                alarm = alarm(missions = listOf(mission)),
                stage = missionStage(0, mission),
                missionMuteLeft = Duration.ZERO,
            )

        val t = reduce(state, RingEvent.OnMissionTimeout(now))

        assertThat(t.effects).containsExactly(RingEffect.StopMissionTimer)
    }

    @Test
    fun `a stale budget expiry outside a silence is ignored`() {
        val state = ringing(missionMuteLeft = 50.seconds)

        val t = reduce(state, RingEvent.MissionMuteExhausted)

        assertThat(t.state).isEqualTo(state)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `snoozing from a silenced mission charges the budget`() {
        val mission = Mission.Math(timeLimitSeconds = 30)
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.OnSnoozeClick(now + 40.seconds))

        val ringing = t.state as RingState.Ringing
        assertThat(ringing.missionMuteLeft).isEqualTo(140.seconds)
        assertThat(ringing.silencedSince).isNull()
    }
}
