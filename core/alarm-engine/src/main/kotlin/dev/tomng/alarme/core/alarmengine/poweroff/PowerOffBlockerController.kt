package dev.tomng.alarme.core.alarmengine.poweroff

import dev.tomng.alarme.core.data.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** The accessibility service as seen by the app: is it granted, and can it press keys? */
interface BlockerChannel {
    fun isConnected(): Boolean

    /** Starts the block sequence; the service decides whether there is anything to fight. */
    fun block()
}

/**
 * Turns a focus loss during a ring into a block attempt. The policy lives in [PowerOffBlocker];
 * this class only feeds it the current settings and the live service state, and keeps the
 * "successful blocks" counter honest.
 */
@Singleton
class PowerOffBlockerController
@Inject
constructor(
    private val settings: SettingsRepository,
    private val channel: BlockerChannel,
) {

    suspend fun onFocusLost(
        dismissed: Boolean,
        screenKeeperPaused: Boolean,
    ) {
        val current = settings.settings.first()
        val decision =
            PowerOffBlocker.decide(
                BlockerInputs(
                    enabled = current.preventPowerOff,
                    dismissed = dismissed,
                    screenKeeperPaused = screenKeeperPaused,
                    windowFocused = false,
                ),
            )
        if (decision != BlockerDecision.Block) return
        if (!channel.isConnected()) return
        channel.block()
    }
}
