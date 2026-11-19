package dev.tomng.alarme.core.alarmengine.statemachine

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

/** The wake-up check, blocked/false-alarm deliveries, and the end-to-end journey. */
class WakeUpCheckReducerTest : RingReducerTestBase() {

    // ---------------------------------------------------------------- wake-up check

    @Test
    fun `the pending check starts on the scheduled event and arms the 100 second window`() {
        val alarm = alarm(wucMinutes = 5)
        val state = RingState.WakeUpCheckPending(alarm, now + 300.seconds)

        val t = reduce(state, RingEvent.WakeUpCheckStarted(alarm, startTime = now + 300.seconds))

        assertThat(t.state).isEqualTo(RingState.WakeUpCheck(alarm, now + 300.seconds))
        assertThat(t.effects)
            .containsExactly(
                RingEffect.StartWakeUpCheckTimer(RingStateMachine.WAKE_UP_CHECK_WINDOW),
            )
    }

    @Test
    fun `completing the check returns to idle and stops the window`() {
        val alarm = alarm(wucMinutes = 5)
        val state = RingState.WakeUpCheck(alarm, now + 300.seconds)

        val t = reduce(state, RingEvent.OnWakeUpCheckComplete)

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects)
            .containsExactly(RingEffect.StopWakeUpCheckTimer, RingEffect.CancelWakeUpCheck)
            .inOrder()
    }

    @Test
    fun `cancelling the check returns to idle and stops the window`() {
        val alarm = alarm(wucMinutes = 5)
        val state = RingState.WakeUpCheck(alarm, now + 300.seconds)

        val t = reduce(state, RingEvent.OnWakeUpCheckCancelled)

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects)
            .containsExactly(RingEffect.StopWakeUpCheckTimer, RingEffect.CancelWakeUpCheck)
            .inOrder()
    }

    @Test
    fun `failing the check re-rings the alarm`() {
        val alarm = alarm(wucMinutes = 5)
        val state = RingState.WakeUpCheck(alarm, now + 300.seconds)

        val t = reduce(state, RingEvent.OnWakeUpCheckFailed)

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects)
            .containsExactly(
                RingEffect.StopWakeUpCheckTimer,
                RingEffect.CancelWakeUpCheck,
                RingEffect.ReRing(alarm),
            )
            .inOrder()
    }

    private fun ringRequest(alarm: Alarm): RingEvent.AlarmRingRequest =
        RingEvent.AlarmRingRequest(
            alarm = alarm,
            scheduleId = scheduleId,
            trigger = dev.tomng.alarme.core.alarmengine.statemachine.RingTrigger.SCHEDULED,
            ringEventId = 99L,
            startedAt = now,
        )

    @Test
    fun `a check firing into a fresh session enters the window`() {
        val alarm = alarm(wucMinutes = 5)

        val t = reduce(RingState.Idle, RingEvent.WakeUpCheckStarted(alarm, startTime = now))

        assertThat(t.state).isEqualTo(RingState.WakeUpCheck(alarm, now))
        assertThat(t.effects)
            .containsExactly(
                RingEffect.StartWakeUpCheckTimer(RingStateMachine.WAKE_UP_CHECK_WINDOW),
            )
    }

    @Test
    fun `a new alarm firing during the check preempts it`() {
        val alarm = alarm(wucMinutes = 5)
        val state = RingState.WakeUpCheck(alarm, now)

        val t = reduce(state, ringRequest(alarm = alarm))

        assertThat(t.state).isInstanceOf(RingState.Ringing::class.java)
        assertThat(t.effects)
            .containsAtLeast(
                RingEffect.StopWakeUpCheckTimer,
                RingEffect.CancelWakeUpCheck,
                RingEffect.Ring(alarm),
            )
    }

    @Test
    fun `a new alarm firing while the check is only pending preempts it without a window stop`() {
        val alarm = alarm(wucMinutes = 5)
        val state = RingState.WakeUpCheckPending(alarm, now + 120.seconds)

        val t = reduce(state, ringRequest(alarm = alarm))

        assertThat(t.state).isInstanceOf(RingState.Ringing::class.java)
        assertThat(t.effects).contains(RingEffect.CancelWakeUpCheck)
        assertThat(t.effects).doesNotContain(RingEffect.StopWakeUpCheckTimer)
    }

    @Test
    fun `a restored pending check re-arms itself`() {
        val alarm = alarm(wucMinutes = 5)

        val t =
            reduce(
                RingState.Idle,
                RingEvent.WakeUpCheckRestored(alarm, checkAt = now + 120.seconds),
            )

        assertThat(t.state).isEqualTo(RingState.WakeUpCheckPending(alarm, now + 120.seconds))
        assertThat(t.effects)
            .containsExactly(RingEffect.ScheduleWakeUpCheck(alarm, now + 120.seconds))
    }

    @Test
    fun `snoozing the check re-prompts in 180 seconds`() {
        val alarm = alarm(wucMinutes = 5)
        val state = RingState.WakeUpCheck(alarm, now + 300.seconds)

        val t = reduce(state, RingEvent.OnSnoozeClick(now))

        assertThat(t.state).isEqualTo(RingState.WakeUpCheckPending(alarm, now + 180.seconds))
        assertThat(t.effects)
            .containsExactly(
                RingEffect.StopWakeUpCheckTimer,
                RingEffect.CancelWakeUpCheck,
                RingEffect.ScheduleWakeUpCheck(alarm, now + 180.seconds),
            )
    }

    // ---------------------------------------------------------------- blocked / false alarm

    @Test
    fun `a blocked invalid alarm reschedules and returns to idle`() {
        val t = reduce(RingState.Idle, RingEvent.RingBlocked(BlockedRingReason.INVALID_ALARM))

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects).containsExactly(RingEffect.Reschedule(RescheduleReason.INVALID_ALARM))
    }

    @Test
    fun `a blocked expired alarm reschedules and returns to idle`() {
        val t = reduce(ringing(), RingEvent.RingBlocked(BlockedRingReason.EXPIRED_ALARM))

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects).containsExactly(RingEffect.Reschedule(RescheduleReason.EXPIRED_ALARM))
    }

    @Test
    fun `a false alarm reschedules the next occurrence`() {
        val t = reduce(ringing(), RingEvent.FalseAlarmDetected(scheduleId))

        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects).containsExactly(RingEffect.Reschedule(RescheduleReason.FALSE_ALARM))
    }

    // ---------------------------------------------------------------- the full journey

    @Test
    fun `the full journey - ring, chain, timeout, retry, emergency, wuc fail, re-ring`() {
        val missions =
            listOf(Mission.Shake(timeLimitSeconds = 30), Mission.Math(timeLimitSeconds = 30))
        val alarm = alarm(missions = missions, wucMinutes = 5)

        var t = reduce(RingState.Idle, request(alarm = alarm))
        assertThat(t.state).isInstanceOf(RingState.Ringing::class.java)

        // user hits dismiss -> first mission (Shake, no prepare)
        t = reduce(t.state, RingEvent.OnDismissClick(now))
        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(0, missions[0], MissionTimer(30.seconds, 30.seconds)))

        // shake done -> Math is next
        t = reduce(t.state, RingEvent.OnMissionComplete(now))
        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(1, missions[1], MissionTimer(30.seconds, 30.seconds)))

        // user can't dismiss mid-chain
        t = reduce(t.state, RingEvent.OnDismissClick(now))
        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(1, missions[1], MissionTimer(30.seconds, 30.seconds)))

        // math times out -> back to the ring screen, the ring resumes
        t = reduce(t.state, RingEvent.OnMissionTimeout(now))
        assertThat((t.state as RingState.Ringing).stage).isEqualTo(RingStage.Ring)
        assertThat(t.effects).contains(RingEffect.ResumeRing)

        // dismiss again -> the chain restarts from the first mission
        t = reduce(t.state, RingEvent.OnDismissClick(now))
        assertThat((t.state as RingState.Ringing).stage)
            .isEqualTo(RingStage.Mission(0, missions[0], MissionTimer(30.seconds, 30.seconds)))

        // the user gives up -> emergency, 100 taps -> dismissed (EMERGENCY) + WUC pending
        t = reduce(t.state, RingEvent.OnEmergencyClick(now))
        repeat(RingStateMachine.EMERGENCY_TAPS) {
            t = reduce(t.state, RingEvent.OnEmergencyTap(now))
        }
        assertThat(t.state).isEqualTo(RingState.WakeUpCheckPending(alarm, now + 300.seconds))
        assertThat(t.effects).contains(RingEffect.Dismiss(alarm, DismissReason.EMERGENCY))

        // check starts, user sleeps through it -> re-ring
        t = reduce(t.state, RingEvent.WakeUpCheckStarted(alarm, now + 300.seconds))
        t = reduce(t.state, RingEvent.OnWakeUpCheckFailed)
        assertThat(t.state).isEqualTo(RingState.Idle)
        assertThat(t.effects).contains(RingEffect.ReRing(alarm))
    }
}
