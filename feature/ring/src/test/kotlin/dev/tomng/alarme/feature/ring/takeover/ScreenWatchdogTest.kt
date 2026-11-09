package dev.tomng.alarme.feature.ring.takeover

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * The screen watchdog: once the grace window after a (re)launch passes, it pulls the ring screen
 * back to the front every second — but only while the display is usable and the session is still
 * ringing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScreenWatchdogTest {

    private val looper: ShadowLooper = shadowOf(Looper.getMainLooper())
    private var ringing = true
    private var usable = true
    private var relaunches = 0
    private lateinit var watchdog: ScreenWatchdog

    @Before
    fun setUp() {
        watchdog =
            ScreenWatchdog(
                handler = Handler(Looper.getMainLooper()),
                clock = SystemClock::elapsedRealtime,
                isRinging = { ringing },
                displaysUsable = { usable },
                relaunch = { relaunches++ },
            )
    }

    @Test
    fun `no relaunch inside the grace window`() {
        watchdog.onActivityShown()
        watchdog.start()

        looper.idleFor(java.time.Duration.ofSeconds(9))

        assertThat(relaunches).isEqualTo(0)
    }

    @Test
    fun `a cheat attempt drops the grace window`() {
        watchdog.onActivityShown()
        watchdog.start()

        watchdog.onCheatDetected()
        watchdog.onActivityHidden()
        looper.idleFor(java.time.Duration.ofSeconds(2))

        assertThat(relaunches).isAtLeast(1)
    }

    @Test
    fun `the foreground ring screen is never re-launched`() {
        watchdog.onActivityShown()
        watchdog.start()

        looper.idleFor(java.time.Duration.ofSeconds(60))

        assertThat(relaunches).isEqualTo(0)
    }

    @Test
    fun `losing the foreground pulls the ring screen back`() {
        watchdog.onActivityShown()
        watchdog.start()

        watchdog.onActivityHidden()
        looper.idleFor(java.time.Duration.ofSeconds(2))

        assertThat(relaunches).isAtLeast(1)
    }

    @Test
    fun `relaunches roughly every second while the screen stays covered`() {
        watchdog.onActivityShown()
        watchdog.onActivityHidden()
        watchdog.start()

        looper.idleFor(java.time.Duration.ofSeconds(3))

        assertThat(relaunches).isAtLeast(2)
    }

    @Test
    fun `no relaunch while the display is off or dozing`() {
        watchdog.onActivityShown()
        watchdog.start()
        usable = false

        looper.idleFor(java.time.Duration.ofSeconds(30))

        assertThat(relaunches).isEqualTo(0)
    }

    @Test
    fun `the loop exits by itself when the session stops ringing`() {
        watchdog.onActivityShown()
        watchdog.start()
        ringing = false

        looper.idleFor(java.time.Duration.ofSeconds(60))

        assertThat(relaunches).isEqualTo(0)
        assertThat(watchdog.isRunning).isFalse()
    }

    @Test
    fun `stop cancels the loop`() {
        watchdog.onActivityShown()
        watchdog.start()
        watchdog.stop()

        looper.idleFor(java.time.Duration.ofSeconds(60))

        assertThat(relaunches).isEqualTo(0)
        assertThat(watchdog.isRunning).isFalse()
    }

    @Test
    fun `a relaunch activity re-arms the grace window`() {
        watchdog.onActivityShown()
        watchdog.onActivityHidden()
        watchdog.start()

        looper.idleFor(java.time.Duration.ofMillis(1_500))
        assertThat(relaunches).isAtLeast(1)

        // The relaunched activity resumes and extends the grace window.
        val relaunched = relaunches
        watchdog.onActivityShown()
        looper.idleFor(java.time.Duration.ofSeconds(5))

        assertThat(relaunches).isEqualTo(relaunched)
    }
}
