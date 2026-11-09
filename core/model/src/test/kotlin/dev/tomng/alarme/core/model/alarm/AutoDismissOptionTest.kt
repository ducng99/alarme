package dev.tomng.alarme.core.model.alarm

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoDismissOptionTest {

    @Test
    fun `off maps to no timer`() {
        assertNull(AutoDismissOption.Off.toMillis())
    }

    @Test
    fun `after minutes maps to millis`() {
        assertEquals(10.minutes.inWholeMilliseconds, AutoDismissOption.After(10).toMillis())
    }

    @Test
    fun `factory rejects zero and negative minutes`() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AutoDismissOption.After(0)
        }
    }
}

class WakeUpCheckTest {

    @Test
    fun `zero minutes means off`() {
        assertEquals(false, WakeUpCheck(minutes = 0).isEnabled)
    }

    @Test
    fun `enabled check schedules after dismiss`() {
        val check = WakeUpCheck(minutes = 5)
        assertEquals(true, check.isEnabled)
        val dismissAt = Instant.parse("2026-11-03T06:40:00Z")
        assertEquals(dismissAt.plus(5.minutes), check.triggerAfter(dismissAt))
    }
}
