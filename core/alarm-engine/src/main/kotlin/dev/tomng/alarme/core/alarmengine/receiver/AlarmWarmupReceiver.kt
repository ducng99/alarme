package dev.tomng.alarme.core.alarmengine.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** PI 2 — OEM pre-wake, seconds before the ring. */
@AndroidEntryPoint
class AlarmWarmupReceiver @Inject constructor() : BroadcastReceiver() {

    @Inject lateinit var warmupAction: WarmupAction

    override fun onReceive(context: Context, intent: Intent) {
        warmupAction.onWarmup(intent)
    }
}
