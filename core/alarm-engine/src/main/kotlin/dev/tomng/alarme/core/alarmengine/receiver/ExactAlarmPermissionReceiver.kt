package dev.tomng.alarme.core.alarmengine.receiver

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.common.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`: the user granted exact-alarm access again
 * — re-arm so the auxiliary (`setExactAndAllowWhileIdle`) stages come back.
 */
@AndroidEntryPoint
class ExactAlarmPermissionReceiver @Inject constructor() : BroadcastReceiver() {

    @Inject lateinit var scheduler: AlarmScheduler

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
            return

        val pending = goAsync()
        scope.launch {
            try {
                scheduler.rearm(RescheduleReason.EXACT_ALARM_PERMISSION_CHANGED)
            } finally {
                pending.finish()
            }
        }
    }
}
