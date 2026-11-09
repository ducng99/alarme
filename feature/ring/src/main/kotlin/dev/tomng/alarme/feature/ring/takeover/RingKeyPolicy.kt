package dev.tomng.alarme.feature.ring.takeover

import android.view.KeyEvent

/** What the ring screen does with a key event. */
enum class KeyVerdict {
    /** Swallow the key; the system must not act on it. */
    CONSUME,

    /** Swallow the key and count it against the mute limit (volume-down). */
    CONSUME_AND_MUTE,

    /** Let the system handle the key normally. */
    PASS,
}

/**
 * The takeover key policy: back can never escape a ringing alarm, and volume keys are swallowed
 * (volume-down additionally reports as a mute attempt) unless the volume-key bypass is active — the
 * bypass lets auxiliary screens offer normal volume control.
 */
object RingKeyPolicy {

    fun onKey(keyCode: Int, volumeKeyBypass: Boolean): KeyVerdict =
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> KeyVerdict.CONSUME
            KeyEvent.KEYCODE_VOLUME_DOWN ->
                if (volumeKeyBypass) KeyVerdict.PASS else KeyVerdict.CONSUME_AND_MUTE
            KeyEvent.KEYCODE_VOLUME_UP ->
                if (volumeKeyBypass) KeyVerdict.PASS else KeyVerdict.CONSUME
            else -> KeyVerdict.PASS
        }
}
