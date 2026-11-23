package dev.tomng.alarme.core.alarmengine.ring

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.FakeAlarmRepository
import dev.tomng.alarme.core.alarmengine.FakeRingEventRepository
import dev.tomng.alarme.core.alarmengine.FakeSettingsRepository
import dev.tomng.alarme.core.alarmengine.notifications.AlarmNotifications
import dev.tomng.alarme.core.alarmengine.power.Wakelocks
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmIntents
import dev.tomng.alarme.core.alarmengine.scheduling.TriggerType
import dev.tomng.alarme.core.alarmengine.statemachine.RingSessionStore
import dev.tomng.alarme.core.alarmengine.statemachine.RingStage
import dev.tomng.alarme.core.alarmengine.statemachine.RingState
import dev.tomng.alarme.core.common.time.FakeAppClock
import dev.tomng.alarme.core.data.repository.AlarmEventType
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.mission.Mission
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmServiceControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-11-04T06:00:01Z")
    private val clock = FakeAppClock(now, TimeZone.UTC)
    private val alarmRepo = FakeAlarmRepository()
    private val settingsRepo = FakeSettingsRepository()
    private val events = FakeRingEventRepository()
    private val host = RecordingRingHost()
    private var resolvedRingtone: String? = null
    private val ringtoneResolver = RingtoneResolver { resolvedRingtone }
    private val store = RingSessionStore()
    private val snoozeStore = dev.tomng.alarme.core.alarmengine.InMemorySnoozeStore()
    private val screenStarter = RecordingRingScreenStarter()
    private val randomMissionStore = dev.tomng.alarme.core.alarmengine.InMemoryRandomMissionStore()
    private lateinit var service: Service

    @Before
    fun setUp() {
        alarmRepo.alarms.value =
            listOf(
                Alarm(
                    id = 2,
                    name = "work",
                    hour = 6,
                    minute = 0,
                    vibrate = true,
                    createdAt = Instant.parse("2026-11-01T00:00:00Z"),
                    updatedAt = Instant.parse("2026-11-01T00:00:00Z"),
                ),
            )
        service = Robolectric.buildService(DummyService::class.java).get()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.newController() =
        AlarmServiceController(
            alarmRepository = alarmRepo,
            settingsRepository = settingsRepo,
            ringEventRepository = events,
            notifications =
                AlarmNotifications(context) { Intent("dev.tomng.alarme.action.OPEN_RING") },
            wakelocks = Wakelocks(context),
            ringHost = host,
            audioOutputDetector = { false },
            ringtoneResolver = ringtoneResolver,
            snoozeStore = snoozeStore,
            clock = clock,
            ringScreenStarter = screenStarter,
            missionPicker = RingMissionPicker(randomMissionStore, kotlin.random.Random(1)),
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
            sessionStore = store,
            effectExecutor =
                RingEffectExecutor(
                    context,
                    host,
                    clock,
                    store,
                    dev.tomng.alarme.core.alarmengine.FakeAlarmScheduler(),
                    dev.tomng.alarme.core.alarmengine.InMemorySnoozeStore(),
                    AlarmNotifications(context) { Intent("dev.tomng.alarme.action.OPEN_RING") },
                    alarmRepo,
                    RingMissionPicker(randomMissionStore, kotlin.random.Random(1)),
                    CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
                ),
        )

    private fun ringIntent(scheduleId: String = "dev_2_0600_2310") =
        AlarmIntents.ringIntent(
            context,
            alarmId = 2,
            scheduleId = scheduleId,
            alarmTimeMillis = Instant.parse("2026-11-04T06:00:00Z").toEpochMilliseconds(),
            triggerType = TriggerType.NORMAL,
        )

    @Test
    fun `start posts foreground notification holds wakelock logs ring and starts the host`() =
        runTest {
            val controller = newController()

            val result = controller.onStartCommand(service, ringIntent(), 0, 1)

            assertThat(result).isEqualTo(Service.START_REDELIVER_INTENT)
            assertThat(shadowOf(service).lastForegroundNotificationId).isEqualTo(1001)
            assertThat(shadowOf(service).lastForegroundNotification).isNotNull()
            assertThat(shadowOf(service).lastForegroundNotification!!.category)
                .isEqualTo(Notification.CATEGORY_ALARM)

            val wakeLock = ShadowPowerManager.getLatestWakeLock()
            assertThat(wakeLock).isNotNull()
            assertThat(wakeLock!!.isHeld).isTrue()

            assertThat(events.events).hasSize(1)
            assertThat(events.events.single().event).isEqualTo(AlarmEventType.RING)
            assertThat(events.events.single().scheduleId).isEqualTo("dev_2_0600_2310")
            assertThat(events.events.single().alarmName).isEqualTo("work")

            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertThat(host.started).hasSize(1)
            assertThat(host.started.single().vibrate).isTrue()
            assertThat(host.started.single().useAlarmStream).isTrue()

            controller.onDestroy()
        }

    @Test
    fun `the mute during mission setting reaches the session`() = runTest {
        settingsRepo.settings.value = settingsRepo.settings.value.copy(muteDuringMission = false)
        val controller = newController()

        controller.onStartCommand(service, ringIntent(), 0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat((store.state.value as RingState.Ringing).muteDuringMission).isFalse()
        controller.onDestroy()
    }

    @Test
    fun `a ring asks for the ring screen directly`() = runTest {
        val controller = newController()

        controller.onStartCommand(service, ringIntent(), 0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(screenStarter.starts).isEqualTo(1)
        controller.onDestroy()
    }

    @Test
    fun `a disabled alarm never opens the ring screen`() = runTest {
        val controller = newController()
        alarmRepo.alarms.value = alarmRepo.alarms.value.map { it.copy(enabled = false) }

        controller.onStartCommand(service, ringIntent(), 0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(screenStarter.starts).isEqualTo(0)
        controller.onDestroy()
    }

    @Test
    fun `the resolver choice becomes the ring's audio`() = runTest {
        val controller = newController()
        resolvedRingtone = dev.tomng.alarme.core.model.ringtone.RingtoneUris.SILENT

        controller.onStartCommand(service, ringIntent(), 0, 1)

        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertThat(host.started.single().audioUri).isNull()

        controller.onDestroy()
    }

    @Test
    fun `ring start feeds the session store and a dismiss returns it to idle`() = runTest {
        val controller = newController()

        controller.onStartCommand(service, ringIntent(), 0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val ringing = store.state.value
        assertThat(ringing).isInstanceOf(RingState.Ringing::class.java)
        ringing as RingState.Ringing
        assertThat(ringing.alarm.id).isEqualTo(2L)
        assertThat(ringing.stage).isEqualTo(RingStage.Ring)
        assertThat(ringing.autoDismiss).isNull()
        assertThat(ringing.muteDuringMission).isTrue()

        store.submit(
            dev.tomng.alarme.core.alarmengine.statemachine.RingEvent.OnDismissClick(clock.now),
        )
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(store.state.value).isEqualTo(RingState.Idle)
        assertThat(host.stopped).isTrue()

        controller.onDestroy()
    }

    @Test
    fun `a manual start rings the alarm without a delivery`() = runTest {
        val controller = newController()

        val result =
            controller.onStartCommand(
                service,
                dev.tomng.alarme.core.alarmengine.ring.AlarmServiceIntents.manualStartIntent(
                    context,
                    alarmId = 2,
                ),
                0,
                1,
            )
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(result).isEqualTo(Service.START_REDELIVER_INTENT)
        assertThat(shadowOf(service).lastForegroundNotificationId).isEqualTo(1001)
        assertThat(events.events.single().event).isEqualTo(AlarmEventType.RING)
        val ringing = store.state.value
        assertThat(ringing).isInstanceOf(RingState.Ringing::class.java)
        assertThat((ringing as RingState.Ringing).alarm.id).isEqualTo(2L)
        assertThat(host.started).hasSize(1)

        controller.onDestroy()
    }

    @Test
    fun `a manual start for a deleted alarm stops instead of ringing a ghost`() = runTest {
        alarmRepo.alarms.value = emptyList()
        val controller = newController()

        controller.onStartCommand(
            service,
            dev.tomng.alarme.core.alarmengine.ring.AlarmServiceIntents.manualStartIntent(
                context,
                alarmId = 2,
            ),
            0,
            1,
        )

        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
        assertThat(host.started).isEmpty()

        controller.onDestroy()
    }

    @Test
    fun `a ring consumes the pending skip once`() = runTest {
        alarmRepo.alarms.value = listOf(alarmRepo.alarms.value.single().copy(isSkipped = true))
        val controller = newController()

        controller.onStartCommand(service, ringIntent(), 0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(alarmRepo.alarms.value.single().isSkipped).isFalse()
        controller.onDestroy()
    }

    @Test
    fun `a ring clears the durable snooze record`() = runTest {
        snoozeStore.save(dev.tomng.alarme.core.alarmengine.scheduling.SnoozeRecord(2L, 1L, 1))
        val controller = newController()

        controller.onStartCommand(service, ringIntent(), 0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(snoozeStore.load()).isNull()
        controller.onDestroy()
    }

    @Test
    fun `a snooze delivery carries its count into the session`() = runTest {
        val controller = newController()
        val snoozeIntent =
            AlarmIntents.ringIntent(
                context,
                alarmId = 2,
                scheduleId = "dev_2_0600_2310",
                alarmTimeMillis = Instant.parse("2026-11-04T06:05:00Z").toEpochMilliseconds(),
                triggerType = TriggerType.SNOOZE,
                snoozeCount = 2,
            )

        controller.onStartCommand(service, snoozeIntent, 0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val ringing = store.state.value as RingState.Ringing
        assertThat(ringing.snooze.count).isEqualTo(2)
        assertThat(ringing.trigger)
            .isEqualTo(dev.tomng.alarme.core.alarmengine.statemachine.RingTrigger.SNOOZE)
        controller.onDestroy()
    }

    @Test
    fun `a random mission alarm rings with its picked missions and logs them`() = runTest {
        val chain = (1..5).map { Mission.Shake(count = it) }
        alarmRepo.alarms.value =
            listOf(alarmRepo.alarms.value.single().copy(missions = chain, randomMissionCount = 2))
        val controller = newController()

        controller.onStartCommand(service, ringIntent(), 0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        val picked = (store.state.value as RingState.Ringing).alarm.missions
        assertThat(picked).hasSize(2)
        assertThat(chain).containsAtLeastElementsIn(picked)
        val logged = events.events.single().missionsJson!!
        picked.forEach { assertThat(logged).contains("\"count\":${(it as Mission.Shake).count}") }
        controller.onDestroy()
    }

    @Test
    fun `a snooze delivery of the same occurrence keeps the picked missions`() = runTest {
        val chain = (1..5).map { Mission.Shake(count = it) }
        alarmRepo.alarms.value =
            listOf(alarmRepo.alarms.value.single().copy(missions = chain, randomMissionCount = 3))
        val controller = newController()
        controller.onStartCommand(service, ringIntent(), 0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val first = (store.state.value as RingState.Ringing).alarm.missions

        val snoozeIntent =
            AlarmIntents.ringIntent(
                context,
                alarmId = 2,
                scheduleId = "dev_2_0600_2310",
                alarmTimeMillis = Instant.parse("2026-11-04T06:05:00Z").toEpochMilliseconds(),
                triggerType = TriggerType.SNOOZE,
                snoozeCount = 1,
            )
        controller.onStartCommand(service, snoozeIntent, 0, 2)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat((store.state.value as RingState.Ringing).alarm.missions).isEqualTo(first)
        controller.onDestroy()
    }

    @Test
    fun `a disabled alarm stops instead of ringing`() = runTest {
        alarmRepo.alarms.value = listOf(alarmRepo.alarms.value.single().copy(enabled = false))
        val controller = newController()

        controller.onStartCommand(service, ringIntent(), 0, 1)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
        assertThat(host.started).isEmpty()
        controller.onDestroy()
    }

    @Test
    fun `null intent stops the service without sticky redelivery`() = runTest {
        val controller = newController()

        val result = controller.onStartCommand(service, null, 0, 1)

        assertThat(result).isEqualTo(Service.START_NOT_STICKY)
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
        assertThat(host.started).isEmpty()
    }

    @Test
    fun `unparseable intent stops the service`() = runTest {
        val controller = newController()

        val result = controller.onStartCommand(service, Intent("bogus"), 0, 1)

        assertThat(result).isEqualTo(Service.START_NOT_STICKY)
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `wakelock renews after nine minutes`() = runTest {
        val controller = newController()
        controller.onStartCommand(service, ringIntent(), 0, 1)

        val wakeLock = ShadowPowerManager.getLatestWakeLock()!!
        val timesHeldBefore = shadowOf(wakeLock).timesHeld

        advanceTimeBy(9.minutes + 1.minutes)

        assertThat(shadowOf(wakeLock).timesHeld).isAtLeast(timesHeldBefore + 1)

        controller.onDestroy()
    }

    @Test
    fun `destroy releases the wakelock and stops the host`() = runTest {
        val controller = newController()
        controller.onStartCommand(service, ringIntent(), 0, 1)
        val wakeLock = ShadowPowerManager.getLatestWakeLock()!!

        controller.onDestroy()

        assertThat(wakeLock.isHeld).isFalse()
        assertThat(host.stopped).isTrue()
    }

    class DummyService : Service() {
        override fun onBind(intent: Intent?) = null
    }

    class RecordingRingHost : RingHost {
        val started = mutableListOf<RingRequest>()
        var stopped = false

        override fun start(request: RingRequest) {
            started += request
        }

        override fun stop() {
            stopped = true
        }
    }
}

/** Counts how often the ring screen was asked for. */
class RecordingRingScreenStarter : dev.tomng.alarme.core.alarmengine.ring.RingScreenStarter {
    var starts = 0

    override fun start() {
        starts++
    }
}
