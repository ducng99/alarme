package dev.tomng.alarme.feature.ring.takeover

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The takeover key policy: back is left to the back dispatcher, volume keys are swallowed
 * (volume-down additionally counts against the mute limit) unless the bypass is active.
 */
class RingKeyPolicyTest {

    @Test
    fun `back is left to the back dispatcher`() {
        assertThat(RingKeyPolicy.onKey(KeyEvent.KEYCODE_BACK, volumeKeyBypass = false))
            .isEqualTo(KeyVerdict.PASS)
        assertThat(RingKeyPolicy.onKey(KeyEvent.KEYCODE_BACK, volumeKeyBypass = true))
            .isEqualTo(KeyVerdict.PASS)
    }

    @Test
    fun `volume down is consumed and counts as a mute attempt`() {
        assertThat(RingKeyPolicy.onKey(KeyEvent.KEYCODE_VOLUME_DOWN, volumeKeyBypass = false))
            .isEqualTo(KeyVerdict.CONSUME_AND_MUTE)
    }

    @Test
    fun `volume up is consumed silently`() {
        assertThat(RingKeyPolicy.onKey(KeyEvent.KEYCODE_VOLUME_UP, volumeKeyBypass = false))
            .isEqualTo(KeyVerdict.CONSUME)
    }

    @Test
    fun `volume keys pass through when the bypass is active`() {
        assertThat(RingKeyPolicy.onKey(KeyEvent.KEYCODE_VOLUME_DOWN, volumeKeyBypass = true))
            .isEqualTo(KeyVerdict.PASS)
        assertThat(RingKeyPolicy.onKey(KeyEvent.KEYCODE_VOLUME_UP, volumeKeyBypass = true))
            .isEqualTo(KeyVerdict.PASS)
    }

    @Test
    fun `every other key passes through`() {
        assertThat(RingKeyPolicy.onKey(KeyEvent.KEYCODE_HOME, volumeKeyBypass = false))
            .isEqualTo(KeyVerdict.PASS)
        assertThat(RingKeyPolicy.onKey(KeyEvent.KEYCODE_A, volumeKeyBypass = false))
            .isEqualTo(KeyVerdict.PASS)
    }
}
