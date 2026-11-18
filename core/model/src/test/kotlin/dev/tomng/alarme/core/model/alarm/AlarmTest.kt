package dev.tomng.alarme.core.model.alarm

import kotlin.time.Instant
import org.junit.Assert.assertThrows
import org.junit.Test

class AlarmTest {

    private fun alarm(
        hour: Int = 6,
        minute: Int = 30,
        volumeRatio: Float = 0.8f,
        wakeUpCheckMinutes: Int = 0,
        snooze: SnoozeOption = SnoozeOption(),
    ) =
        Alarm(
            id = 1L,
            type = AlarmType.NORMAL,
            name = "morning",
            hour = hour,
            minute = minute,
            daysOfWeek = DaysOfWeek.EVERY_DAY,
            volumeRatio = volumeRatio,
            snoozeOption = snooze,
            wakeUpCheck = WakeUpCheck(wakeUpCheckMinutes),
            createdAt = Instant.parse("2026-11-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-11-01T00:00:00Z"),
        )

    @Test
    fun `hour bounds are enforced`() {
        assertThrows(IllegalArgumentException::class.java) { alarm(hour = 24) }
        assertThrows(IllegalArgumentException::class.java) { alarm(hour = -1) }
    }

    @Test
    fun `minute bounds are enforced`() {
        assertThrows(IllegalArgumentException::class.java) { alarm(minute = 60) }
    }

    @Test
    fun `volume ratio must be within 0 to 1`() {
        assertThrows(IllegalArgumentException::class.java) { alarm(volumeRatio = 2f) }
        assertThrows(IllegalArgumentException::class.java) { alarm(volumeRatio = -0.5f) }
    }

    @Test
    fun `snooze option bounds are enforced`() {
        assertThrows(IllegalArgumentException::class.java) {
            alarm(snooze = SnoozeOption(minutes = 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            alarm(snooze = SnoozeOption(minutes = 121))
        }
        assertThrows(IllegalArgumentException::class.java) {
            alarm(snooze = SnoozeOption(limit = 10))
        }
    }
}
