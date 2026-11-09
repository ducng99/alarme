package dev.tomng.alarme.core.audio.vibration

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * The ring vibration, ported from `h0/a` + `AlarmService` re-issuing: a 500 ms on/off waveform that
 * repeats for the whole ring. Started/stopped together with the audio host.
 */
class RingVibrator(context: Context) {

    private val vibrator = context.getSystemService(Vibrator::class.java)

    fun start() {
        // (0, 500, 500) repeat-from-1: buzz immediately, then alternate 500/500.
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 1))
    }

    fun stop() {
        vibrator?.cancel()
    }
}
