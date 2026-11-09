package dev.tomng.alarme.core.audio.device

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** External audio device types, ported from `sk/b` (WIRED, BLUETOOTH, USB, LINE, BLE). */
object ExternalAudioDevices {
    private val EXTERNAL_TYPES =
        setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
        )

    fun isExternalType(type: Int): Boolean = EXTERNAL_TYPES.contains(type)
}

/** Real implementation of the engine's `AudioOutputDetector` seam. */
class ExternalAudioOutputDetector
@Inject
constructor(@ApplicationContext private val context: Context) :
    dev.tomng.alarme.core.alarmengine.ring.AudioOutputDetector {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    override fun hasExternalOutput(): Boolean =
        audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty().any { device ->
            ExternalAudioDevices.isExternalType(device.type)
        }
}
