package dev.tomng.alarme.core.model.scheduling

import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.AlarmType
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NextOccurrenceCalculatorTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    private fun at(iso: String, zone: TimeZone = berlin): Instant =
        LocalDateTime.parse(iso).toInstant(zone)

    private fun alarm(
        hour: Int,
        minute: Int,
        days: DaysOfWeek = DaysOfWeek.EVERY_DAY,
        enabled: Boolean = true,
        skipped: Boolean = false,
        type: AlarmType = AlarmType.NORMAL,
        updatedAt: Instant = Instant.parse("2026-11-01T00:00:00Z"),
    ): Alarm =
        Alarm(
            id = 1L,
            type = type,
            name = "test",
            hour = hour,
            minute = minute,
            daysOfWeek = days,
            enabled = enabled,
            isSkipped = skipped,
            createdAt = Instant.parse("2026-11-01T00:00:00Z"),
            updatedAt = updatedAt,
        )

    private fun localAt(instant: Instant, zone: TimeZone = berlin): LocalDateTime =
        instant.toLocalDateTime(zone)

    // --- one-shot alarms -------------------------------------------------

    @Test
    fun `one-shot after today's time returns tomorrow`() {
        val now = at("2026-11-03T10:00:00")
        val next = NextOccurrenceCalculator.nextOccurrence(alarm(6, 30), now, berlin)
        // 06:30 already passed -> tomorrow
        assertEquals(at("2026-11-04T06:30:00"), next)
    }

    @Test
    fun `one-shot before today's time returns today`() {
        val now = at("2026-11-03T05:00:00")
        val next = NextOccurrenceCalculator.nextOccurrence(alarm(6, 30), now, berlin)
        assertEquals(at("2026-11-03T06:30:00"), next)
    }

    @Test
    fun `disabled alarm has no occurrence`() {
        val now = at("2026-11-03T05:00:00")
        assertNull(
            NextOccurrenceCalculator.nextOccurrence(alarm(6, 30, enabled = false), now, berlin),
        )
    }

    // --- repeating alarms ------------------------------------------------

    @Test
    fun `repeating alarm fires today when today is enabled and time not passed`() {
        val now = at("2026-11-03T05:00:00") // Tuesday
        val mondays = DaysOfWeek.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY)
        val next = NextOccurrenceCalculator.nextOccurrence(alarm(9, 0, days = mondays), now, berlin)
        assertEquals(at("2026-11-03T09:00:00"), next)
    }

    @Test
    fun `repeating alarm rolls to next enabled day`() {
        val now = at("2026-11-03T10:00:00") // Tuesday, 09:00 passed
        val mondays = DaysOfWeek.of(DayOfWeek.MONDAY)
        val next = NextOccurrenceCalculator.nextOccurrence(alarm(9, 0, days = mondays), now, berlin)
        assertEquals(at("2026-11-09T09:00:00"), next)
    }

    @Test
    fun `friday evening alarm rolls over the weekend to monday`() {
        val now = at("2026-11-06T20:00:00") // Friday
        val weekdays =
            DaysOfWeek.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            )
        val next =
            NextOccurrenceCalculator.nextOccurrence(alarm(7, 15, days = weekdays), now, berlin)
        assertEquals(DayOfWeek.MONDAY, localAt(next!!).dayOfWeek)
        assertEquals(at("2026-11-09T07:15:00"), next)
    }

    @Test
    fun `midnight alarm rolls to tomorrow when now is past midnight`() {
        val now = at("2026-11-03T00:16:00")
        val next = NextOccurrenceCalculator.nextOccurrence(alarm(0, 15), now, berlin)
        assertEquals(at("2026-11-04T00:15:00"), next)
    }

    @Test
    fun `late evening alarm returns tonight`() {
        val now = at("2026-11-03T22:00:00")
        val next = NextOccurrenceCalculator.nextOccurrence(alarm(23, 59), now, berlin)
        assertEquals(at("2026-11-03T23:59:00"), next)
    }

    // --- skip-once --------------------------------------------------------

    @Test
    fun `skipped repeating alarm jumps to the following occurrence`() {
        val now = at("2026-11-03T05:00:00") // Tuesday 05:00, alarm at 06:30 today
        val next =
            NextOccurrenceCalculator.nextOccurrence(
                alarm(6, 30, skipped = true, updatedAt = now),
                now,
                berlin,
            )
        assertEquals(at("2026-11-04T06:30:00"), next)
    }

    @Test
    fun `a skip that already passed no longer drops the next occurrence`() {
        val skippedAt = at("2026-11-02T22:00:00") // Monday 22:00, skipping Tuesday 06:30
        val now = at("2026-11-03T09:00:00") // Tuesday 09:00, the skipped occurrence has passed
        val next =
            NextOccurrenceCalculator.nextOccurrence(
                alarm(6, 30, skipped = true, updatedAt = skippedAt),
                now,
                berlin,
            )
        // Wednesday, not Thursday: the skip was spent on Tuesday.
        assertEquals(at("2026-11-04T06:30:00"), next)
    }

    @Test
    fun `re-arming before the skipped occurrence keeps dropping it`() {
        val skippedAt = at("2026-11-02T22:00:00")
        val now = at("2026-11-03T06:00:00") // 30 minutes before the skipped 06:30
        val next =
            NextOccurrenceCalculator.nextOccurrence(
                alarm(6, 30, skipped = true, updatedAt = skippedAt),
                now,
                berlin,
            )
        assertEquals(at("2026-11-04T06:30:00"), next)
    }

    @Test
    fun `skipped one-shot alarm has no occurrence`() {
        val now = at("2026-11-03T05:00:00")
        val oneShot = alarm(6, 30, days = DaysOfWeek.NONE, skipped = true)
        assertNull(NextOccurrenceCalculator.nextOccurrence(oneShot, now, berlin))
    }

    @Test
    fun `skipped alarm still honours enabled days`() {
        val now = at("2026-11-03T05:00:00") // Tuesday
        val mondays = DaysOfWeek.of(DayOfWeek.MONDAY)
        val next =
            NextOccurrenceCalculator.nextOccurrence(
                alarm(6, 30, days = mondays, skipped = true, updatedAt = now),
                now,
                berlin,
            )
        // Mon Nov 9 is the occurrence being skipped; the scheduler arms the Monday after it.
        assertEquals(at("2026-11-16T06:30:00"), next)
    }

    // --- DST boundaries (Europe/Berlin) -----------------------------------

    @Test
    fun `spring forward gap shifts nonexistent wall time forward`() {
        // 2026-03-29 02:00 CET -> 03:00 CEST: wall time 02:30 does not exist.
        val now = at("2026-03-28T20:00:00")
        val next = NextOccurrenceCalculator.nextOccurrence(alarm(2, 30), now, berlin)
        // Resolved forward to 03:30 CEST on the 29th.
        assertEquals("2026-03-29T03:30", localAt(next!!).toString().substring(0, 16))
    }

    @Test
    fun `daily alarm keeps 24 hour cadence across fall back at non-ambiguous time`() {
        val first = at("2026-10-24T01:30:00")
        val next = NextOccurrenceCalculator.nextOccurrence(alarm(1, 30), first, berlin)
        // 01:30 is not ambiguous on Oct 25 (clocks fall back at 03:00 CEST -> 02:00 CET).
        assertEquals(24.hours, next!! - first)
    }

    @Test
    fun `fall back ambiguous wall time resolves to the earlier occurrence`() {
        // On Oct 25 the wall clock 02:30 happens twice (first CEST, then CET).
        val now = at("2026-10-24T20:00:00")
        val next = NextOccurrenceCalculator.nextOccurrence(alarm(2, 30), now, berlin)
        val resolved = localAt(next!!)
        assertEquals("2026-10-25T02:30", resolved.toString().substring(0, 16))
        // Earlier (first) occurrence = CEST = UTC+2, i.e. 00:30 UTC; the later one would be 01:30
        // UTC.
        assertEquals(at("2026-10-25T00:30:00", TimeZone.UTC), next)
    }

    @Test
    fun `next search continues past an ambiguous night`() {
        // Right after the first 02:30 (CEST), the next occurrence is the repeated 02:30 (CET).
        val now = at("2026-10-25T02:30:00") // resolves to CEST
        val next = NextOccurrenceCalculator.nextOccurrence(alarm(2, 30), now, berlin)
        // Same wall time today is not strictly after `now` (CET 02:30 < CEST 02:30), so tomorrow.
        assertEquals("2026-10-26T02:30", localAt(next!!).toString().substring(0, 16))
    }
}
