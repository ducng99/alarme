package dev.tomng.alarme.feature.missions.camera

import com.google.common.truth.Truth.assertThat
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import org.junit.Test

class ZxingBarcodeDecoderTest {

    private val decoder = ZxingBarcodeDecoder()

    @Test
    fun `decodes an upright QR code`() {
        val frame = render(BarcodeFormat.QR_CODE, "alarme-qr", 300, 300)

        assertThat(decoder.decode(frame)).isEqualTo("alarme-qr")
    }

    @Test
    fun `decodes an EAN-13 barcode`() {
        val frame = render(BarcodeFormat.EAN_13, "4006381333931", 400, 200)

        assertThat(decoder.decode(frame)).isEqualTo("4006381333931")
    }

    @Test
    fun `decodes a 1D barcode from a sensor frame rotated by 90 degrees`() {
        val upright = render(BarcodeFormat.CODE_128, "ALARME-128", 400, 200)
        val sensor = rotateCounterClockwise(upright)

        assertThat(decoder.decode(sensor.withRotation(90))).isEqualTo("ALARME-128")
    }

    @Test
    fun `decodes a 1D barcode from a sensor frame rotated by 270 degrees`() {
        val upright = render(BarcodeFormat.CODE_39, "ALARME39", 400, 200)
        val sensor = rotateClockwise(upright)

        assertThat(decoder.decode(sensor.withRotation(270))).isEqualTo("ALARME39")
    }

    @Test
    fun `honours a row stride wider than the frame`() {
        val frame = render(BarcodeFormat.DATA_MATRIX, "matrix", 200, 200)
        val padding = 32
        val stride = frame.width + padding
        val padded = ByteArray(stride * frame.height)
        for (y in 0 until frame.height) {
            System.arraycopy(frame.luminance, y * frame.width, padded, y * stride, frame.width)
        }

        val strided = LuminanceFrame(padded, frame.width, frame.height, rowStride = stride)

        assertThat(decoder.decode(strided)).isEqualTo("matrix")
    }

    @Test
    fun `returns null when the frame has no barcode`() {
        val blank = ByteArray(200 * 200) { WHITE }
        val frame = LuminanceFrame(blank, width = 200, height = 200, rowStride = 200)

        assertThat(decoder.decode(frame)).isNull()
    }

    @Test
    fun `supports every mission format`() {
        assertThat(ZxingBarcodeDecoder.FORMATS)
            .containsExactly(
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

    private fun render(
        format: BarcodeFormat,
        value: String,
        width: Int,
        height: Int,
    ): LuminanceFrame {
        val matrix: BitMatrix = MultiFormatWriter().encode(value, format, width, height)
        val pixels = ByteArray(matrix.width * matrix.height)
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                pixels[y * matrix.width + x] = if (matrix[x, y]) BLACK else WHITE
            }
        }
        return LuminanceFrame(pixels, matrix.width, matrix.height, rowStride = matrix.width)
    }

    private fun LuminanceFrame.withRotation(degrees: Int): LuminanceFrame =
        LuminanceFrame(luminance, width, height, rowStride, rotationDegrees = degrees)

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

    private companion object {
        const val BLACK: Byte = 0
        const val WHITE: Byte = -1
    }
}
