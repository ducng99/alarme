package dev.tomng.alarme.feature.ring.takeover

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.statemachine.MuteState
import dev.tomng.alarme.core.alarmengine.statemachine.RingStage
import dev.tomng.alarme.core.alarmengine.statemachine.RingState
import dev.tomng.alarme.core.alarmengine.statemachine.RingTrigger
import dev.tomng.alarme.core.alarmengine.statemachine.SnoozeState
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.scheduling.ScheduleId
import kotlin.time.Instant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The window takeover: wake the screen, draw over the lock, keep the screen on, hide the bars — and
 * give all of it back when the ring ends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingTakeoverControllerTest {

    private val bars = RecordingBars()

    @Test
    fun `taking over wakes the screen, draws over the lock and keeps it on`() {
        val controller = RingTakeoverController(bars)
        withActivity { activity ->
            controller.takeOver(activity)

            val flags = activity.window.attributes.flags
            assertThat(shadowOf(activity).showWhenLocked).isTrue()
            assertThat(shadowOf(activity).turnScreenOn).isTrue()
            assertThat(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON).isNotEqualTo(0)
        }
        assertThat(bars.hidden).isEqualTo(1)
        assertThat(bars.shown).isEqualTo(0)
    }

    @Test
    fun `re-entering immersive keeps the bars hidden`() {
        val controller = RingTakeoverController(bars)
        withActivity { activity ->
            controller.takeOver(activity)
            controller.keepImmersive()

            assertThat(bars.hidden).isEqualTo(2)
        }
    }

    @Test
    fun `letting the screen sleep clears keep-screen-on but stays over the lock`() {
        val controller = RingTakeoverController(bars)
        withActivity { activity ->
            controller.takeOver(activity)
            controller.holdScreenOn(activity, hold = false)

            val flags = activity.window.attributes.flags
            assertThat(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON).isEqualTo(0)
            assertThat(shadowOf(activity).showWhenLocked).isTrue()

            controller.holdScreenOn(activity, hold = true)

            val heldFlags = activity.window.attributes.flags
            assertThat(heldFlags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON).isNotEqualTo(0)
        }
    }

    @Test
    fun `the screen is held on while ringing and allowed to dim while snoozed`() {
        val now = Instant.parse("2026-11-10T06:00:00Z")
        val alarm =
            Alarm(id = 7L, name = "Work", hour = 6, minute = 0, createdAt = now, updatedAt = now)
        val ringing =
            RingState.Ringing(
                alarm = alarm,
                scheduleId = ScheduleId("d", 7L, "20261110_0600", "20261109_2200"),
                trigger = RingTrigger.SCHEDULED,
                ringEventId = 11L,
                startedAt = now,
                autoDismiss = null,
                mute = MuteState(count = 0, limit = 3, enabled = true),
                snooze = SnoozeState(count = 0, limit = alarm.snoozeOption.limit),
                stage = RingStage.Ring,
            )
        val snoozed = ringing.copy(stage = RingStage.Snoozing(finishAt = now))

        assertThat(RingTakeoverController.holdsScreenOn(ringing)).isTrue()
        assertThat(RingTakeoverController.holdsScreenOn(snoozed)).isFalse()
        assertThat(RingTakeoverController.holdsScreenOn(RingState.WakeUpCheck(alarm, now))).isTrue()
        assertThat(
                RingTakeoverController.holdsScreenOn(RingState.WakeUpCheckPending(alarm, now)),
            )
            .isFalse()
        assertThat(RingTakeoverController.holdsScreenOn(RingState.Idle)).isFalse()
    }

    @Test
    fun `releasing clears every flag and shows the bars`() {
        val controller = RingTakeoverController(bars)
        withActivity { activity ->
            controller.takeOver(activity)
            controller.release(activity)

            val flags = activity.window.attributes.flags
            assertThat(shadowOf(activity).showWhenLocked).isFalse()
            assertThat(shadowOf(activity).turnScreenOn).isFalse()
            assertThat(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON).isEqualTo(0)
        }
        assertThat(bars.shown).isEqualTo(1)
    }

    private fun withActivity(block: (ComponentActivity) -> Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                ApplicationProvider.getApplicationContext<android.app.Application>()
                block(activity)
            }
        }
    }

    private class RecordingBars : SystemBarsHider {
        var hidden = 0
        var shown = 0

        override fun hide() {
            hidden++
        }

        override fun show() {
            shown++
        }
    }
}
