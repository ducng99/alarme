package dev.tomng.alarme.core.alarmengine.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.tomng.alarme.core.alarmengine.scheduling.RingDeliveryHandler
import dev.tomng.alarme.core.common.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** PI 1 — the ring itself (`setAlarmClock`). Validates, then hands over to the ring host. */
@AndroidEntryPoint
class AlarmRingReceiver @Inject constructor() : BroadcastReceiver() {

    @Inject lateinit var handler: RingDeliveryHandler

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                handler.handle(intent)
            } finally {
                pending.finish()
            }
        }
    }
}
