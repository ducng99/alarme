package dev.tomng.alarme.core.alarmengine.dispatch

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomng.alarme.core.alarmengine.service.WakeUpCheckServiceIntents
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts the wake-up check service from the exact-alarm broadcast (PI 100). The broadcast runs with
 * an exact-alarm allowance, so a foreground service start is permitted.
 */
@Singleton
class AndroidWakeUpCheckDispatcher
@Inject
constructor(@ApplicationContext private val context: Context) : WakeUpCheckDispatcher {

    override fun onStartCheck(alarmId: Long) {
        context.startForegroundService(WakeUpCheckServiceIntents.startIntent(context, alarmId))
    }
}
