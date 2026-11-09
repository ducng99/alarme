package dev.tomng.alarme.core.alarmengine.power

import android.content.Context
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

/**
 * Named, non-reference-counted partial wakelocks: each component acquires under its own tag so we
 * never over-release another holder's lock.
 */
@Singleton
class Wakelocks
@Inject
constructor(
    @ApplicationContext context: Context,
) {
    private val powerManager = context.getSystemService(PowerManager::class.java)

    fun acquire(name: String, timeout: Duration): PowerManager.WakeLock? {
        val manager = powerManager ?: return null
        return manager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG_PREFIX$name").apply {
            setReferenceCounted(false)
            acquire(timeout.inWholeMilliseconds)
        }
    }

    companion object {
        const val TAG_PREFIX = "dev.tomng.alarme:"

        /** Held across the whole ring, renewed by the service. */
        const val ALARM_SERVICE = "AlarmServiceWakeLock"

        /** The wake-up check service holds its own wakelock through the 100 s window. */
        const val WAKE_UP_CHECK = "WakeUpCheckServiceWakeLock"

        /** Held ~1 min by the warm-up receiver so the CPU is awake at ring time. */
        const val WARMUP = "AlarmWarmupWakeLock"

        /** 10 s across prepare work. */
        const val PREPARE = "AlarmPrepareWakeLock"
    }
}
