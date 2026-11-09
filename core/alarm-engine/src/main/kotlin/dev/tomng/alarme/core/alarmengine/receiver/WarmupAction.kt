package dev.tomng.alarme.core.alarmengine.receiver

import android.content.Intent
import dev.tomng.alarme.core.alarmengine.power.Wakelocks
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmIntents
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * OEM pre-wake: hold the CPU with a one-minute partial wakelock so the ring alarm is delivered on
 * time. OEMs whose rule says otherwise skip it.
 */
class WarmupAction
@Inject
constructor(
    private val wakelocks: Wakelocks,
) {
    fun onWarmup(intent: Intent) {
        val acquire = intent.getBooleanExtra(AlarmIntents.EXTRA_ACQUIRE_WAKE_LOCK, false)
        if (acquire) wakelocks.acquire(Wakelocks.WARMUP, WAKELOCK_TIMEOUT)
    }

    private companion object {
        val WAKELOCK_TIMEOUT = 1.minutes
    }
}
