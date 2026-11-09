package dev.tomng.alarme.core.audio.volume

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Volume math: `ceil(maxVolume * volumeRatio)` clamped to `[0, max]`. */
class TargetVolumeTest {

    @Test
    fun `exact halves round up`() {
        assertThat(targetVolume(max = 7, ratio = 0.5f)).isEqualTo(4)
    }

    @Test
    fun `full ratio is max`() {
        assertThat(targetVolume(max = 7, ratio = 1f)).isEqualTo(7)
    }

    @Test
    fun `zero ratio is zero`() {
        assertThat(targetVolume(max = 7, ratio = 0f)).isEqualTo(0)
    }

    @Test
    fun `tiny ratio is at least one step`() {
        assertThat(targetVolume(max = 15, ratio = 0.01f)).isEqualTo(1)
    }

    @Test
    fun `overloud ratio clamps to max`() {
        assertThat(targetVolume(max = 7, ratio = 1.2f)).isEqualTo(7)
    }

    @Test
    fun `negative ratio clamps to zero`() {
        assertThat(targetVolume(max = 7, ratio = -0.5f)).isEqualTo(0)
    }
}

/** The crescendo floor table: the floor depends on the configured volume ratio. */
class CrescendoFloorTest {

    @Test
    fun table() {
        assertThat(crescendoFloor(0.09f)).isEqualTo(0.3f)
        assertThat(crescendoFloor(0.1f)).isEqualTo(0.12f)
        assertThat(crescendoFloor(0.29f)).isEqualTo(0.12f)
        assertThat(crescendoFloor(0.3f)).isEqualTo(0.08f)
        assertThat(crescendoFloor(0.49f)).isEqualTo(0.08f)
        assertThat(crescendoFloor(0.5f)).isEqualTo(0.065f)
        assertThat(crescendoFloor(0.69f)).isEqualTo(0.065f)
        assertThat(crescendoFloor(0.7f)).isEqualTo(0.05f)
        assertThat(crescendoFloor(1f)).isEqualTo(0.05f)
    }
}

/**
 * The ramp: every second, `clamp((t/d)^2, floor, 1)`; a non-positive duration jumps straight to
 * full volume.
 */
class CrescendoGainTest {

    @Test
    fun `starts at the floor`() {
        assertThat(crescendoGain(elapsedSeconds = 0, durationSeconds = 30, floor = 0.05f))
            .isEqualTo(0.05f)
    }

    @Test
    fun `quadratic midpoint`() {
        assertThat(crescendoGain(elapsedSeconds = 15, durationSeconds = 30, floor = 0.05f))
            .isWithin(1e-6f)
            .of(0.25f)
    }

    @Test
    fun `reaches full volume at the end`() {
        assertThat(crescendoGain(elapsedSeconds = 30, durationSeconds = 30, floor = 0.05f))
            .isEqualTo(1f)
    }

    @Test
    fun `early gain never dips below floor`() {
        assertThat(crescendoGain(elapsedSeconds = 2, durationSeconds = 600, floor = 0.12f))
            .isEqualTo(0.12f)
    }

    @Test
    fun `nonpositive duration jumps to full`() {
        assertThat(crescendoGain(elapsedSeconds = 0, durationSeconds = 0, floor = 0.3f))
            .isEqualTo(1f)
    }

    @Test
    fun `past the end stays full`() {
        assertThat(crescendoGain(elapsedSeconds = 90, durationSeconds = 30, floor = 0.05f))
            .isEqualTo(1f)
    }
}
