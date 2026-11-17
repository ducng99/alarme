package dev.tomng.alarme.feature.missions.camera

import android.content.Context

/**
 * Everything a camera mission screen needs: the analyser/capturer seam plus the live preview
 * surface ([controller] is null in headless tests).
 */
data class BarcodeMissionUi(
    val scanner: BarcodeScanner,
    val controller: MissionCameraController?,
) {
    companion object {
        fun default(context: Context): BarcodeMissionUi =
            MissionCameraController(context).let { controller ->
                BarcodeMissionUi(ZxingBarcodeScanner(controller), controller)
            }
    }
}

data class PhotoMissionUi(
    val capturer: PhotoCapturer,
    val controller: MissionCameraController?,
) {
    companion object {
        fun default(context: Context): PhotoMissionUi =
            MissionCameraController(context).let { controller ->
                PhotoMissionUi(MissionPhotoCapturer(controller), controller)
            }
    }
}
