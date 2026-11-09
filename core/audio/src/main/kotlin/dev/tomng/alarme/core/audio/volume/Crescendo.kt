package dev.tomng.alarme.core.audio.volume

import kotlin.math.ceil

/**
 * Ring volume math: the alarm forces the stream to `ceil(maxVolume * volumeRatio)` — the per-alarm
 * volume setting, not necessarily maximum — clamped to the stream range.
 */
fun targetVolume(max: Int, ratio: Float): Int = ceil(max * ratio).toInt().coerceIn(0, max)

/**
 * The floor a crescendo ramp starts from: quieter alarms start from a *higher* floor so the ramp
 * remains audible.
 */
fun crescendoFloor(ratio: Float): Float =
    when {
        ratio < 0.1f -> 0.3f
        ratio < 0.3f -> 0.12f
        ratio < 0.5f -> 0.08f
        ratio < 0.7f -> 0.065f
        else -> 0.05f
    }

/**
 * One step of the crescendo ramp: every second the player gain is `clamp((t/duration)^2, floor,
 * 1)`. A non-positive duration jumps straight to full volume.
 */
fun crescendoGain(elapsedSeconds: Int, durationSeconds: Int, floor: Float): Float {
    if (durationSeconds <= 0) return 1f
    val progress = elapsedSeconds.toFloat() / durationSeconds
    return (progress * progress).coerceIn(floor, 1f)
}
