package dev.tomng.alarme.core.audio.device

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** External-output device types, ported from `sk/b` (`AudioDeviceInfo` type constants). */
class ExternalAudioDevicesTest {

    @Test
    fun `wired and bluetooth and usb types are external`() {
        assertThat(ExternalAudioDevices.isExternalType(3)).isTrue() // TYPE_WIRED_HEADSET
        assertThat(ExternalAudioDevices.isExternalType(4)).isTrue() // TYPE_WIRED_HEADPHONES
        assertThat(ExternalAudioDevices.isExternalType(7)).isTrue() // TYPE_BLUETOOTH_SCO
        assertThat(ExternalAudioDevices.isExternalType(8)).isTrue() // TYPE_BLUETOOTH_A2DP
        assertThat(ExternalAudioDevices.isExternalType(11)).isTrue() // TYPE_USB_DEVICE
        assertThat(ExternalAudioDevices.isExternalType(12)).isTrue() // TYPE_USB_ACCESSORY
        assertThat(ExternalAudioDevices.isExternalType(22)).isTrue() // TYPE_USB_HEADSET
        assertThat(ExternalAudioDevices.isExternalType(23)).isTrue() // TYPE_HEARING_AID
        assertThat(ExternalAudioDevices.isExternalType(26)).isTrue() // TYPE_BLE_HEADSET
        assertThat(ExternalAudioDevices.isExternalType(27)).isTrue() // TYPE_BLE_SPEAKER
    }

    @Test
    fun `built-in outputs are not external`() {
        assertThat(ExternalAudioDevices.isExternalType(5)).isFalse() // TYPE_LINE_ANALOG is NOT
        // external (dock/line outputs are ignored; literal 23 is hearing aids)
        assertThat(ExternalAudioDevices.isExternalType(1)).isFalse() // BUILTIN_SPEAKER
        assertThat(ExternalAudioDevices.isExternalType(2)).isFalse() // BUILTIN_EARPIECE
        assertThat(ExternalAudioDevices.isExternalType(15)).isFalse() // BUILTIN_MIC
        assertThat(ExternalAudioDevices.isExternalType(18)).isFalse() // TELEPHONY
        assertThat(ExternalAudioDevices.isExternalType(0)).isFalse()
        assertThat(ExternalAudioDevices.isExternalType(-1)).isFalse()
    }
}
