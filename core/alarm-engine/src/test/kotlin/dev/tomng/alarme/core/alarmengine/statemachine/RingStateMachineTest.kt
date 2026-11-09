package dev.tomng.alarme.core.alarmengine.statemachine

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

/** Ring start, dismiss, snooze and auto-dismiss. */
class RingStateMachineTest : RingReducerTestBase() {

    // ---------------------------------------------------------------- ring start

    @Test
    fun `a ring request from idle starts ringing with fresh mute and snooze state`() {
        val t = reduce(RingState.Idle, request())

        val ringing = t.state as RingState.Ringing
        assertThat(ringing.alarm.id).isEqualTo(7L)
        assertThat(ringing.stage).isEqualTo(RingStage.Ring)
        assertThat(ringing.startedAt).isEqualTo(now)
        assertThat(ringing.mute).isEqualTo(MuteState(count = 0, limit = 3, enabled = true))
        assertThat(ringing.snooze).isEqualTo(SnoozeState(count = 0, limit = 3))
        assertThat(t.effects).containsExactly(RingEffect.Ring(ringing.alarm))
    }

    @Test
    fun `a ring request with auto dismiss arms the auto dismiss timer`() {
        val t = reduce(RingState.Idle, request(autoDismiss = 60.seconds))

        assertThat(t.effects)
            .containsAtLeast(RingEffect.StartAutoDismissTimer(60.seconds), RingEffect.Ring(alarm()))
        assertThat((t.state as RingState.Ringing).autoDismiss).isEqualTo(60.seconds)
    }

    @Test
    fun `a ring request carries the persisted snooze count forward`() {
        val t = reduce(RingState.Idle, request(snoozeCount = 2))

        assertThat((t.state as RingState.Ringing).snooze)
            .isEqualTo(SnoozeState(count = 2, limit = 3))
    }

    @Test
    fun `a restored ring restarts the ring instead of starting fresh`() {
        val event =
            RingEvent.AlarmRingRestored(
                alarm = alarm(),
                scheduleId = scheduleId,
                trigger = RingTrigger.RESTORED,
                ringEventId = 11L,
                startedAt = now,
                autoDismiss = null,
                snoozeCount = 1,
            )

        val t = reduce(RingState.Idle, event)

        val ringing = t.state as RingState.Ringing
        assertThat(ringing.trigger).isEqualTo(RingTrigger.RESTORED)
        assertThat(ringing.snooze.count).isEqualTo(1)
        assertThat(t.effects).containsExactly(RingEffect.RestoreRing(ringing.alarm))
    }

    @Test
    fun `a second ring request while already ringing is dropped`() {
        val state = ringing()

        val t = reduce(state, request())

        assertThat(t.state).isEqualTo(state)
        assertThat(t.effects).isEmpty()
    }

    // ---------------------------------------------------------------- dismiss (no missions)

    @Test
    fun `dismissing a mission-less alarm dismisses normally`() {
        val t = reduce(ringing(), RingEvent.OnDismissClick(now))

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects)
            .containsExactly(
                RingEffect.Dismiss(alarm(), DismissReason.NORMAL),
                RingEffect.ResetSnooze,
                RingEffect.StopAutoDismissTimer,
            )
            .inOrder()
    }

    @Test
    fun `dismissing with a wake-up check configured arms the pending check`() {
        val alarm = alarm(wucMinutes = 5)
        val checkAt = now + 5.seconds * 60

        val t = reduce(ringing(alarm = alarm), RingEvent.OnDismissClick(now))

        assertThat(t.state).isEqualTo(RingState.WakeUpCheckPending(alarm, checkAt))
        assertThat(t.effects)
            .containsAtLeast(
                RingEffect.Dismiss(alarm, DismissReason.NORMAL),
                RingEffect.ScheduleWakeUpCheck(alarm, checkAt),
            )
    }

    // ---------------------------------------------------------------- snooze

    @Test
    fun `snoozing moves to the snooze stage and increments the count`() {
        val t = reduce(ringing(), RingEvent.OnSnoozeClick(now))

        val ringing = t.state as RingState.Ringing
        assertThat(ringing.stage).isEqualTo(RingStage.Snoozing(finishAt = now + 300.seconds))
        assertThat(ringing.snooze.count).isEqualTo(1)
        assertThat(t.effects)
            .containsExactly(
                RingEffect.StopMissionTimer,
                RingEffect.StopAutoDismissTimer,
                RingEffect.Snooze(alarm(), now + 300.seconds, 300.seconds, 1),
            )
    }

    @Test
    fun `snooze uses the explicit duration when given`() {
        val t = reduce(ringing(), RingEvent.OnSnoozeClick(now, duration = 10.seconds))

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Snoozing(now + 10.seconds))
        assertThat(t.effects).contains(RingEffect.Snooze(alarm(), now + 10.seconds, 10.seconds, 1))
    }

    @Test
    fun `snooze is ignored when the alarm has snooze disabled`() {
        val state = ringing(alarm = alarm(snoozeEnabled = false))

        val t = reduce(state, RingEvent.OnSnoozeClick(now))

        assertThat(t.state).isEqualTo(state)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `snooze is ignored once the limit is reached`() {
        val state = ringing(snoozeCount = 3)

        val t = reduce(state, RingEvent.OnSnoozeClick(now))

        assertThat(t.state).isEqualTo(state)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `unlimited snooze never blocks`() {
        val state = ringing(alarm = alarm(snoozeLimit = -1), snoozeCount = 99)

        val t = reduce(state, RingEvent.OnSnoozeClick(now))

        assertThat((t.state as RingState.Ringing).snooze.count).isEqualTo(100)
    }

    @Test
    fun `snooze works from the mission stage`() {
        val state =
            ringing(stage = missionStage(0, dev.tomng.alarme.core.model.mission.Mission.Shake()))

        val t = reduce(state, RingEvent.OnSnoozeClick(now))

        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Snoozing(now + 300.seconds))
    }

    @Test
    fun `snooze during emergency is ignored`() {
        val state =
            ringing(
                stage =
                    RingStage.Emergency(
                        0,
                        dev.tomng.alarme.core.model.mission.Mission.Shake(),
                        40.seconds,
                    ),
            )

        val t = reduce(state, RingEvent.OnSnoozeClick(now))

        assertThat(t.state).isEqualTo(state)
        assertThat(t.effects).isEmpty()
    }

    @Test
    fun `snooze while already snoozing is ignored`() {
        val state = ringing(stage = RingStage.Snoozing(now + 300.seconds))

        val t = reduce(state, RingEvent.OnSnoozeClick(now))

        assertThat(t.state).isEqualTo(state)
    }

    @Test
    fun `dismissing from the snooze stage cancels the snooze and ends the ring`() {
        val state = ringing(stage = RingStage.Snoozing(now + 300.seconds), snoozeCount = 1)

        val t = reduce(state, RingEvent.OnDismissClick(now))

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects)
            .containsExactly(
                RingEffect.Dismiss(alarm(), DismissReason.NORMAL),
                RingEffect.ResetSnooze,
                RingEffect.StopAutoDismissTimer,
            )
            .inOrder()
    }

    @Test
    fun `the snooze delivery re-rings with a fresh stage and the persisted count`() {
        val state = ringing(stage = RingStage.Snoozing(now + 300.seconds))

        val t = reduce(state, request(trigger = RingTrigger.SNOOZE, snoozeCount = 2))

        val ringing = t.state as RingState.Ringing
        assertThat(ringing.stage).isEqualTo(RingStage.Ring)
        assertThat(ringing.snooze).isEqualTo(SnoozeState(count = 2, limit = 3))
        assertThat(t.effects).containsExactly(RingEffect.Ring(alarm()))
    }

    @Test
    fun `the snooze delivery re-arms the auto dismiss timer`() {
        val state = ringing(stage = RingStage.Snoozing(now + 300.seconds), autoDismiss = 60.seconds)

        val t = reduce(state, request(trigger = RingTrigger.SNOOZE, autoDismiss = 60.seconds))

        assertThat(t.effects)
            .containsAtLeast(RingEffect.Ring(alarm()), RingEffect.StartAutoDismissTimer(60.seconds))
    }

    // ---------------------------------------------------------------- auto dismiss

    @Test
    fun `the auto dismiss timeout dismisses with the auto reason`() {
        val t = reduce(ringing(autoDismiss = 60.seconds), RingEvent.AutoDismissTimeout(now))

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects)
            .containsExactly(
                RingEffect.Dismiss(alarm(), DismissReason.AUTO),
                RingEffect.ResetSnooze,
                RingEffect.StopAutoDismissTimer,
            )
    }

    @Test
    fun `auto dismiss during a mission still dismisses`() {
        val mission = dev.tomng.alarme.core.model.mission.Mission.Shake()
        val state =
            ringing(alarm = alarm(missions = listOf(mission)), stage = missionStage(0, mission))

        val t = reduce(state, RingEvent.AutoDismissTimeout(now))

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects)
            .contains(RingEffect.Dismiss(alarm(missions = listOf(mission)), DismissReason.AUTO))
    }

    @Test
    fun `auto dismiss during snooze is ignored`() {
        val state = ringing(stage = RingStage.Snoozing(now + 300.seconds))

        val t = reduce(state, RingEvent.AutoDismissTimeout(now))

        assertThat(t.state).isEqualTo(state)
        assertThat(t.effects).isEmpty()
    }

    // ---------------------------------------------------------------- unknown-state robustness

    @Test
    fun `events that do not apply to the current state are ignored`() {
        val cases: List<Pair<RingState, RingEvent>> =
            listOf(
                RingState.Idle to RingEvent.OnDismissClick(now),
                RingState.Idle to RingEvent.OnSnoozeClick(now),
                RingState.Idle to RingEvent.OnMissionComplete(now),
                RingState.Idle to RingEvent.OnMissionTimeout,
                RingState.Idle to RingEvent.AutoDismissTimeout(now),
                RingState.Idle to RingEvent.VolumeMuteRequested,
                RingState.Idle to RingEvent.OnWakeUpCheckComplete,
                RingState.Idle to RingEvent.OnEmergencyComplete(now),
                RingState.Idle to RingEvent.OnAddMissionTimeClick,
            )

        for ((state, event) in cases) {
            val t = reduce(state, event)
            assertThat(t.state).isEqualTo(state)
            assertThat(t.effects).isEmpty()
        }
    }
}
