package dev.tomng.alarme.feature.missions.camera

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

/**
 * The camera missions' seams. Screens depend on these, production wires ZXing/CameraX, tests push
 * values through fakes.
 */
interface BarcodeScanner : AutoCloseable {
    /** The most recently scanned barcode value, or null before the first scan. */
    val scannedValue: StateFlow<String?>

    /**
     * Restricts scanning to a centered square of [boxSidePx] inside a preview of the given size.
     */
    fun setScanArea(viewWidthPx: Int, viewHeightPx: Int, boxSidePx: Int) = Unit
}

interface PhotoCapturer : AutoCloseable {
    /** Captures one frame and returns its dHash, or null when the capture failed. */
    suspend fun capture(): Long?
}

/** A registered reference photo, decoded for display and hashed for comparison. */
class ReferencePhoto(val image: ImageBitmap, val hash: Long)

fun interface ReferencePhotoSource {
    /** Null when the file is missing or unreadable. */
    suspend fun load(path: String): ReferencePhoto?
}
