package dev.tomng.alarme.core.alarmengine.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.tomng.alarme.core.alarmengine.dispatch.WakeUpCheckDispatcher
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmIntents
import javax.inject.Inject

/** PI 100 — the wake-up check re-ring after dismiss (host service lands in M9). */
@AndroidEntryPoint
class WakeUpCheckReceiver @Inject constructor() : BroadcastReceiver() {

    @Inject lateinit var dispatcher: WakeUpCheckDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AlarmIntents.ACTION_WAKE_UP_CHECK) return
        val alarmId = intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1L)
        if (alarmId != -1L) dispatcher.onStartCheck(alarmId)
    }
}
