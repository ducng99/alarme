package dev.tomng.alarme.core.alarmengine.scheduling

import android.content.Context
import android.content.Intent
import dev.tomng.alarme.core.alarmengine.receiver.AlarmPrepareReceiver
import dev.tomng.alarme.core.alarmengine.receiver.AlarmRingReceiver
import dev.tomng.alarme.core.alarmengine.receiver.AlarmWarmupReceiver
import dev.tomng.alarme.core.alarmengine.receiver.WakeUpCheckReceiver

/** Intent extras and builders shared by the scheduler and the receivers. */
object AlarmIntents {
    const val ACTION_RING = "dev.tomng.alarme.action.RING"
    const val ACTION_WARMUP = "dev.tomng.alarme.action.WARMUP"
    const val ACTION_PREPARE = "dev.tomng.alarme.action.PREPARE"
    const val ACTION_WAKE_UP_CHECK = "dev.tomng.alarme.action.WAKE_UP_CHECK"

    const val EXTRA_ALARM_ID = "dev.tomng.alarme.extra.ALARM_ID"
    const val EXTRA_SCHEDULE_ID = "dev.tomng.alarme.extra.SCHEDULE_ID"
    const val EXTRA_ALARM_TIME = "dev.tomng.alarme.extra.ALARM_TIME"
    const val EXTRA_TRIGGER_TYPE = "dev.tomng.alarme.extra.TRIGGER_TYPE"
    const val EXTRA_ACQUIRE_WAKE_LOCK = "dev.tomng.alarme.extra.ACQUIRE_WAKE_LOCK"
    const val EXTRA_IS_START_FAILURE_RETRY = "dev.tomng.alarme.extra.IS_START_FAILURE_RETRY"
    const val EXTRA_SNOOZE_COUNT = "dev.tomng.alarme.extra.SNOOZE_COUNT"

    /** Delivery broadcasts carry `0x10000020`: foreground + include stopped packages. */
    private const val BROADCAST_FLAGS =
        Intent.FLAG_RECEIVER_FOREGROUND or Intent.FLAG_INCLUDE_STOPPED_PACKAGES

    fun ringIntent(
        context: Context,
        alarmId: Long,
        scheduleId: String,
        alarmTimeMillis: Long,
        triggerType: TriggerType,
        isRetry: Boolean = false,
        snoozeCount: Int = 0,
    ): Intent =
        Intent(context, AlarmRingReceiver::class.java)
            .setAction(ACTION_RING)
            .addFlags(BROADCAST_FLAGS)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_SCHEDULE_ID, scheduleId)
            .putExtra(EXTRA_ALARM_TIME, alarmTimeMillis)
            .putExtra(EXTRA_TRIGGER_TYPE, triggerType.name)
            .putExtra(EXTRA_IS_START_FAILURE_RETRY, isRetry)
            .putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)

    fun warmupIntent(context: Context, acquireWakeLock: Boolean): Intent =
        Intent(context, AlarmWarmupReceiver::class.java)
            .setAction(ACTION_WARMUP)
            .addFlags(BROADCAST_FLAGS)
            .putExtra(EXTRA_ACQUIRE_WAKE_LOCK, acquireWakeLock)

    fun prepareIntent(context: Context): Intent =
        Intent(context, AlarmPrepareReceiver::class.java)
            .setAction(ACTION_PREPARE)
            .addFlags(BROADCAST_FLAGS)

    fun wakeUpCheckIntent(context: Context, alarmId: Long, alarmTimeMillis: Long): Intent =
        Intent(context, WakeUpCheckReceiver::class.java)
            .setAction(ACTION_WAKE_UP_CHECK)
            .addFlags(BROADCAST_FLAGS)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_ALARM_TIME, alarmTimeMillis)
            .putExtra(EXTRA_TRIGGER_TYPE, TriggerType.WAKE_UP_CHECK.name)
}

/** A parsed ring/wake-up-check delivery, validated. */
data class RingDelivery(
    val alarmId: Long,
    /** Absent for wake-up-check deliveries (they are not gated by schedule id). */
    val scheduleId: String?,
    val alarmTimeMillis: Long,
    val triggerType: TriggerType,
    val isRetry: Boolean = false,
    val snoozeCount: Int = 0,
) {
    companion object {
        fun fromIntent(intent: Intent): RingDelivery? {
            val id = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, Long.MIN_VALUE)
            if (id == Long.MIN_VALUE) return null
            val scheduleId = intent.getStringExtra(AlarmIntents.EXTRA_SCHEDULE_ID)
            val alarmTime = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_TIME, Long.MIN_VALUE)
            if (alarmTime == Long.MIN_VALUE) return null
            val snoozeCount = intent.getIntExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, 0)
            if (
                scheduleId != null &&
                    dev.tomng.alarme.core.model.scheduling.ScheduleIds.parse(scheduleId) == null
            ) {
                return null
            }
            val trigger =
                intent.getStringExtra(AlarmIntents.EXTRA_TRIGGER_TYPE)?.let {
                    runCatching { TriggerType.valueOf(it) }.getOrNull()
                } ?: return null
            return RingDelivery(
                alarmId = id,
                scheduleId = scheduleId,
                alarmTimeMillis = alarmTime,
                triggerType = trigger,
                isRetry = intent.getBooleanExtra(AlarmIntents.EXTRA_IS_START_FAILURE_RETRY, false),
                snoozeCount = snoozeCount,
            )
        }
    }
}
