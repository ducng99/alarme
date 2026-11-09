package dev.tomng.alarme.core.model.scheduling

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Identifies one specific arming of one alarm: `<deviceId>_<alarmId>_<alarmTime HHmm>_<armedAt
 * HHmm>` (e.g. `b0ef-12_7_0630_2310`).
 *
 * Every arming mints a fresh id; the current one is stored (device-protected storage, M3) and
 * echoed in ring intents so stale/bogus deliveries can be rejected by [RingGate].
 */
data class ScheduleId(
    val deviceId: String,
    val alarmId: Long,
    val alarmTime: String,
    val nowTime: String,
) {
    val raw: String
        get() = "$deviceId${SEPARATOR}$alarmId${SEPARATOR}$alarmTime${SEPARATOR}$nowTime"

    companion object {
        const val SEPARATOR = "_"
    }
}

object ScheduleIds {

    fun format(
        deviceId: String,
        alarmId: Long,
        triggerAt: Instant,
        armedAt: Instant,
        zone: TimeZone,
    ): ScheduleId =
        ScheduleId(
            deviceId = deviceId,
            alarmId = alarmId,
            alarmTime = formatTime(triggerAt, zone),
            nowTime = formatTime(armedAt, zone),
        )

    fun parse(raw: String): ScheduleId? {
        val parts = raw.split(ScheduleId.SEPARATOR)
        if (parts.size != 4) return null
        val deviceId = parts[0]
        val alarmId = parts[1].toLongOrNull() ?: return null
        val alarmTime = parts[2]
        val nowTime = parts[3]
        if (deviceId.isBlank()) return null
        if (!isValidHhmm(alarmTime) || !isValidHhmm(nowTime)) return null
        return ScheduleId(deviceId, alarmId, alarmTime, nowTime)
    }

    fun isStale(received: String, expected: String): Boolean = received != expected

    private fun formatTime(instant: Instant, zone: TimeZone): String {
        val time = instant.toLocalDateTime(zone).time
        return "${time.hour.twoDigits()}${time.minute.twoDigits()}"
    }

    private fun isValidHhmm(value: String): Boolean {
        if (value.length != 4 || value.any { !it.isDigit() }) return false
        val hour = value.substring(0, 2).toInt()
        val minute = value.substring(2, 4).toInt()
        return hour in 0..23 && minute in 0..59
    }

    private fun Int.twoDigits(): String = toString().padStart(2, '0')
}

/** Why a ring delivery was rejected before it could ring. */
sealed interface RingRejection {
    /** No schedule id, or one that does not match the armed id (deleted/replaced alarm). */
    data object InvalidAlarm : RingRejection

    /**
     * The delivery arrived far too late to be meaningful (device slept through, replayed
     * broadcast).
     */
    data object ExpiredAlarm : RingRejection
}

/**
 * Validates an incoming ring delivery against what was armed. The default grace of one minute
 * tolerates slow delivery (the reference app retries blocked starts for up to ~55 s) while
 * rejecting broadcast replays from the distant past.
 */
object RingGate {

    fun check(
        expectedScheduleId: String?,
        receivedScheduleId: String?,
        scheduledAt: Instant,
        receivedAt: Instant,
        staleGrace: Duration = DEFAULT_STALE_GRACE,
    ): RingRejection? {
        if (receivedScheduleId == null || expectedScheduleId == null)
            return RingRejection.InvalidAlarm
        if (ScheduleIds.isStale(receivedScheduleId, expectedScheduleId))
            return RingRejection.InvalidAlarm
        if (receivedAt - scheduledAt > staleGrace) return RingRejection.ExpiredAlarm
        return null
    }

    private val DEFAULT_STALE_GRACE = 60.seconds
}
