package dev.tomng.alarme.core.model.alarm

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/**
 * Wake-up check: N minutes after a dismissal the alarm silently re-rings and gives the user 100
 * seconds to prove they are actually up ("I'm up"); a timeout re-rings the alarm. `minutes == 0`
 * disables the feature.
 */
@Serializable
data class WakeUpCheck(val minutes: Int = 0) {
    init {
        require(minutes in 0..MAX_MINUTES) { "wake-up check minutes must be in 0..$MAX_MINUTES" }
    }

    val isEnabled: Boolean
        get() = minutes > 0

    fun triggerAfter(dismissedAt: Instant): Instant = dismissedAt + minutes.minutes

    companion object {
        const val MAX_MINUTES = 1440
        const val COUNTDOWN_SECONDS = 100L
    }
}
