package dev.tomng.alarme.core.alarmengine.service

import android.app.Notification
import android.app.Service
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.FakeAlarmRepository
import dev.tomng.alarme.core.alarmengine.notifications.AlarmNotifications
import dev.tomng.alarme.core.alarmengine.power.Wakelocks
import dev.tomng.alarme.core.alarmengine.ring.CheckVibrator
import dev.tomng.alarme.core.alarmengine.statemachine.RingSessionStore
import dev.tomng.alarme.core.alarmengine.statemachine.RingState
import dev.tomng.alarme.core.common.time.FakeAppClock
import dev.tomng.alarme.core.model.alarm.Alarm
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

/**
 * The wake-up check host: the quiet second alarm. It must show its own notification (1002), vibrate
 * instead of playing audio, feed the check into the session, and stop itself for alarms that no
 * longer deserve a check (deleted, disabled, skipped).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WakeUpCheckServiceControllerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-11-04T06:05:00Z")
    private val clock = FakeAppClock(now)
    private val alarmRepo = FakeAlarmRepository()
    private val store = RingSessionStore()
    private val vibrator = RecordingVibrator()
    private lateinit var service: Service

    @Before
    fun setUp() {
        alarmRepo.alarms.value = listOf(alarm())
        service = Robolectric.buildService(DummyService::class.java).get()
    }

    private fun alarm(enabled: Boolean = true, skipped: Boolean = false) =
        Alarm(
            id = 2,
            name = "work",
            hour = 6,
            minute = 0,
            enabled = enabled,
            isSkipped = skipped,
            createdAt = Instant.parse("2026-11-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-11-01T00:00:00Z"),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun TestScope.newController() =
        WakeUpCheckServiceController(
            alarmRepository = alarmRepo,
            notifications =
                AlarmNotifications(context) {
                    android.content.Intent("dev.tomng.alarme.action.OPEN_RING")
                },
            wakelocks = Wakelocks(context),
            vibrator = vibrator,
            clock = clock,
            sessionStore = store,
            scope =
                kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()),
        )

    @Test
    fun `the check shows its own alarm-category notification and starts the window`() = runTest {
        val controller = newController()

        controller.onStartCommand(service, alarmId = 2)

        assertThat(shadowOf(service).lastForegroundNotificationId).isEqualTo(1002)
        assertThat(shadowOf(service).lastForegroundNotification!!.category)
            .isEqualTo(Notification.CATEGORY_ALARM)
        assertThat(vibrator.started).isEqualTo(1)
        assertThat(store.state.value).isEqualTo(RingState.WakeUpCheck(alarm(), startTime = now))
        assertThat(ShadowPowerManager.getLatestWakeLock()!!.isHeld).isTrue()

        controller.onDestroy()
    }

    @Test
    fun `a deleted alarm stops the check instead of nagging`() = runTest {
        alarmRepo.alarms.value = emptyList()
        val controller = newController()

        controller.onStartCommand(service, alarmId = 2)

        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
        assertThat(vibrator.started).isEqualTo(0)
    }

    @Test
    fun `a disabled alarm stops the check`() = runTest {
        alarmRepo.alarms.value = listOf(alarm(enabled = false))
        val controller = newController()

        controller.onStartCommand(service, alarmId = 2)

        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }

    @Test
    fun `a skipped alarm stops the check`() = runTest {
        alarmRepo.alarms.value = listOf(alarm(skipped = true))
        val controller = newController()

        controller.onStartCommand(service, alarmId = 2)

        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }

    @Test
    fun `destroy cancels the vibration and releases the wakelock`() = runTest {
        val controller = newController()
        controller.onStartCommand(service, alarmId = 2)

        controller.onDestroy()

        assertThat(vibrator.stopped).isEqualTo(1)
        assertThat(ShadowPowerManager.getLatestWakeLock()!!.isHeld).isFalse()
    }

    class DummyService : Service() {
        override fun onBind(intent: android.content.Intent?) = null
    }

    private class RecordingVibrator : CheckVibrator {
        var started = 0
        var stopped = 0

        override fun start() {
            started++
        }

        override fun stop() {
            stopped++
        }
    }
}
