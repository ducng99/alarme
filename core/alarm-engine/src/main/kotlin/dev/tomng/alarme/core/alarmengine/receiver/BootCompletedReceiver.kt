package dev.tomng.alarme.core.alarmengine.receiver

import android.app.ActivityManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.tomng.alarme.core.alarmengine.scheduling.BootHandler
import dev.tomng.alarme.core.common.di.ApplicationScope
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * `BOOT_COMPLETED` / `LOCKED_BOOT_COMPLETED`: re-arm the next alarm after a reboot and log any ring
 * missed while the device was off.
 */
@AndroidEntryPoint
class BootCompletedReceiver @Inject constructor() : BroadcastReceiver() {

    @Inject lateinit var bootHandler: BootHandler

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED)
            return
        val wasForceStopped = probeForceStopped(context)
        Log.i(TAG, "boot_complete wasForceStopped=$wasForceStopped")

        val pending = goAsync()
        scope.launch {
            try {
                bootHandler.onBoot()
            } finally {
                pending.finish()
            }
        }
    }

    /** Probes `ApplicationStartInfo.wasForceStopped()` (API 35) — diagnostics only. */
    private fun probeForceStopped(context: Context): Boolean? = runCatching {
        if (Build.VERSION.SDK_INT >= 35) {
            context
                .getSystemService(ActivityManager::class.java)
                ?.getHistoricalProcessStartReasons(1)
                ?.firstOrNull()
                ?.wasForceStopped()
        } else {
            null
        }
    }
        .getOrNull()

    private companion object {
        const val TAG = "Alarme.Boot"
    }
}
