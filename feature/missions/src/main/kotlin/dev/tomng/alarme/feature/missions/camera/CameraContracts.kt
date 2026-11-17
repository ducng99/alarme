package dev.tomng.alarme.feature.missions.camera

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
    /** Captures one frame to internal storage; true on success. */
    suspend fun capture(): Boolean
}
