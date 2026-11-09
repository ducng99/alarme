package dev.tomng.alarme.core.alarmengine.receiver

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

/** `MY_PACKAGE_REPLACED` — updates clear alarms; re-arm immediately. */
@AndroidEntryPoint
class AppUpdateReceiver @Inject constructor() : BroadcastReceiver() {

    @Inject lateinit var scheduler: AlarmScheduler

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pending = goAsync()
        scope.launch {
            try {
                scheduler.rearm(RescheduleReason.APP_UPDATE)
            } finally {
                pending.finish()
            }
        }
    }
}
