package dev.tomng.alarme.feature.missions.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

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

    /** Captures one upright frame on the camera executor; null when the capture failed. */
    suspend fun captureBitmap(): Bitmap? = suspendCancellableCoroutine { continuation ->
        controller.takePicture(
            executor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.use { runCatching { it.toUprightBitmap() }.getOrNull() }
                    if (continuation.isActive) continuation.resume(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    if (continuation.isActive) continuation.resume(null)
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

private fun ImageProxy.toUprightBitmap(): Bitmap {
    val bitmap = toBitmap()
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

/** Captures a frame and hashes it for comparison against the reference photo. */
class MissionPhotoCapturer(
    private val controller: MissionCameraController,
) : PhotoCapturer {
    override suspend fun capture(): Long? =
        controller.captureBitmap()?.let { withContext(Dispatchers.Default) { it.dHash() } }

    override fun close() = Unit
}
