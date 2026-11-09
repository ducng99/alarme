package dev.tomng.alarme.feature.missions.camera

import android.content.Context
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabel
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import dev.tomng.alarme.feature.missions.engine.ImageRecognitionVerifier
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

/** Barcode scanning through ML Kit: every frame's first barcode updates [scannedValue]. */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
class MlKitBarcodeScanner(
    controller: MissionCameraController,
) : BarcodeScanner {
    private val _scannedValue = MutableStateFlow<String?>(null)
    override val scannedValue: StateFlow<String?> = _scannedValue

    private val client =
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_DATA_MATRIX,
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_AZTEC,
                )
                .build(),
        )

    init {
        controller.setAnalyzer { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return@setAnalyzer
            }
            val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            client
                .process(input)
                .addOnSuccessListener { barcodes ->
                    barcodes.firstOrNull()?.rawValue?.let { _scannedValue.value = it }
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    override fun close() {
        client.close()
    }
}

/**
 * On-device image labelling with a centred crop: only the middle of the frame is analysed, so "the
 * object is somewhere in the room" does not count.
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
class MlKitLabelScanner(
    controller: MissionCameraController,
    private val cropFraction: Float = 0.5f,
) : LabelScanner {
    private val _labels =
        MutableStateFlow<List<ImageRecognitionVerifier.RecognizedLabel>>(emptyList())
    override val labels: StateFlow<List<ImageRecognitionVerifier.RecognizedLabel>> = _labels

    private val labeler: ImageLabeler =
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(ImageRecognitionVerifier.MIN_CONFIDENCE)
                .build(),
        )

    init {
        controller.setAnalyzer { imageProxy ->
            val mediaImage = imageProxy.image
            if (mediaImage == null) {
                imageProxy.close()
                return@setAnalyzer
            }
            // Analyse only a centred crop: an object anywhere in the room must not count.
            val full = imageProxy.cropRect
            val width = (full.width() * cropFraction).toInt().coerceAtLeast(1)
            val height = (full.height() * cropFraction).toInt().coerceAtLeast(1)
            imageProxy.setCropRect(
                android.graphics.Rect(
                    full.centerX() - width / 2,
                    full.centerY() - height / 2,
                    full.centerX() + width / 2,
                    full.centerY() + height / 2,
                ),
            )
            val cropped =
                InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            labeler
                .process(cropped)
                .addOnSuccessListener { results ->
                    _labels.value = results.map { label: ImageLabel ->
                        ImageRecognitionVerifier.RecognizedLabel(
                            text = label.text,
                            confidence = label.confidence,
                        )
                    }
                }
                .addOnCompleteListener { imageProxy.close() }
        }
    }

    override fun close() {
        labeler.close()
    }
}

/** The honour-system photo capture: any frame completes the mission. */
class MissionPhotoCapturer(
    private val controller: MissionCameraController,
) : PhotoCapturer {
    override suspend fun capture(): Boolean = controller.capturePhoto()

    override fun close() = Unit
}
