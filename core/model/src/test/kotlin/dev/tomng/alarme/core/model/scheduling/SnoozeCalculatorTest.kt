package dev.tomng.alarme.core.model.scheduling

import dev.tomng.alarme.core.model.alarm.SnoozeOption
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class SnoozeCalculatorTest {

    private val now = Instant.parse("2026-11-03T06:30:11Z")
    private val option = SnoozeOption(enabled = true, minutes = 5, limit = 3)

    @Test
    fun `snooze disabled denies`() {
        val result = SnoozeCalculator.snooze(SnoozeOption(enabled = false), now, countSoFar = 0)
        assertEquals(SnoozeResult.Denied.SnoozeDisabled, result)
    }

    @Test
    fun `finish time is now plus duration`() {
        val result = SnoozeCalculator.snooze(option, now, countSoFar = 0)
        val allowed = result as SnoozeResult.Allowed
        assertEquals(now + 5.minutes, allowed.finishAt)
        assertEquals(1, allowed.nextCount)
    }

    @Test
    fun `count below limit is allowed`() {
        val result = SnoozeCalculator.snooze(option, now, countSoFar = 2)
        assertEquals(SnoozeResult.Allowed(now + 5.minutes, 3), result)
    }

    @Test
    fun `count at limit denies`() {
        val result = SnoozeCalculator.snooze(option, now, countSoFar = 3)
        assertEquals(SnoozeResult.Denied.SnoozeLimitReached, result)
    }

    @Test
    fun `limit of zero denies immediately`() {
        val result = SnoozeCalculator.snooze(option.copy(limit = 0), now, countSoFar = 0)
        assertEquals(SnoozeResult.Denied.SnoozeLimitReached, result)
    }

    @Test
    fun `no limit means always allowed`() {
        val unlimited = SnoozeOption(enabled = true, minutes = 10, limit = SnoozeOption.UNLIMITED)
        val result = SnoozeCalculator.snooze(unlimited, now, countSoFar = 99)
        assertEquals(SnoozeResult.Allowed(now + 10.minutes, 100), result)
    }
}
