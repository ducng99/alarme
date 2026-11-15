package dev.tomng.alarme.feature.ring.takeover

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * The system-dialog closer: while the ring is up it repeatedly broadcasts
 * `ACTION_CLOSE_SYSTEM_DIALOGS` so a notification shade or power dialog cannot stay open — on
 * builds where that broadcast is restricted (31+) the component stays a no-op.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SystemDialogCloserTest {

    private val app: android.content.ContextWrapper = ApplicationProvider.getApplicationContext()
    private val looper: ShadowLooper = shadowOf(Looper.getMainLooper())

    @Suppress("DEPRECATION")
    private fun closeBroadcastCount(): Int =
        shadowOf(app).broadcastIntents.count { it.action == Intent.ACTION_CLOSE_SYSTEM_DIALOGS }

    @Test
    fun `when allowed it spams the close broadcast at most 200 ms apart`() {
        val closer =
            SystemDialogCloser(app, Handler(Looper.getMainLooper()), broadcastAllowed = true)

        closer.start()
        looper.idleFor(java.time.Duration.ofMillis(700))
        closer.stop()

        assertThat(closeBroadcastCount()).isAtLeast(3)
    }

    @Test
    fun `stopping halts the broadcast loop`() {
        val closer =
            SystemDialogCloser(app, Handler(Looper.getMainLooper()), broadcastAllowed = true)

        closer.start()
        looper.idleFor(java.time.Duration.ofMillis(400))
        closer.stop()
        val baseline = closeBroadcastCount()
        looper.idleFor(java.time.Duration.ofSeconds(5))

        assertThat(closeBroadcastCount()).isEqualTo(baseline)
    }

    @Test
    fun `when the broadcast is restricted it never sends`() {
        val closer =
            SystemDialogCloser(app, Handler(Looper.getMainLooper()), broadcastAllowed = false)

        closer.start()
        looper.idleFor(java.time.Duration.ofSeconds(2))
        closer.stop()

        assertThat(closeBroadcastCount()).isEqualTo(0)
    }
}
