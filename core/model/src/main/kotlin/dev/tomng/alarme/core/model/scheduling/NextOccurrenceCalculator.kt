package dev.tomng.alarme.core.model.scheduling

import dev.tomng.alarme.core.model.alarm.Alarm
import kotlin.time.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Computes the next wall-clock occurrence of an alarm.
 *
 * Semantics (mirroring the reference behaviour):
 * - one-shot alarms fire at the next wall-clock occurrence of [Alarm.hour]:[Alarm.minute];
 * - repeating alarms only fire on enabled days ([Alarm.daysOfWeek]);
 * - skipping drops exactly the first occurrence after the skip was requested ([Alarm.updatedAt]);
 *   once that occurrence is in the past the skip is spent and no longer affects the schedule
 *   (repeating alarms only — a skipped one-shot has no occurrence at all);
 * - disabled alarms never occur;
 * - DST gaps shift a nonexistent wall time forward, overlaps resolve to the earlier instant
 *   (platform tzdb semantics via `LocalDateTime.toInstant`).
 */
object NextOccurrenceCalculator {

    /** A repeating alarm fires at most weekly, so 14 days of search is a safe upper bound. */
    private const val SEARCH_DAYS = 14

    fun nextOccurrence(alarm: Alarm, now: Instant, zone: TimeZone): Instant? {
        if (!alarm.enabled) return null
        if (alarm.isSkipped && !alarm.daysOfWeek.isRepeating) return null

        val candidates = candidateOccurrences(alarm, now, zone)
        if (!alarm.isSkipped) return candidates.firstOrNull()
        val skipped = candidateOccurrences(alarm, alarm.updatedAt, zone).firstOrNull()
        return candidates.firstOrNull { it != skipped }
    }

    /** All occurrences strictly after [now] within the search window, in order. */
    private fun candidateOccurrences(
        alarm: Alarm,
        now: Instant,
        zone: TimeZone,
    ): Sequence<Instant> = sequence {
        val today = now.toLocalDateTime(zone).date
        val target = LocalTime(alarm.hour, alarm.minute)
        for (offset in 0..SEARCH_DAYS) {
            val date = today.plus(offset, DateTimeUnit.DAY)
            val dayEnabled = !alarm.daysOfWeek.isRepeating || date.dayOfWeek in alarm.daysOfWeek
            if (dayEnabled) {
                val candidate = LocalDateTime(date, target).toInstant(zone)
                if (candidate > now) yield(candidate)
            }
        }
    }
}
