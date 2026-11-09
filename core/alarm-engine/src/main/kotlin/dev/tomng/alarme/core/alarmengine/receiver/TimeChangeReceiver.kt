package dev.tomng.alarme.core.alarmengine.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** `TIME_SET` + `TIMEZONE_CHANGED` -> re-arm the next alarm (guarded, once per pair). */
@AndroidEntryPoint
class TimeChangeReceiver @Inject constructor() : BroadcastReceiver() {

    @Inject lateinit var timeChangeAction: TimeChangeAction

    override fun onReceive(context: Context, intent: Intent) {
        timeChangeAction.onTimeChanged(intent.action)
    }
}
