package dev.tomng.alarme.core.model.scheduling

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleIdTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    private fun at(iso: String, zone: TimeZone = berlin): Instant =
        LocalDateTime.parse(iso).toInstant(zone)

    @Test
    fun `format follows deviceId_alarmId_alarmTime_nowTime`() {
        val id =
            ScheduleIds.format(
                deviceId = "b0ef9f2e-12",
                alarmId = 7L,
                triggerAt = at("2026-11-04T06:30:00"),
                armedAt = at("2026-11-03T23:10:00"),
                zone = berlin,
            )
        assertEquals("b0ef9f2e-12_7_0630_2310", id.raw)
    }

    @Test
    fun `format and parse round trip`() {
        val raw = "device_12_0630_2310"
        val parsed = ScheduleIds.parse(raw)
        assertEquals(
            ScheduleId(deviceId = "device", alarmId = 12L, alarmTime = "0630", nowTime = "2310"),
            parsed,
        )
        assertEquals(raw, parsed!!.raw)
    }

    @Test
    fun `parse rejects malformed ids`() {
        assertNull(ScheduleIds.parse("nope"))
        assertNull(ScheduleIds.parse("a_b_c"))
        assertNull(ScheduleIds.parse("a_b_9900_2310")) // alarm time not HHmm
        assertNull(ScheduleIds.parse("_7_0630_2310")) // empty device id
    }

    @Test
    fun `ring gate accepts matching id within grace`() {
        val expected =
            ScheduleIds.format(
                "device",
                7L,
                at("2026-11-04T06:30:00"),
                at("2026-11-03T23:10:00"),
                berlin,
            )
        val rejection =
            RingGate.check(
                expectedScheduleId = expected.raw,
                receivedScheduleId = expected.raw,
                scheduledAt = at("2026-11-04T06:30:00"),
                receivedAt = at("2026-11-04T06:30:20"),
            )
        assertNull(rejection)
    }

    @Test
    fun `ring gate rejects mismatching id as invalid`() {
        val rejection =
            RingGate.check(
                expectedScheduleId = "device_7_0630_2310",
                receivedScheduleId = "device_8_0700_2310",
                scheduledAt = at("2026-11-04T06:30:00"),
                receivedAt = at("2026-11-04T06:30:20"),
            )
        assertEquals(RingRejection.InvalidAlarm, rejection)
    }

    @Test
    fun `ring gate rejects missing id as invalid`() {
        val rejection =
            RingGate.check(
                expectedScheduleId = "device_7_0630_2310",
                receivedScheduleId = null,
                scheduledAt = at("2026-11-04T06:30:00"),
                receivedAt = at("2026-11-04T06:30:20"),
            )
        assertEquals(RingRejection.InvalidAlarm, rejection)
    }

    @Test
    fun `ring gate rejects deliveries older than grace as expired`() {
        val rejection =
            RingGate.check(
                expectedScheduleId = "device_7_0630_2310",
                receivedScheduleId = "device_7_0630_2310",
                scheduledAt = at("2026-11-04T06:30:00"),
                receivedAt = at("2026-11-04T06:30:00").plus(10.minutes),
            )
        assertEquals(RingRejection.ExpiredAlarm, rejection)
    }

    @Test
    fun `grace is configurable`() {
        val rejection =
            RingGate.check(
                expectedScheduleId = "device_7_0630_2310",
                receivedScheduleId = "device_7_0630_2310",
                scheduledAt = at("2026-11-04T06:30:00"),
                receivedAt = at("2026-11-04T06:30:00").plus(90.seconds),
                staleGrace = 30.seconds,
            )
        assertEquals(RingRejection.ExpiredAlarm, rejection)
    }

    @Test
    fun `isStale compares raw ids`() {
        assertTrue(ScheduleIds.isStale("device_7_0630_2310", "device_8_0700_2310"))
        assertTrue(!ScheduleIds.isStale("device_7_0630_2310", "device_7_0630_2310"))
    }
}
