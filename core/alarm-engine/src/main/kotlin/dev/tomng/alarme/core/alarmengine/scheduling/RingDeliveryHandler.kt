package dev.tomng.alarme.core.alarmengine.scheduling

import android.content.Intent
import dev.tomng.alarme.core.alarmengine.dispatch.RingDispatcher
import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.data.repository.AlarmEvent
import dev.tomng.alarme.core.data.repository.AlarmEventType
import dev.tomng.alarme.core.model.scheduling.RingGate
import dev.tomng.alarme.core.model.scheduling.RingRejection
import javax.inject.Inject
import kotlin.time.Instant

/**
 * Validates an incoming ring delivery against the armed schedule before anything rings:
 * unknown/mismatched schedule ids and far-late deliveries are blocked, logged and the next alarm
 * re-armed — a deleted alarm can never ring.
 */
class RingDeliveryHandler
@Inject
constructor(
    private val scheduleIdStore: ScheduleIdStore,
    private val scheduler: AlarmScheduler,
    private val ringEventRepository: dev.tomng.alarme.core.data.repository.RingEventRepository,
    private val ringDispatcher: RingDispatcher,
    private val snoozeStore: SnoozeStore,
    private val clock: AppClock,
) {
    suspend fun handle(intent: Intent) {
        val delivery = RingDelivery.fromIntent(intent)
        if (delivery == null) {
            block(
                scheduleId = null,
                reason = REASON_INVALID,
                reschedule = RescheduleReason.FALSE_ALARM,
            )
            return
        }

        val rejection =
            if (delivery.triggerType == TriggerType.SNOOZE) {
                snoozeRejection(delivery)
            } else {
                RingGate.check(
                    expectedScheduleId = scheduleIdStore.load(),
                    receivedScheduleId = delivery.scheduleId,
                    scheduledAt = Instant.fromEpochMilliseconds(delivery.alarmTimeMillis),
                    receivedAt = clock.now(),
                )
            }
        when (rejection) {
            null -> ringDispatcher.dispatch(delivery)
            RingRejection.InvalidAlarm ->
                block(delivery.scheduleId, REASON_INVALID, RescheduleReason.FALSE_ALARM)
            RingRejection.ExpiredAlarm ->
                block(delivery.scheduleId, REASON_EXPIRED, RescheduleReason.EXPIRED_ALARM)
        }
    }

    /**
     * A snooze re-rings the alarm that was ringing a few minutes ago, so the armed schedule id has
     * already moved on to the next occurrence: the durable snooze record is what authenticates the
     * delivery, while the gate still rejects a late one.
     */
    private fun snoozeRejection(delivery: RingDelivery): RingRejection? {
        if (snoozeStore.load()?.alarmId != delivery.alarmId) return RingRejection.InvalidAlarm
        return RingGate.check(
            expectedScheduleId = delivery.scheduleId,
            receivedScheduleId = delivery.scheduleId,
            scheduledAt = Instant.fromEpochMilliseconds(delivery.alarmTimeMillis),
            receivedAt = clock.now(),
        )
    }

    private suspend fun block(scheduleId: String?, reason: String, reschedule: RescheduleReason) {
        ringEventRepository.logEvent(
            AlarmEvent(
                id = 0L,
                event = AlarmEventType.BLOCKED,
                ringRootId = scheduleId.orEmpty(),
                timestamp = clock.now(),
                triggerType = reason,
                missionsJson = null,
                alarmName = "",
                alarmEmoji = "",
                scheduleId = scheduleId.orEmpty(),
            ),
        )
        scheduler.rearm(reschedule)
    }

    private companion object {
        const val REASON_INVALID = "invalid_alarm"
        const val REASON_EXPIRED = "expired_alarm"
    }
}
