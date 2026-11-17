package dev.tomng.alarme.feature.missions.camera

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer

/**
 * A greyscale camera frame: the Y plane of a YUV image. [rotationDegrees] is the clockwise rotation
 * that makes the frame upright, as reported by CameraX.
 */
class LuminanceFrame(
    val luminance: ByteArray,
    val width: Int,
    val height: Int,
    val rowStride: Int,
    val rotationDegrees: Int = 0,
)

/** Pure-JVM barcode decoding with ZXing; returns the raw value of the first barcode found. */
class ZxingBarcodeDecoder {
    private val reader =
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to FORMATS,
                    DecodeHintType.TRY_HARDER to true,
                ),
            )
        }

    fun decode(frame: LuminanceFrame): String? {
        val upright = upright(frame)
        // 1D barcodes are read row by row, so a barcode held sideways needs a second, rotated pass.
        return decodeUpright(upright) ?: decodeUpright(rotateClockwise(upright))
    }

    private fun decodeUpright(frame: LuminanceFrame): String? {
        val source =
            PlanarYUVLuminanceSource(
                frame.luminance,
                frame.width,
                frame.height,
                0,
                0,
                frame.width,
                frame.height,
                false,
            )
        return try {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
        } catch (_: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }

    private fun upright(frame: LuminanceFrame): LuminanceFrame {
        val packed = packed(frame)
        return when (frame.rotationDegrees) {
            90 -> rotateClockwise(packed)
            180 -> rotateClockwise(rotateClockwise(packed))
            270 -> rotateCounterClockwise(packed)
            else -> packed
        }
    }

    private fun packed(frame: LuminanceFrame): LuminanceFrame {
        if (frame.rowStride == frame.width) return frame
        val out = ByteArray(frame.width * frame.height)
        for (y in 0 until frame.height) {
            System.arraycopy(
                frame.luminance,
                y * frame.rowStride,
                out,
                y * frame.width,
                frame.width,
            )
        }
        return LuminanceFrame(out, frame.width, frame.height, rowStride = frame.width)
    }

    private fun rotateClockwise(frame: LuminanceFrame): LuminanceFrame {
        val w = frame.width
        val h = frame.height
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                out[x * h + (h - 1 - y)] = frame.luminance[y * w + x]
            }
        }
        return LuminanceFrame(out, width = h, height = w, rowStride = h)
    }

    private fun rotateCounterClockwise(frame: LuminanceFrame): LuminanceFrame {
        val w = frame.width
        val h = frame.height
        val out = ByteArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                out[(w - 1 - x) * h + y] = frame.luminance[y * w + x]
            }
        }
        return LuminanceFrame(out, width = h, height = w, rowStride = h)
    }

    companion object {
        val FORMATS: List<BarcodeFormat> =
            listOf(
                BarcodeFormat.QR_CODE,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.DATA_MATRIX,
                BarcodeFormat.PDF_417,
                BarcodeFormat.AZTEC,
            )
    }
}
