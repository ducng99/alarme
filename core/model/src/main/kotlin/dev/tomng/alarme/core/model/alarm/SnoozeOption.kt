package dev.tomng.alarme.core.model.alarm

import kotlinx.serialization.Serializable

/** Per-alarm snooze configuration. Defaults: enabled, 5 minutes, limit 3. */
@Serializable
data class SnoozeOption(
    val enabled: Boolean = true,
    val minutes: Int = DEFAULT_MINUTES,
    val limit: Int = DEFAULT_LIMIT,
) {
    init {
        require(minutes in MIN_MINUTES..MAX_MINUTES) {
            "snooze minutes must be in $MIN_MINUTES..$MAX_MINUTES"
        }
        require(limit == UNLIMITED || limit in 0..MAX_LIMIT) {
            "snooze limit must be UNLIMITED or 0..$MAX_LIMIT"
        }
    }

    companion object {
        const val DEFAULT_MINUTES = 5
        const val DEFAULT_LIMIT = 3
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 120
        const val MAX_LIMIT = 9

        /** Sentinel limit meaning "snooze as often as you like". */
        const val UNLIMITED = -1
    }
}
