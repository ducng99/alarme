package dev.tomng.alarme.core.alarmengine.poweroff

import javax.inject.Inject
import javax.inject.Singleton

/** Binds [BlockerChannel] to the live accessibility service, if the user granted it. */
@Singleton
class AccessibilityBlockerChannel @Inject constructor() : BlockerChannel {

    override fun isConnected(): Boolean = ShutdownBlockerService.connected() != null

    override fun block() {
        ShutdownBlockerService.connected()?.blockNow()
    }
}
