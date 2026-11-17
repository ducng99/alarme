package dev.tomng.alarme.core.alarmengine.ring

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.statemachine.RingEvent
import dev.tomng.alarme.core.alarmengine.statemachine.RingSessionStore
import dev.tomng.alarme.core.alarmengine.statemachine.RingTrigger
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.scheduling.ScheduleId
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * The ring screen keeper lives with the ring session, not with the ring activity: whenever the ring
 * screen is not in the foreground while the alarm rings (or the wake-up check runs), it pulls the
 * screen back every second, even after the activity was destroyed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RingScreenKeeperTest {

    private val now = Instant.parse("2026-11-17T06:00:00Z")
    private val store = RingSessionStore()
    private val starter = RecordingRingScreenStarter()
    private var displaysUsable = true

    private val alarm =
        Alarm(id = 7L, name = "Work", hour = 6, minute = 0, createdAt = now, updatedAt = now)

    private fun ring() =
        store.submit(
            RingEvent.AlarmRingRequest(
                alarm = alarm,
                scheduleId =
                    ScheduleId(
                        deviceId = "d",
                        alarmId = 7L,
                        alarmTime = "20261117_0600",
                        nowTime = "20261116_2200",
                    ),
                trigger = RingTrigger.SCHEDULED,
                ringEventId = 11L,
                startedAt = now,
            ),
        )

    private fun keeperTest(block: suspend TestScope.(RingScreenKeeper) -> Unit) = runTest {
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
        val keeper =
            RingScreenKeeper(
                sessionStore = store,
                starter = starter,
                displays = DisplayProbe { displaysUsable },
                scope = scope,
            )
        keeper.watch()
        runCurrent()
        try {
            block(keeper)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `nothing happens while no alarm rings`() = keeperTest { keeper ->
        advanceTimeBy(60_000)

        assertThat(starter.starts).isEqualTo(0)
        assertThat(keeper.isRunning).isFalse()
    }

    @Test
    fun `the launch in flight gets a grace window before the first pull back`() = keeperTest {
        ring()
        advanceTimeBy(RingScreenKeeper.ENTRY_GRACE_MILLIS - 100)

        assertThat(starter.starts).isEqualTo(0)

        advanceTimeBy(RingScreenKeeper.PERIOD_MILLIS + 200)

        assertThat(starter.starts).isAtLeast(1)
    }

    @Test
    fun `the foreground ring screen is never re-launched`() = keeperTest { keeper ->
        ring()
        keeper.onScreenShown()

        advanceTimeBy(60_000)

        assertThat(starter.starts).isEqualTo(0)
        assertThat(keeper.isRunning).isTrue()
    }

    @Test
    fun `leaving the ring screen pulls it back every second`() = keeperTest { keeper ->
        ring()
        keeper.onScreenShown()
        advanceTimeBy(10_000)

        keeper.onScreenHidden()
        advanceTimeBy(3_100)

        assertThat(starter.starts).isAtLeast(3)
    }

    @Test
    fun `a destroyed ring activity is still pulled back`() = keeperTest { keeper ->
        ring()
        keeper.onScreenShown()
        // Swiping the task away pauses, then destroys, the activity; nothing else reports in.
        keeper.onScreenHidden()

        advanceTimeBy(5_100)

        assertThat(starter.starts).isAtLeast(3)
    }

    @Test
    fun `no pull back while every display is off`() = keeperTest { keeper ->
        ring()
        keeper.onScreenHidden()
        displaysUsable = false

        advanceTimeBy(30_000)

        assertThat(starter.starts).isEqualTo(0)
    }

    @Test
    fun `the loop stops once the alarm is dismissed`() = keeperTest { keeper ->
        ring()
        keeper.onScreenHidden()
        advanceTimeBy(5_000)
        val before = starter.starts

        store.submit(RingEvent.OnDismissClick(now))
        advanceTimeBy(60_000)

        assertThat(starter.starts).isEqualTo(before)
        assertThat(keeper.isRunning).isFalse()
    }

    @Test
    fun `the wake-up check is pulled back too`() = keeperTest { keeper ->
        store.submit(RingEvent.WakeUpCheckStarted(alarm = alarm, startTime = now))
        keeper.onScreenHidden()

        advanceTimeBy(5_000)

        assertThat(starter.starts).isAtLeast(1)
        assertThat(keeper.isRunning).isTrue()
    }

    @Test
    fun `watching twice does not double the pull backs`() = keeperTest { keeper ->
        keeper.watch()
        ring()
        keeper.onScreenHidden()

        advanceTimeBy(
            RingScreenKeeper.ENTRY_GRACE_MILLIS + 4 * RingScreenKeeper.PERIOD_MILLIS + 100,
        )

        assertThat(starter.starts).isEqualTo(5)
    }
}
