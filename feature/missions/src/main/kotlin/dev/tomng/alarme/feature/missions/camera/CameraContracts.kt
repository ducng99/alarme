package dev.tomng.alarme.feature.missions.camera

import dev.tomng.alarme.feature.missions.engine.ImageRecognitionVerifier
import kotlinx.coroutines.flow.StateFlow

/**
 * The camera missions' seams. Screens depend on these, production wires ML Kit/CameraX, tests push
 * values through fakes.
 */
interface BarcodeScanner : AutoCloseable {
    /** The most recently scanned barcode value, or null before the first scan. */
    val scannedValue: StateFlow<String?>
}

interface LabelScanner : AutoCloseable {
    /** Labels recognised in the latest analysed frame (inside the target box). */
    val labels: StateFlow<List<ImageRecognitionVerifier.RecognizedLabel>>
}

interface PhotoCapturer : AutoCloseable {
    /** Captures one frame to internal storage; true on success. */
    suspend fun capture(): Boolean
}
