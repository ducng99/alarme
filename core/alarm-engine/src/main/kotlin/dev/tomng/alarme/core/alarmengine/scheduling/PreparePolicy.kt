package dev.tomng.alarme.core.alarmengine.scheduling

import kotlin.time.Instant

/**
 * Decides whether an early-wake prepare alarm is armed, and when: only alarms ringing between 05:00
 * and 10:59, never for skipped or snooze deliveries, five minutes ahead — fifteen on devices with
 * an OEM warm-up rule.
 */
object PreparePolicy {

    fun prepareAt(
        hour: Int,
        minute: Int,
        skipped: Boolean,
        triggerType: TriggerType,
        occurrence: Instant,
        hasWarmUpRule: Boolean,
        now: Instant,
    ): Instant? {
        if (skipped) return null
        if (triggerType != TriggerType.NORMAL) return null
        val minuteOfDay = hour * 60 + minute
        if (minuteOfDay < WINDOW_START_MINUTE || minuteOfDay >= WINDOW_END_MINUTE) return null
        val leadMillis = if (hasWarmUpRule) LEAD_OEM_MILLIS else LEAD_DEFAULT_MILLIS
        val raw = Instant.fromEpochMilliseconds(occurrence.toEpochMilliseconds() - leadMillis)
        return maxOf(raw, now)
    }

    /** First minute of the day that gets a reminder (05:00). */
    const val WINDOW_START_MINUTE = 5 * 60

    /** Exclusive end of the reminder window (11:00 local). */
    const val WINDOW_END_MINUTE = 11 * 60
    const val LEAD_DEFAULT_MILLIS = 5 * 60_000L
    const val LEAD_OEM_MILLIS = 15 * 60_000L
}
