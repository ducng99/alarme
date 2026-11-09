package dev.tomng.alarme.core.alarmengine.service

import android.app.Service
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The wake-up check host (notification id 1002): silent, vibration-only. All logic lives in
 * [WakeUpCheckServiceController]; this shell only wires Hilt. Not redelivered: a check lost to
 * process death is restored as a pending check and re-armed by the boot path.
 */
@AndroidEntryPoint
class WakeUpCheckService : Service() {

    @Inject lateinit var controller: WakeUpCheckServiceController

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        controller.onStartCommand(
            this,
            alarmId = intent?.let(WakeUpCheckServiceIntents::alarmId) ?: -1L,
        )
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        controller.onDestroy()
        super.onDestroy()
    }
}
