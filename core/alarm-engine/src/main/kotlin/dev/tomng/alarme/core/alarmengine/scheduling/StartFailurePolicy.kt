package dev.tomng.alarme.core.alarmengine.scheduling

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * When starting the ring FGS from the receiver is denied, retry exactly once — but only for a fresh
 * delivery (FGS start failure + not already retried + alarm time ≤ 55 s in the past → re-arm at
 * now + 3 s).
 */
object StartFailurePolicy {

    fun shouldRetry(now: Instant, scheduledAt: Instant, isRetry: Boolean): Boolean {
        if (isRetry) return false
        return now - scheduledAt <= MAX_LATENESS
    }

    val RETRY_DELAY: Duration = 3.seconds
    val MAX_LATENESS: Duration = 55.seconds
}
