package dev.tomng.alarme.feature.ring.takeover

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
            controller.takeOver(activity.window)

            val flags = activity.window.attributes.flags
            assertThat(flags and WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED).isNotEqualTo(0)
            assertThat(flags and WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON).isNotEqualTo(0)
            assertThat(flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON).isNotEqualTo(0)
        }
        assertThat(bars.hidden).isEqualTo(1)
        assertThat(bars.shown).isEqualTo(0)
    }

    @Test
    fun `re-entering immersive keeps the bars hidden`() {
        val controller = RingTakeoverController(bars)
        withActivity { activity ->
            controller.takeOver(activity.window)
            controller.keepImmersive()

            assertThat(bars.hidden).isEqualTo(2)
        }
    }

    @Test
    fun `releasing clears every flag and shows the bars`() {
        val controller = RingTakeoverController(bars)
        withActivity { activity ->
            controller.takeOver(activity.window)
            controller.release(activity.window)

            val flags = activity.window.attributes.flags
            assertThat(flags and WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED).isEqualTo(0)
            assertThat(flags and WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON).isEqualTo(0)
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
