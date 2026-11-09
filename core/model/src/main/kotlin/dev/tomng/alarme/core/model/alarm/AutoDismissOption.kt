package dev.tomng.alarme.core.model.alarm

/**
 * Optional safety timer: a ringing alarm that nobody interacts with is dismissed automatically
 * after N minutes. `Off` (the default) means the alarm rings until handled.
 */
sealed interface AutoDismissOption {

    fun toMillis(): Long?

    data object Off : AutoDismissOption {
        override fun toMillis(): Long? = null
    }

    data class After(val minutes: Int) : AutoDismissOption {
        init {
            require(minutes in 1..MAX_MINUTES) { "auto-dismiss minutes must be in 1..$MAX_MINUTES" }
        }

        override fun toMillis(): Long = minutes * MILLIS_PER_MINUTE
    }

    companion object {
        const val MAX_MINUTES = 30
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
