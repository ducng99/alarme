package dev.tomng.alarme.feature.ring.takeover

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.view.Display

/**
 * Whether any display is in a usable (non-off, non-dozing) state — relaunching the ring screen
 * while every display is off would just be a background-activity-start failure.
 */
class DisplayUsabilityProbe(context: Context) : () -> Boolean {

    private val displayManager = context.getSystemService(DisplayManager::class.java)

    override fun invoke(): Boolean {
        val displays = displayManager?.displays ?: return false
        return displays.any { display -> display.state !in UNSAFE_STATES }
    }

    companion object {
        private val UNSAFE_STATES =
            setOf(Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND)
    }
}

/**
 * The screen watchdog: "you cannot navigate away". Whenever the ring screen loses the foreground,
 * it is pulled back on the next tick — but only while a display is usable and the session is still
 * ringing, in which case the loop exits by itself.
 */
class ScreenWatchdog(
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val clock: () -> Long,
    private val isRinging: () -> Boolean,
    private val displaysUsable: () -> Boolean,
    private val relaunch: () -> Unit,
) {
    private var graceUntil = 0L
    private var running = false
    private var foreground = false

    val isRunning: Boolean
        get() = running

    /** The ring screen became visible; extend the grace window so we never fight it. */
    fun onActivityShown() {
        foreground = true
        graceUntil = clock() + GRACE_PERIOD_MILLIS
    }

    /**
     * The ring screen went to the background, so something is covering it: pull it back on the very
     * next tick. While it stays in the foreground the loop leaves it alone instead of re-launching
     * the screen it is already showing.
     */
    fun onActivityHidden() {
        foreground = false
        graceUntil = 0L
    }

    /**
     * The user tried to leave the ring screen: drop the grace so the very next tick pulls the ring
     * back instead of waiting out the post-launch window.
     */
    fun onCheatDetected() {
        graceUntil = 0L
    }

    @Synchronized
    fun start() {
        if (running) return
        running = true
        handler.postDelayed(tick, PERIOD_MILLIS)
    }

    @Synchronized
    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    private val tick: Runnable = Runnable {
        val stillRinging = isRinging()
        if (!stillRinging) {
            stop()
            return@Runnable
        }
        if (!foreground && clock() >= graceUntil && displaysUsable()) {
            relaunch()
        }
        handler.postDelayed(tick, PERIOD_MILLIS)
    }

    companion object {
        /**
         * Grace right after a (re)launch so the watchdog never fights the screen it just started.
         */
        const val GRACE_PERIOD_MILLIS = 10_000L

        const val PERIOD_MILLIS = 1_000L
    }
}
