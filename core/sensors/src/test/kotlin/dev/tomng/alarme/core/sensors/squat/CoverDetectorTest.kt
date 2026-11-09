package dev.tomng.alarme.core.sensors.squat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The cover rule: proximity is the primary signal (near = covered); devices without one fall back
 * to the light sensor, where less than one lux counts as covered.
 */
class CoverDetectorTest {

    @Test
    fun `light below one lux is covered`() {
        val detector = CoverDetector(lightSensorPresent = true)
        assertThat(detector.onLightSample(lux = 0.5f)).isEqualTo(CoverDetector.Covered.YES)
        assertThat(detector.onLightSample(lux = 0.0f)).isEqualTo(CoverDetector.Covered.YES)
        assertThat(detector.onLightSample(lux = 1.0f)).isEqualTo(CoverDetector.Covered.NO)
        assertThat(detector.onLightSample(lux = 40f)).isEqualTo(CoverDetector.Covered.NO)
    }

    @Test
    fun `proximity near is covered when present`() {
        val detector = CoverDetector(proximitySensorPresent = true)
        assertThat(detector.onProximitySample(cm = 0f)).isEqualTo(CoverDetector.Covered.YES)
        assertThat(detector.onProximitySample(cm = 1f)).isEqualTo(CoverDetector.Covered.NO)
        assertThat(detector.onProximitySample(cm = 5f)).isEqualTo(CoverDetector.Covered.NO)
    }

    @Test
    fun `proximity wins over light when both exist`() {
        val detector = CoverDetector(lightSensorPresent = true, proximitySensorPresent = true)
        detector.onLightSample(lux = 40f) // light says uncovered
        assertThat(detector.onProximitySample(cm = 0f)).isEqualTo(CoverDetector.Covered.YES)
        detector.onLightSample(lux = 0.5f) // light says covered now
        assertThat(detector.onProximitySample(cm = 5f)).isEqualTo(CoverDetector.Covered.NO)
    }

    @Test
    fun `light is ignored while proximity is present but has not fired yet`() {
        val detector = CoverDetector(lightSensorPresent = true, proximitySensorPresent = true)
        assertThat(detector.onLightSample(lux = 0.5f)).isEqualTo(CoverDetector.Covered.PENDING)
        assertThat(detector.onProximitySample(cm = 1f)).isEqualTo(CoverDetector.Covered.NO)
    }

    @Test
    fun `no sensor at all means unavailable`() {
        val detector = CoverDetector(lightSensorPresent = false, proximitySensorPresent = false)
        assertThat(detector.isAvailable).isFalse()
    }

    @Test
    fun `either sensor alone is enough`() {
        assertThat(CoverDetector(lightSensorPresent = true).isAvailable).isTrue()
        assertThat(CoverDetector(proximitySensorPresent = true).isAvailable).isTrue()
    }
}
