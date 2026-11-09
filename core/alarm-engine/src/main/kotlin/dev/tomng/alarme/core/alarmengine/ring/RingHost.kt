package dev.tomng.alarme.core.alarmengine.ring

import android.content.Intent

/** Detects whether audio is currently routed to an external device. */
fun interface AudioOutputDetector {
    fun hasExternalOutput(): Boolean
}

/**
 * The seam the ring service drives once delivery is validated; the audio engine (Media3 players,
 * volume lock, TTS) implements it.
 */
interface RingHost {
    fun start(request: RingRequest)

    fun stop()
}

/** Starts the ring foreground service; abstracted so the retry policy is unit-testable. */
fun interface RingServiceStarter {
    fun start(intent: Intent)
}
