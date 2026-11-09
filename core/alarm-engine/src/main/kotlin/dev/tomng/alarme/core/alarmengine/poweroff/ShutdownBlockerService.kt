package dev.tomng.alarme.core.alarmengine.poweroff

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The "prevent power-off" command channel. It deliberately processes no accessibility events: the
 * app only needs the service's ability to press global actions, and reading window content would be
 * both invasive and unnecessary. The service is granted by the user in system settings and is
 * entirely optional — without it the ring still works, it just cannot fight the power menu.
 */
@AndroidEntryPoint
class ShutdownBlockerService : AccessibilityService() {

    @Inject lateinit var counter: BlockCounterStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var running: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    /**
     * Presses the block sequence when another app is in front; a second call while one is running
     * is ignored, as is anything inside the refractory window right after a block — the sequence's
     * own HOME presses move the focus away from the ring screen, and that must not read as a new
     * cheat attempt.
     */
    fun blockNow() {
        if (running?.isActive == true) return
        val now = System.currentTimeMillis()
        if (now - lastBlockAt < REFACTORY_MILLIS) return
        val foreground = rootInActiveWindow?.packageName?.toString()
        if (!PowerOffBlocker.shouldBlockForeground(foreground, packageName)) return
        lastBlockAt = now
        running = scope.launch {
            for (step in PowerOffBlocker.blockSequence()) {
                performGlobalAction(
                    when (step.action) {
                        GlobalAction.BACK -> GLOBAL_ACTION_BACK
                        GlobalAction.HOME -> GLOBAL_ACTION_HOME
                    },
                )
                if (step.pauseAfterMillis > 0) delay(step.pauseAfterMillis)
            }
            counter.increment()
        }
    }

    companion object {
        /** One block per escape attempt; the sequence's own focus changes must not re-arm it. */
        private const val REFACTORY_MILLIS = 1_500L

        @Volatile private var instance: ShutdownBlockerService? = null

        @Volatile private var lastBlockAt = 0L

        /** The live service, or null when the user has not granted accessibility. */
        fun connected(): ShutdownBlockerService? = instance
    }
}
