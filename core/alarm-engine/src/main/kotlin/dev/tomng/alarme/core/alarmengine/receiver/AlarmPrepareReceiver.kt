package dev.tomng.alarme.core.alarmengine.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.tomng.alarme.core.alarmengine.dispatch.PrepareActions
import dev.tomng.alarme.core.alarmengine.power.Wakelocks
import dev.tomng.alarme.core.common.di.ApplicationScope
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** PI 3 — early-wake prepare: 10 s wakelock, then prepare work. */
@AndroidEntryPoint
class AlarmPrepareReceiver @Inject constructor() : BroadcastReceiver() {

    @Inject lateinit var wakelocks: Wakelocks

    @Inject lateinit var prepareActions: PrepareActions

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val wakeLock = wakelocks.acquire(Wakelocks.PREPARE, WAKELOCK_TIMEOUT)
        val pending = goAsync()
        scope.launch {
            try {
                prepareActions.onPrepare()
            } finally {
                wakeLock?.release()
                pending.finish()
            }
        }
    }

    private companion object {
        val WAKELOCK_TIMEOUT = 10.seconds
    }
}
