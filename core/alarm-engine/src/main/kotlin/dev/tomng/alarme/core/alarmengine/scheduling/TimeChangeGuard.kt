package dev.tomng.alarme.core.alarmengine.scheduling

import dev.tomng.alarme.core.common.time.AppClock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * TIME_SET and TIMEZONE_CHANGED usually arrive as a pair; the guard swallows everything inside
 * [WINDOW] so the app reschedules once, not per broadcast.
 */
class TimeChangeGuard(private val clock: AppClock) {

    private var lastActedAt: Instant? = null

    @Synchronized
    fun shouldAct(): Boolean {
        val now = clock.now()
        val last = lastActedAt
        if (last != null && now - last < WINDOW) return false
        lastActedAt = now
        return true
    }

    @Synchronized
    fun reset() {
        lastActedAt = null
    }

    companion object {
        val WINDOW: Duration = 60.seconds
    }
}
