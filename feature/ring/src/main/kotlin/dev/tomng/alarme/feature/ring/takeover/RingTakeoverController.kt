package dev.tomng.alarme.feature.ring.takeover

import android.app.Activity
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dev.tomng.alarme.core.alarmengine.statemachine.RingStage
import dev.tomng.alarme.core.alarmengine.statemachine.RingState

/** Hides and reveals the system bars of one window. */
interface SystemBarsHider {
    fun hide()

    fun show()
}

/**
 * The production bar hider: immersive-sticky via the insets controller —
 * `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` makes revealed bars transient, so a swipe reveals them
 * briefly and they collapse again by themselves.
 */
class InsetsSystemBarsHider(window: Window) : SystemBarsHider {

    private val controller: WindowInsetsControllerCompat =
        WindowCompat.getInsetsController(window, window.decorView)

    init {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun hide() {
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun show() {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * Owns the window flags that turn a normal activity into a ring takeover: wake the display, draw
 * over the lock screen, keep the screen on, and hide the system bars. [release] gives everything
 * back so no flag outlives the ring.
 */
class RingTakeoverController(private val bars: SystemBarsHider) {

    fun takeOver(activity: Activity) {
        activity.setShowWhenLocked(true)
        activity.setTurnScreenOn(true)
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        bars.hide()
    }

    /** While snoozed the screen may dim and sleep like any other app; it is held on otherwise. */
    fun holdScreenOn(activity: Activity, hold: Boolean) {
        if (hold) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    /** Re-applies immersive mode when the window is resumed. */
    fun keepImmersive() {
        bars.hide()
    }

    fun release(activity: Activity) {
        activity.setShowWhenLocked(false)
        activity.setTurnScreenOn(false)
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        bars.show()
    }

    companion object {
        fun holdsScreenOn(state: RingState): Boolean =
            when (state) {
                is RingState.Ringing -> state.stage !is RingStage.Snoozing
                is RingState.WakeUpCheck -> true
                else -> false
            }
    }
}
