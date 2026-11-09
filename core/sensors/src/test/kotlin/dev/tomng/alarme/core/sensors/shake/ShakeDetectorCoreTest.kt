package dev.tomng.alarme.core.sensors.shake

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The shake rule: one accelerometer sample whose magnitude deviates from 1 g by more than the
 * threshold counts as a shake, unless it lands inside the debounce window after the last one.
 */
class ShakeDetectorCoreTest {

    private val core = ShakeDetectorCore(threshold = 1.3f, debounceMillis = 300)

    @Test
    fun `a hard sample counts as a shake`() {
        // g magnitude 2.31: |2.31 - 1| = 1.31 > 1.3
        val g = 9.80665f
        assertThat(core.onSample(0f, 0f, 2.31f * g, timestampMillis = 1_000)).isTrue()
        assertThat(core.count).isEqualTo(1)
    }

    @Test
    fun `a borderline sample at exactly the threshold does not count`() {
        val g = 9.80665f
        // |2.3 - 1| = 1.3, not strictly greater
        assertThat(core.onSample(0f, 0f, 2.3f * g, timestampMillis = 1_000)).isFalse()
        assertThat(core.count).isEqualTo(0)
    }

    @Test
    fun `resting gravity never counts`() {
        val g = 9.80665f
        assertThat(core.onSample(0f, 0f, g, timestampMillis = 1_000)).isFalse()
        assertThat(core.onSample(0f, 0f, g * 1.1f, timestampMillis = 1_100)).isFalse()
    }

    @Test
    fun `samples inside the debounce window are ignored`() {
        val g = 9.80665f
        assertThat(core.onSample(0f, 0f, 3f * g, timestampMillis = 1_000)).isTrue()
        assertThat(core.onSample(0f, 0f, 3f * g, timestampMillis = 1_100)).isFalse()
        assertThat(core.onSample(0f, 0f, 3f * g, timestampMillis = 1_299)).isFalse()
        assertThat(core.count).isEqualTo(1)
    }

    @Test
    fun `a sample after the debounce window counts again`() {
        val g = 9.80665f
        assertThat(core.onSample(0f, 0f, 3f * g, timestampMillis = 1_000)).isTrue()
        assertThat(core.onSample(0f, 0f, 3f * g, timestampMillis = 1_301)).isTrue()
        assertThat(core.count).isEqualTo(2)
    }

    @Test
    fun `a weak sample does not extend the debounce window`() {
        val g = 9.80665f
        assertThat(core.onSample(0f, 0f, 3f * g, timestampMillis = 1_000)).isTrue()
        assertThat(core.onSample(0f, 0f, g, timestampMillis = 1_050)).isFalse() // weak, ignored
        // 350 ms after the last real shake: outside the window, so this one counts.
        assertThat(core.onSample(0f, 0f, 3f * g, timestampMillis = 1_350)).isTrue()
        assertThat(core.count).isEqualTo(2)
    }

    @Test
    fun `the magnitude uses all three axes`() {
        val g = 9.80665f
        // magnitude of (2, 2, 2) * g-ish: sqrt(12) = 3.46 -> |3.46 - 1| > 1.3
        assertThat(core.onSample(2f * g, 2f * g, 2f * g, timestampMillis = 1_000)).isTrue()
    }

    @Test
    fun `reset zeroes the progress`() {
        val g = 9.80665f
        core.onSample(0f, 0f, 3f * g, timestampMillis = 1_000)
        core.reset()
        assertThat(core.count).isEqualTo(0)
    }
}
