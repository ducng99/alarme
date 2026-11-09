package dev.tomng.alarme.core.alarmengine.scheduling

import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.data.repository.AlarmEvent
import dev.tomng.alarme.core.data.repository.AlarmEventType
import dev.tomng.alarme.core.data.repository.RingEventRepository
import dev.tomng.alarme.core.model.alarm.Alarm
import javax.inject.Inject
import kotlin.time.Instant

/**
 * Boot re-arm: schedule the next alarm again and record any alarm whose ring was missed while the
 * device was off.
 */
class BootHandler
@Inject
constructor(
    private val scheduler: AlarmScheduler,
    private val ringEventRepository: RingEventRepository,
    private val scheduleIdStore: ScheduleIdStore,
    private val snoozeStore: SnoozeStore,
    private val clock: AppClock,
) {
    suspend fun onBoot(): SchedulingResult {
        val result = scheduler.rearm(RescheduleReason.REBOOT)
        result.missed.forEach { alarm ->
            ringEventRepository.logEvent(alarm.missedEvent(clock.now()))
        }
        restoreSnooze(result)
        return result
    }

    /**
     * A reboot mid-snooze re-arms the snooze for its stored finish — or right now if that finish
     * passed while the device was off (the user still has to wake up). The snooze reuses the
     * schedule id the rearm just minted, so the ring gate accepts its delivery; with no next alarm
     * at all (everything disabled) there is nothing to restore.
     */
    private fun restoreSnooze(result: SchedulingResult) {
        if (result.next == null) return
        val record = snoozeStore.load() ?: return
        val scheduleId = scheduleIdStore.load() ?: return
        val finishAt = maxOf(record.finishAt(), clock.now())
        scheduler.armSnooze(record.alarmId, scheduleId, finishAt, record.count)
    }

    private fun Alarm.missedEvent(at: Instant) =
        AlarmEvent(
            id = 0L,
            event = AlarmEventType.MISSED,
            ringRootId = "",
            timestamp = at,
            triggerType = REASON_MISSED,
            missionsJson = null,
            alarmName = name,
            alarmEmoji = emoji,
            scheduleId = "",
        )

    private companion object {
        const val REASON_MISSED = "missed_alarm"
    }
}
