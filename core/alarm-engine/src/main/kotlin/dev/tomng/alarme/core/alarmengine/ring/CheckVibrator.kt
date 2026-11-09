package dev.tomng.alarme.core.alarmengine.ring

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The wake-up check pulses instead of playing audio ("quiet" second alarm). The seam keeps the
 * controller testable.
 */
interface CheckVibrator {
    fun start()

    fun stop()
}

/** The repeating 500 ms on/off buzz, stopped when the check ends. */
@Singleton
class AndroidCheckVibrator @Inject constructor(@ApplicationContext context: Context) :
    CheckVibrator {

    private val vibrator = context.getSystemService(Vibrator::class.java)

    override fun start() {
        // (0, 500, 500) repeat-from-1: buzz immediately, then alternate 500/500.
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 1))
    }

    override fun stop() {
        vibrator?.cancel()
    }
}
