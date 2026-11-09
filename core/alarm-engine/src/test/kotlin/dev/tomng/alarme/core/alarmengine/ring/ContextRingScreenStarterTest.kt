package dev.tomng.alarme.core.alarmengine.ring

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContextRingScreenStarterTest {

    private val application = RuntimeEnvironment.getApplication()

    private val starter =
        ContextRingScreenStarter(application) {
            RingIntents.openRingIntent(application.packageName)
        }

    @Test
    fun `sends the ring screen to the front through a pending intent`() {
        starter.start()

        val started = shadowOf(application).nextStartedActivity

        assertThat(started).isNotNull()
        assertThat(started.action).isEqualTo(RingIntents.ACTION_OPEN_RING)
        assertThat(started.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun `an existing ring screen is brought forward instead of re-created`() {
        starter.start()

        val started = shadowOf(application).nextStartedActivity

        assertThat(started.flags and android.content.Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            .isNotEqualTo(0)
    }

    @Test
    fun `starting again reuses the same pending intent`() {
        starter.start()
        starter.start()

        assertThat(shadowOf(application).nextStartedActivity.action)
            .isEqualTo(RingIntents.ACTION_OPEN_RING)
    }
}
