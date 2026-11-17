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

    /** Decodes [frame]; when [cropSidePx] is set only a centered square of that size is scanned. */
    fun decode(frame: LuminanceFrame, cropSidePx: Int? = null): String? {
        val uprightFrame = upright(frame)
        val upright = if (cropSidePx == null) uprightFrame else cropCenter(uprightFrame, cropSidePx)
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

    private fun cropCenter(frame: LuminanceFrame, sidePx: Int): LuminanceFrame {
        val side = sidePx.coerceIn(1, minOf(frame.width, frame.height))
        val left = (frame.width - side) / 2
        val top = (frame.height - side) / 2
        val out = ByteArray(side * side)
        for (y in 0 until side) {
            System.arraycopy(frame.luminance, (top + y) * frame.width + left, out, y * side, side)
        }
        return LuminanceFrame(out, side, side, rowStride = side)
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
        /**
         * Size in image pixels of the preview's centered box, given the FILL_CENTER preview scale.
         */
        fun boxSideInImagePx(
            imageWidth: Int,
            imageHeight: Int,
            viewWidthPx: Int,
            viewHeightPx: Int,
            boxSidePx: Int,
        ): Int {
            val scale =
                maxOf(viewWidthPx.toFloat() / imageWidth, viewHeightPx.toFloat() / imageHeight)
            return (boxSidePx / scale).toInt()
        }

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
