package dev.tomng.alarme.core.alarmengine.scheduling

import dev.tomng.alarme.core.model.alarm.Alarm
import kotlin.time.Instant

/**
 * Owns the single system alarm: the app keeps at most one ring armed (plus its auxiliary stages)
 * and re-arms on every mutation.
 */
interface AlarmScheduler {

    /**
     * Cancels any previous arming, recomputes the next upcoming alarm and arms the ring
     * (`setAlarmClock`), the OEM warm-up and the early-wake prepare when applicable.
     */
    suspend fun rearm(reason: RescheduleReason): SchedulingResult

    /** Arms the wake-up check re-ring (request code 100, `setExactAndAllowWhileIdle`). */
    fun armWakeUpCheck(alarmId: Long, at: Instant)

    fun cancelWakeUpCheck()

    /**
     * The live snooze: its own alarm-clock PI so it survives process death and shows in the bar.
     */
    fun armSnooze(alarmId: Long, scheduleId: String?, at: Instant, count: Int)

    fun cancelSnooze()

    /** Re-arms a blocked FGS start at `at` with the retry flag. */
    fun armRetryRing(delivery: RingDelivery, at: Instant)
}

/** Outcome of a re-arm pass. */
data class SchedulingResult(
    /** The alarm that is now armed, or null when nothing is scheduled. */
    val next: Alarm?,
    val nextAt: Instant?,
    /**
     * Enabled alarms whose persisted next occurrence already passed (device was off / app was dead)
     * — callers log these as missed ring events.
     */
    val missed: List<Alarm>,
    /**
     * False when [next] exists but the platform refused to arm it (the exact-alarm grant is
     * missing), so the alarm will not ring at [nextAt] until the user allows it.
     */
    val armed: Boolean = true,
)

/** Supplier for the OEM warm-up rule so tests can pin it. */
fun interface WarmUpRule {
    fun get(): WarmUpOptions?
}
