package dev.tomng.alarme.feature.missions.camera

import android.content.Context
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The production camera controller: one [LifecycleCameraController] shared by the preview and the
 * analysis pipeline. Each mission installs its analyser; the controller is released with the UI.
 */
class MissionCameraController(context: Context) : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private val controller =
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE)
        }

    val cameraController: LifecycleCameraController
        get() = controller

    fun setAnalyzer(analyzer: ImageAnalysis.Analyzer) {
        controller.setImageAnalysisAnalyzer(executor, analyzer)
    }

    suspend fun capturePhoto(): Boolean = suspendCancellableCoroutine { continuation ->
        controller.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    image.close()
                    if (continuation.isActive) continuation.resume(true)
                }

                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                    if (continuation.isActive) continuation.resume(false)
                }
            },
        )
    }

    override fun close() {
        controller.clearImageAnalysisAnalyzer()
        executor.shutdown()
    }
}

/** Barcode scanning through ZXing: every frame's first barcode updates [scannedValue]. */
class ZxingBarcodeScanner(
    controller: MissionCameraController,
) : BarcodeScanner {
    private val _scannedValue = MutableStateFlow<String?>(null)
    override val scannedValue: StateFlow<String?> = _scannedValue

    private val decoder = ZxingBarcodeDecoder()

    init {
        controller.setAnalyzer { imageProxy ->
            imageProxy.use { image ->
                decoder.decode(image.toLuminanceFrame())?.let { _scannedValue.value = it }
            }
        }
    }

    override fun close() = Unit
}

private fun ImageProxy.toLuminanceFrame(): LuminanceFrame {
    val plane = planes[0]
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return LuminanceFrame(
        luminance = bytes,
        width = width,
        height = height,
        rowStride = plane.rowStride,
        rotationDegrees = imageInfo.rotationDegrees,
    )
}

/** The honour-system photo capture: any frame completes the mission. */
class MissionPhotoCapturer(
    private val controller: MissionCameraController,
) : PhotoCapturer {
    override suspend fun capture(): Boolean = controller.capturePhoto()

    override fun close() = Unit
}
