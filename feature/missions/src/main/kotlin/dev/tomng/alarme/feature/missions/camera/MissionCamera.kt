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
    private val confirmer = ReadConfirmer()

    @Volatile private var scanArea: ScanArea? = null

    private class ScanArea(val viewWidthPx: Int, val viewHeightPx: Int, val boxSidePx: Int)

    override fun setScanArea(viewWidthPx: Int, viewHeightPx: Int, boxSidePx: Int) {
        scanArea = ScanArea(viewWidthPx, viewHeightPx, boxSidePx)
    }

    init {
        controller.setAnalyzer { imageProxy ->
            imageProxy.use { image ->
                val frame = image.toLuminanceFrame()
                val read = decoder.decode(frame, cropSideFor(frame))
                confirmer.offer(read)?.let { _scannedValue.value = it }
            }
        }
    }

    private fun cropSideFor(frame: LuminanceFrame): Int? {
        val area = scanArea ?: return null
        val sideways = frame.rotationDegrees == 90 || frame.rotationDegrees == 270
        return ZxingBarcodeDecoder.boxSideInImagePx(
            imageWidth = if (sideways) frame.height else frame.width,
            imageHeight = if (sideways) frame.width else frame.height,
            viewWidthPx = area.viewWidthPx,
            viewHeightPx = area.viewHeightPx,
            boxSidePx = area.boxSidePx,
        )
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
