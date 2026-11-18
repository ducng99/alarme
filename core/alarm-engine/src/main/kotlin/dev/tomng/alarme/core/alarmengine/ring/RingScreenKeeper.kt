package dev.tomng.alarme.core.alarmengine.ring

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomng.alarme.core.alarmengine.statemachine.RingSessionStore
import dev.tomng.alarme.core.alarmengine.statemachine.RingStage
import dev.tomng.alarme.core.alarmengine.statemachine.RingState
import dev.tomng.alarme.core.common.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Whether any display can show the ring screen; launching while every display is off would fail.
 */
fun interface DisplayProbe {
    fun anyDisplayUsable(): Boolean
}

class AndroidDisplayProbe @Inject constructor(@ApplicationContext context: Context) : DisplayProbe {

    private val displayManager = context.getSystemService(DisplayManager::class.java)

    override fun anyDisplayUsable(): Boolean {
        val displays = displayManager?.displays ?: return false
        return displays.any { display -> display.state !in UNSAFE_STATES }
    }

    private companion object {
        val UNSAFE_STATES = setOf(Display.STATE_OFF, Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND)
    }
}

/**
 * Keeps the ring screen in front for as long as the session needs it (ringing or the wake-up
 * check). It follows the session store rather than the ring activity, so swiping the task away or
 * opening another app cannot end the mission: the screen is pulled back every second until the
 * alarm is dismissed, snoozed or finished through the emergency path. While snoozed the phone is
 * free to use; the pull back resumes when the snooze re-rings.
 */
@Singleton
class RingScreenKeeper
@Inject
constructor(
    private val sessionStore: RingSessionStore,
    private val starter: RingScreenStarter,
    private val displays: DisplayProbe,
    @ApplicationScope private val scope: CoroutineScope,
) {
    @Volatile private var screenInForeground = false
    @Volatile private var graceRemainingMillis = 0L
    @Volatile private var running = false
    private var watchJob: Job? = null

    val isRunning: Boolean
        get() = running

    @Synchronized
    fun watch() {
        if (watchJob != null) return
        watchJob = scope.launch {
            sessionStore.state
                .map { it.needsRingScreen() }
                .distinctUntilChanged()
                .collectLatest { needed ->
                    running = needed
                    if (needed) keepInFront()
                }
        }
    }

    fun onScreenShown() {
        screenInForeground = true
    }

    /** Something covers the ring screen (or it is gone): pull it back on the next tick. */
    fun onScreenHidden() {
        screenInForeground = false
        graceRemainingMillis = 0L
    }

    private suspend fun keepInFront() {
        // The service has just asked for the screen; give that launch a moment before repeating it.
        graceRemainingMillis = ENTRY_GRACE_MILLIS
        while (true) {
            delay(PERIOD_MILLIS)
            graceRemainingMillis = (graceRemainingMillis - PERIOD_MILLIS).coerceAtLeast(0L)
            if (shouldPullBack()) starter.start()
        }
    }

    private fun shouldPullBack(): Boolean {
        if (graceRemainingMillis > 0L || screenInForeground) return false
        return sessionStore.state.value.needsRingScreen() && displays.anyDisplayUsable()
    }

    private fun RingState.needsRingScreen(): Boolean =
        when (this) {
            is RingState.Ringing -> stage !is RingStage.Snoozing
            is RingState.WakeUpCheck -> true
            else -> false
        }

    companion object {
        const val ENTRY_GRACE_MILLIS = 3_000L
        const val PERIOD_MILLIS = 1_000L
    }
}
