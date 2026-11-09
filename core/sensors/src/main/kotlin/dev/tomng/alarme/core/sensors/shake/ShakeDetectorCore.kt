package dev.tomng.alarme.core.sensors.shake

/**
 * Pure shake rule: one accelerometer sample whose magnitude deviates from 1 g by more than
 * [threshold] counts as a shake, unless it lands inside [debounceMillis] of the previous one — the
 * debounce is what stops one vigorous shake from counting many times.
 */
class ShakeDetectorCore(
    private val threshold: Float = DEFAULT_THRESHOLD,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
) {
    private var lastShakeAtMillis: Long = NEVER

    /** Shakes counted so far. */
    var count: Int = 0
        private set

    /**
     * Feeds one accelerometer sample (m/s²) at [timestampMillis] (any monotonic clock). Returns
     * true when the sample counted as a new shake.
     */
    fun onSample(x: Float, y: Float, z: Float, timestampMillis: Long): Boolean {
        val g = magnitudeG(x, y, z)
        if (kotlin.math.abs(g - 1.0f) <= threshold) return false
        if (lastShakeAtMillis != NEVER && timestampMillis - lastShakeAtMillis <= debounceMillis) {
            return false
        }
        lastShakeAtMillis = timestampMillis
        count++
        return true
    }

    fun reset() {
        count = 0
        lastShakeAtMillis = Long.MIN_VALUE
    }

    private fun magnitudeG(x: Float, y: Float, z: Float): Float =
        kotlin.math.sqrt(x * x + y * y + z * z) / GRAVITY

    companion object {
        const val NEVER = Long.MIN_VALUE

        /** Standard gravity in m/s², as the sensor reports against. */
        const val GRAVITY = 9.80665f
        const val DEFAULT_THRESHOLD = 1.3f
        const val DEFAULT_DEBOUNCE_MILLIS = 300L
    }
}
