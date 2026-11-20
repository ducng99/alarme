package dev.tomng.alarme.core.alarmengine.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.tomng.alarme.core.alarmengine.dispatch.SkipAlarmAction
import dev.tomng.alarme.core.alarmengine.notifications.AlarmNotifications
import javax.inject.Inject

/** The reminder's "Skip alarm" action: skip the next occurrence of that alarm once. */
@AndroidEntryPoint
class SkipAlarmReceiver @Inject constructor() : BroadcastReceiver() {

    @Inject lateinit var action: SkipAlarmAction

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getLongExtra(EXTRA_ALARM_ID, -1L)
        context
            .getSystemService(NotificationManager::class.java)
            .cancel(AlarmNotifications.REMINDER_NOTIFICATION_ID)
        if (alarmId != -1L) action.skip(alarmId)
    }

    companion object {
        const val EXTRA_ALARM_ID = "dev.tomng.alarme.extra.SKIP_ALARM_ID"
    }
}
