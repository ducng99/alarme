package dev.tomng.alarme.core.alarmengine.service

import android.app.Service
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.tomng.alarme.core.alarmengine.ring.AlarmServiceController
import dev.tomng.alarme.core.common.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope

/**
 * The ringing host: foreground service, type `systemExempted`, `START_REDELIVER_INTENT` so a killed
 * service gets its intent back. All logic lives in [AlarmServiceController]; this shell only wires
 * Hilt.
 */
@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var controller: AlarmServiceController

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        controller.onStartCommand(this, intent, flags, startId)

    override fun onDestroy() {
        controller.onDestroy()
        super.onDestroy()
    }
}
