package dev.tomng.alarme.core.model.scheduling

import dev.tomng.alarme.core.model.alarm.SnoozeOption
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Result of a snooze attempt: either granted (with the new finish time) or denied with a reason.
 */
sealed interface SnoozeResult {

    data class Allowed(val finishAt: Instant, val nextCount: Int) : SnoozeResult

    sealed interface Denied : SnoozeResult {
        data object SnoozeDisabled : Denied

        data object SnoozeLimitReached : Denied
    }
}

/**
 * Snooze arithmetic: finish time is `now + duration` and the persisted count increments per snooze
 * until the configured limit is reached (mirrors the reference persistence keys).
 */
object SnoozeCalculator {

    fun snooze(option: SnoozeOption, now: Instant, countSoFar: Int): SnoozeResult {
        if (!option.enabled) return SnoozeResult.Denied.SnoozeDisabled
        if (option.limit != SnoozeOption.UNLIMITED && countSoFar >= option.limit) {
            return SnoozeResult.Denied.SnoozeLimitReached
        }
        return SnoozeResult.Allowed(
            finishAt = now + option.minutes.minutes,
            nextCount = countSoFar + 1,
        )
    }
}
