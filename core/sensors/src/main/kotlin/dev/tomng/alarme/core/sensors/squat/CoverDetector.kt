package dev.tomng.alarme.core.sensors.squat

/**
 * Pure cover rule for the squat mission. Proximity is the primary signal — a real "near" event,
 * available on almost all phones; the light sensor (less than one lux) is the fallback. When both
 * exist, the latest proximity reading wins and light is ignored until proximity has spoken.
 */
class CoverDetector(
    private val lightSensorPresent: Boolean = false,
    private val proximitySensorPresent: Boolean = false,
) {
    /** Tri-state so the UI can distinguish "not covered" from "no reading yet". */
    enum class Covered {
        YES,
        NO,
        PENDING,
    }

    val isAvailable: Boolean = lightSensorPresent || proximitySensorPresent

    private var seenProximity = false

    /** Light sample in lux; ignored when a proximity sensor exists. */
    fun onLightSample(lux: Float): Covered {
        if (proximitySensorPresent && seenProximity) return lastCovered
        if (proximitySensorPresent && !seenProximity) return Covered.PENDING
        if (!lightSensorPresent) return Covered.PENDING
        lastCovered = if (lux < COVERED_LUX) Covered.YES else Covered.NO
        return lastCovered
    }

    /** Proximity sample in centimetres; 0 means near. */
    fun onProximitySample(cm: Float): Covered {
        if (!proximitySensorPresent) return onLightSample(cm)
        seenProximity = true
        lastCovered = if (cm < NEAR_CM) Covered.YES else Covered.NO
        return lastCovered
    }

    private var lastCovered: Covered = Covered.PENDING

    companion object {
        const val COVERED_LUX = 1.0f
        const val NEAR_CM = 1f
    }
}
