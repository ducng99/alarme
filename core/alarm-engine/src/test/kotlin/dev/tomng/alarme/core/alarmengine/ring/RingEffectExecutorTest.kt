package dev.tomng.alarme.core.alarmengine.ring

import android.app.Application
import android.content.ComponentName
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.service.AlarmService
import dev.tomng.alarme.core.alarmengine.statemachine.RingEffect
import dev.tomng.alarme.core.alarmengine.statemachine.RingEvent
import dev.tomng.alarme.core.alarmengine.statemachine.RingSessionStore
import dev.tomng.alarme.core.alarmengine.statemachine.RingStage
import dev.tomng.alarme.core.alarmengine.statemachine.RingState
import dev.tomng.alarme.core.alarmengine.statemachine.RingStateMachine
import dev.tomng.alarme.core.alarmengine.statemachine.RingTrigger
import dev.tomng.alarme.core.common.time.FakeAppClock
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.scheduling.ScheduleId
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContextWrapper
import org.robolectric.shadows.ShadowLooper

/**
 * The effect executor: it runs the reducer's commands — audio start/stop via the primed request,
 * service shutdown on dismiss, and the two ring-loop timers (auto-dismiss and snooze finish).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingEffectExecutorTest {

    private val context: ContextWrapper = ApplicationProvider.getApplicationContext<Application>()
    private val mainLooper: ShadowLooper = shadowOf(android.os.Looper.getMainLooper())

    private val clock = FakeAppClock(now = Instant.parse("2026-11-10T06:00:00Z"))
    private val store = RingSessionStore()
    private val host = RecordingRingHost()
    private val scheduler = dev.tomng.alarme.core.alarmengine.FakeAlarmScheduler()
    private val snoozeStore = dev.tomng.alarme.core.alarmengine.InMemorySnoozeStore()
    private val alarmRepository = RecordingAlarmRepository()
    private val randomMissionStore = dev.tomng.alarme.core.alarmengine.InMemoryRandomMissionStore()
    private val missionPicker = RingMissionPicker(randomMissionStore, kotlin.random.Random(1))
    private lateinit var executor: RingEffectExecutor

    private val alarm =
        Alarm(
            id = 7L,
            name = "Work",
            hour = 6,
            minute = 0,
            createdAt = clock.now,
            updatedAt = clock.now,
        )
    private val scheduleId =
        ScheduleId(
            deviceId = "d",
            alarmId = 7L,
            alarmTime = "20261110_0600",
            nowTime = "20261109_2200",
        )

    @Before
    fun setUp() {
        executor =
            RingEffectExecutor(
                context,
                host,
                clock,
                store,
                scheduler,
                snoozeStore,
                dev.tomng.alarme.core.alarmengine.notifications.AlarmNotifications(context) {
                    android.content.Intent("dev.tomng.alarme.action.OPEN_RING")
                },
                alarmRepository,
                missionPicker,
                kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.Dispatchers.Unconfined,
                ),
            )
        executor.prime(
            RingRequestBuilder.build(alarm, defaultSettings(), hasExternalOutput = false),
        )
    }

    @Test
    fun `a ring effect starts the primed audio`() {
        store.submit(ringRequest())

        mainLooper.idle()

        assertThat(host.started).isEqualTo(1)
        assertThat(host.stopped).isEqualTo(0)
    }

    @Test
    fun `a ring effect without a primed request does nothing`() {
        val bareExecutor =
            RingEffectExecutor(
                context,
                host,
                clock,
                RingSessionStore(),
                scheduler,
                snoozeStore,
                dev.tomng.alarme.core.alarmengine.notifications.AlarmNotifications(context) {
                    android.content.Intent("dev.tomng.alarme.action.OPEN_RING")
                },
                alarmRepository,
                missionPicker,
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined),
            )
        bareExecutor.onEffect(RingEffect.Ring(alarm))

        mainLooper.idle()

        assertThat(host.started).isEqualTo(0)
    }

    @Test
    fun `dismiss stops the audio and the service`() {
        store.submit(ringRequest())
        mainLooper.idle()

        store.submit(RingEvent.OnDismissClick(clock.now))
        mainLooper.idle()

        assertThat(host.stopped).isEqualTo(1)
        val shadowContext: ShadowContextWrapper = shadowOf(context)
        assertThat(shadowContext.getNextStoppedService()?.component)
            .isEqualTo(ComponentName(context, AlarmService::class.java))
    }

    @Test
    fun `dismissing forgets the random mission pick so the next occurrence picks again`() {
        missionPicker.pick(
            alarm.copy(missions = listOf(Mission.Math(), Mission.Shake()), randomMissionCount = 1),
            scheduleId = scheduleId.raw,
        )
        store.submit(ringRequest())
        mainLooper.idle()

        store.submit(RingEvent.OnDismissClick(clock.now))
        mainLooper.idle()

        assertThat(randomMissionStore.load()).isNull()
    }

    @Test
    fun `snoozing keeps the random mission pick`() {
        missionPicker.pick(
            alarm.copy(missions = listOf(Mission.Math(), Mission.Shake()), randomMissionCount = 1),
            scheduleId = scheduleId.raw,
        )
        store.submit(ringRequest())
        mainLooper.idle()

        store.submit(RingEvent.OnSnoozeClick(clock.now))
        mainLooper.idle()

        assertThat(randomMissionStore.load()).isNotNull()
    }

    @Test
    fun `dismissing a one-shot alarm turns it off`() {
        val oneShot = alarm.copy(daysOfWeek = dev.tomng.alarme.core.model.alarm.DaysOfWeek.NONE)
        executor.prime(
            RingRequestBuilder.build(oneShot, defaultSettings(), hasExternalOutput = false),
        )
        store.submit(ringRequest(alarm = oneShot))
        mainLooper.idle()

        store.submit(RingEvent.OnDismissClick(clock.now))
        mainLooper.idle()

        assertThat(alarmRepository.disabled).containsExactly(7L)
    }

    @Test
    fun `dismissing a repeating alarm leaves it on`() {
        store.submit(ringRequest())
        mainLooper.idle()

        store.submit(RingEvent.OnDismissClick(clock.now))
        mainLooper.idle()

        assertThat(alarmRepository.disabled).isEmpty()
    }

    @Test
    fun `the auto dismiss timer fires and dismisses the ring`() {
        store.submit(ringRequest(autoDismiss = 60.seconds))
        mainLooper.idle()
        assertThat(host.started).isEqualTo(1)

        mainLooper.idleFor(java.time.Duration.ofSeconds(60))

        assertThat(store.state.value).isEqualTo(RingState.Idle)
        assertThat(host.stopped).isEqualTo(1)
    }

    @Test
    fun `stopping the auto dismiss timer cancels it`() {
        store.submit(ringRequest(autoDismiss = 60.seconds))
        mainLooper.idle()

        store.submit(RingEvent.OnSnoozeClick(clock.now)) // snooze stops the auto-dismiss timer
        mainLooper.idleFor(java.time.Duration.ofSeconds(120))

        // The auto-dismiss deadline (+60 s) passed while snoozing — had the timer survived the
        // snooze, the session would be dismissed instead of still snoozing.
        val state = store.state.value
        assertThat(state).isInstanceOf(RingState.Ringing::class.java)
        assertThat((state as RingState.Ringing).stage)
            .isInstanceOf(
                dev.tomng.alarme.core.alarmengine.statemachine.RingStage.Snoozing::class.java,
            )
    }

    @Test
    fun `a snooze stops the audio and arms a durable finish`() {
        store.submit(ringRequest())
        mainLooper.idle()
        assertThat(host.started).isEqualTo(1)

        store.submit(RingEvent.OnSnoozeClick(clock.now))
        mainLooper.idle()

        assertThat(host.stopped).isEqualTo(1)
        val snoozing = store.state.value as RingState.Ringing
        val finishAt =
            (snoozing.stage as dev.tomng.alarme.core.alarmengine.statemachine.RingStage.Snoozing)
                .finishAt
        assertThat(scheduler.snoozes.single()).isEqualTo(Triple(7L, finishAt, 1))
        assertThat(snoozeStore.load()?.alarmId).isEqualTo(7L)
        assertThat(scheduler.reasons)
            .contains(dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason.SNOOZE_ALARM)

        // The finish is the PI's job now: no in-session resurrection without a delivery.
        mainLooper.idleFor(java.time.Duration.ofSeconds(300))
        assertThat((store.state.value as RingState.Ringing).stage)
            .isInstanceOf(
                dev.tomng.alarme.core.alarmengine.statemachine.RingStage.Snoozing::class.java,
            )
    }

    @Test
    fun `a dismiss rearms the next occurrence and clears the snooze`() {
        snoozeStore.save(
            dev.tomng.alarme.core.alarmengine.scheduling.SnoozeRecord(7L, 1L, 1),
        )
        store.submit(ringRequest())
        mainLooper.idle()

        store.submit(RingEvent.OnDismissClick(clock.now))
        mainLooper.idle()

        assertThat(scheduler.reasons)
            .contains(dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason.DISMISS)
        assertThat(snoozeStore.load()).isNull()
        assertThat(scheduler.snoozesCancelled).isEqualTo(1)
    }

    @Test
    fun `dismissing a snoozed mission alarm cancels the snooze and rings again`() {
        val missionAlarm = alarm.copy(missions = listOf(Mission.Math(timeLimitSeconds = 30)))
        executor.prime(
            RingRequestBuilder.build(missionAlarm, defaultSettings(), hasExternalOutput = false),
        )
        store.submit(ringRequest(alarm = missionAlarm))
        mainLooper.idle()
        store.submit(RingEvent.OnSnoozeClick(clock.now))
        mainLooper.idle()

        store.submit(RingEvent.OnDismissClick(clock.now))
        mainLooper.idle()

        assertThat((store.state.value as RingState.Ringing).stage)
            .isInstanceOf(
                dev.tomng.alarme.core.alarmengine.statemachine.RingStage.Mission::class.java,
            )
        assertThat(host.started).isEqualTo(2)
        assertThat(snoozeStore.load()).isNull()
        assertThat(scheduler.snoozesCancelled).isEqualTo(1)
        assertThat(alarmRepository.disabled).isEmpty()
    }

    @Test
    fun `resetting the snooze counters clears the durable snooze`() {
        snoozeStore.save(dev.tomng.alarme.core.alarmengine.scheduling.SnoozeRecord(7L, 1L, 3))

        executor.onEffect(RingEffect.ResetSnooze)
        mainLooper.idle()

        assertThat(snoozeStore.load()).isNull()
        assertThat(scheduler.snoozesCancelled).isEqualTo(1)
    }

    @Test
    fun `a reschedule effect rearms with its reason`() {
        executor.onEffect(
            RingEffect.Reschedule(
                dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
                    .RECOVER_DISABLED_ALARM,
            ),
        )
        mainLooper.idle()

        assertThat(scheduler.reasons)
            .contains(
                dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
                    .RECOVER_DISABLED_ALARM,
            )
    }

    private fun ringRequest(
        autoDismiss: kotlin.time.Duration? = null,
        alarm: Alarm = this.alarm,
    ): RingEvent.AlarmRingRequest =
        RingEvent.AlarmRingRequest(
            alarm = alarm,
            scheduleId = scheduleId,
            trigger = RingTrigger.SCHEDULED,
            ringEventId = 11L,
            startedAt = clock.now,
            autoDismiss = autoDismiss,
        )

    @Test
    fun `the mission timer ticks each second and fails back to the ringing alarm on timeout`() {
        val mission = Mission.Math(timeLimitSeconds = 3, rounds = 1)
        val missionAlarm = alarm.copy(emoji = "", missions = listOf(mission))
        executor.prime(
            RingRequestBuilder.build(missionAlarm, defaultSettings(), hasExternalOutput = false),
        )
        store.submit(
            ringRequest().copy(alarm = missionAlarm, ringEventId = 12L),
        )
        mainLooper.idle()

        store.submit(RingEvent.OnDismissClick(clock.now)) // starts mission 0
        mainLooper.idle()
        val missionStage =
            store.state.value.let { (it as RingState.Ringing).stage } as RingStage.Mission
        assertThat(missionStage.timer.remaining).isEqualTo(3.seconds)
        assertThat(host.stopped).isEqualTo(1) // silenced while solving

        mainLooper.idleFor(java.time.Duration.ofSeconds(3))

        assertThat(store.state.value.let { (it as RingState.Ringing).stage })
            .isEqualTo(RingStage.Ring)
        assertThat(host.started).isEqualTo(2) // the ring, then the resume after the failure
        assertThat(host.lastRequest?.crescendo).isNull()
    }

    @Test
    fun `missions keep the ring sounding when mute during mission is off`() {
        val mission = Mission.Typing(timeLimitSeconds = 10, rounds = 1)
        val missionAlarm = alarm.copy(emoji = "", missions = listOf(mission))
        store.submit(
            ringRequest().copy(alarm = missionAlarm, ringEventId = 16L, muteDuringMission = false),
        )
        mainLooper.idle()

        store.submit(RingEvent.OnDismissClick(clock.now))
        mainLooper.idle()

        assertThat(host.started).isEqualTo(1)
        assertThat(host.stopped).isEqualTo(0)
    }

    @Test
    fun `mission completion dismisses the ring once the chain is finished`() {
        val mission = Mission.Typing(timeLimitSeconds = 10, rounds = 1)
        val missionAlarm = alarm.copy(emoji = "", missions = listOf(mission))
        executor.prime(
            RingRequestBuilder.build(missionAlarm, defaultSettings(), hasExternalOutput = false),
        )
        store.submit(ringRequest().copy(alarm = missionAlarm, ringEventId = 13L))
        mainLooper.idle()

        store.submit(RingEvent.OnDismissClick(clock.now))
        mainLooper.idle()
        store.submit(RingEvent.OnMissionComplete(clock.now))
        mainLooper.idle()

        assertThat(store.state.value).isEqualTo(RingState.Idle)
        // The mission countdown must be gone: idling past its 10 s limit must not fire anything.
        mainLooper.idleFor(java.time.Duration.ofSeconds(12))
        assertThat(store.state.value).isEqualTo(RingState.Idle)
        assertThat(host.started).isEqualTo(1)
    }

    @Test
    fun `the emergency resumes the ring and dismisses after the tap goal`() {
        val mission = Mission.Memory(rounds = 1)
        val missionAlarm = alarm.copy(emoji = "", missions = listOf(mission))
        store.submit(ringRequest().copy(alarm = missionAlarm, ringEventId = 14L))
        mainLooper.idle()
        store.submit(RingEvent.OnDismissClick(clock.now))
        mainLooper.idle()

        store.submit(RingEvent.OnEmergencyClick(clock.now))
        mainLooper.idle()
        assertThat(host.started).isEqualTo(2)

        // No countdown runs during the emergency: idling long past the mission limit changes
        // nothing.
        mainLooper.idleFor(java.time.Duration.ofSeconds(120))
        assertThat(store.state.value.let { (it as RingState.Ringing).stage })
            .isInstanceOf(RingStage.Emergency::class.java)

        repeat(RingStateMachine.EMERGENCY_TAPS) {
            store.submit(RingEvent.OnEmergencyTap(clock.now))
        }
        mainLooper.idle()

        assertThat(store.state.value).isEqualTo(RingState.Idle)
    }

    // ---------------------------------------------------------- wake-up check

    @Test
    fun `scheduling the wake-up check arms the exact alarm`() {
        val at = clock.now + 5.seconds * 60
        executor.onEffect(RingEffect.ScheduleWakeUpCheck(alarm, at))

        mainLooper.idle()

        assertThat(scheduler.lastWakeUpCheck).isEqualTo(7L to at)
    }

    @Test
    fun `cancelling the wake-up check disarms the alarm and stops the check service`() {
        executor.onEffect(RingEffect.CancelWakeUpCheck)

        mainLooper.idle()

        assertThat(scheduler.wakeUpChecksCancelled).isEqualTo(1)
        assertThat(shadowOf(context).nextStoppedService?.component?.className)
            .isEqualTo("dev.tomng.alarme.core.alarmengine.service.WakeUpCheckService")
    }

    @Test
    fun `the check window expiry fails the check, disarms it and re-rings the alarm service`() {
        store.submit(
            RingEvent.WakeUpCheckRestored(alarm, checkAt = clock.now + 300.seconds),
        )
        mainLooper.idle() // arms the PI 100 alarm

        store.submit(RingEvent.WakeUpCheckStarted(alarm = alarm, startTime = clock.now))
        mainLooper.idle() // arms the 100 s window

        mainLooper.idleFor(java.time.Duration.ofSeconds(100))

        assertThat(store.state.value).isEqualTo(RingState.Idle)
        assertThat(scheduler.wakeUpChecksCancelled).isEqualTo(1)
        val started = shadowOf(context).nextStartedService
        assertThat(started.component?.className)
            .isEqualTo("dev.tomng.alarme.core.alarmengine.service.AlarmService")
        assertThat(started.getStringExtra(AlarmServiceIntents.EXTRA_START_MODE))
            .isEqualTo(AlarmServiceIntents.START_MODE_MANUAL)
        assertThat(
                started.getLongExtra(
                    dev.tomng.alarme.core.alarmengine.scheduling.AlarmIntents.EXTRA_ALARM_ID,
                    -1L,
                ),
            )
            .isEqualTo(7L)
    }

    @Test
    fun `a completed check stops the window so nothing fires later`() {
        store.submit(RingEvent.WakeUpCheckStarted(alarm = alarm, startTime = clock.now))
        mainLooper.idle()
        store.submit(RingEvent.OnWakeUpCheckComplete)
        mainLooper.idle()

        // Idling past the window must not resurrect the session with a failure.
        mainLooper.idleFor(java.time.Duration.ofSeconds(101))

        assertThat(store.state.value).isEqualTo(RingState.Idle)
        assertThat(shadowOf(context).nextStartedService).isNull()
    }

    private fun defaultSettings() = dev.tomng.alarme.core.data.settings.AppSettings()

    private class RecordingRingHost : RingHost {
        var started = 0
        var stopped = 0
        var lastRequest: RingRequest? = null

        override fun start(request: RingRequest) {
            started++
            lastRequest = request
        }

        override fun stop() {
            stopped++
        }
    }
}

/** Records the one-shot disable that a dismissal performs. */
private class RecordingAlarmRepository : dev.tomng.alarme.core.data.repository.AlarmRepository {
    val disabled = mutableListOf<Long>()

    override fun observeAlarms(): kotlinx.coroutines.flow.Flow<List<Alarm>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override fun observeNextAlarm(): kotlinx.coroutines.flow.Flow<Alarm?> =
        kotlinx.coroutines.flow.flowOf(null)

    override suspend fun getAlarm(id: Long): Alarm? = null

    override suspend fun upsertAlarm(alarm: Alarm): Long = alarm.id

    override suspend fun setSkipped(id: Long, skipped: Boolean) = Unit

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        if (!enabled) disabled += id
    }

    override suspend fun deleteAlarm(id: Long) = Unit
}
