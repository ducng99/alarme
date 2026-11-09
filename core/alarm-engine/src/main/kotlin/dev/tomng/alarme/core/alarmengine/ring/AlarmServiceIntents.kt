package dev.tomng.alarme.core.alarmengine.ring

import android.content.Context
import android.content.Intent
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmIntents
import dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery
import dev.tomng.alarme.core.alarmengine.service.AlarmService

/** Intent contract between the ring dispatcher/receiver and [AlarmService]. */
object AlarmServiceIntents {

    const val EXTRA_START_MODE = "dev.tomng.alarme.extra.START_MODE"
    const val START_MODE_RECEIVER = "receiver"
    const val START_MODE_MANUAL = "manual"

    fun serviceIntent(context: Context, delivery: RingDelivery): Intent =
        Intent(context, AlarmService::class.java).apply {
            putExtra(EXTRA_START_MODE, START_MODE_RECEIVER)
            putExtra(AlarmIntents.EXTRA_ALARM_ID, delivery.alarmId)
            delivery.scheduleId?.let { putExtra(AlarmIntents.EXTRA_SCHEDULE_ID, it) }
            putExtra(AlarmIntents.EXTRA_ALARM_TIME, delivery.alarmTimeMillis)
            putExtra(AlarmIntents.EXTRA_TRIGGER_TYPE, delivery.triggerType.name)
            putExtra(AlarmIntents.EXTRA_IS_START_FAILURE_RETRY, delivery.isRetry)
        }

    /** Manual start (previews, tests): service must look the alarm up itself. */
    fun manualStartIntent(context: Context, alarmId: Long): Intent =
        Intent(context, AlarmService::class.java)
            .putExtra(EXTRA_START_MODE, START_MODE_MANUAL)
            .putExtra(AlarmIntents.EXTRA_ALARM_ID, alarmId)
}
