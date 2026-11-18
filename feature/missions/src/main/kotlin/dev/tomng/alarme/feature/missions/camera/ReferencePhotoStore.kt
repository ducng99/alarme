package dev.tomng.alarme.feature.missions.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import dev.tomng.alarme.feature.missions.engine.DHash
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val DIRECTORY = "mission_photos"
private const val MAX_SIDE = 1280
private const val DISPLAY_SIDE = 512
private const val HASH_SIDE = 72
private const val JPEG_QUALITY = 90

/**
 * Reference photos live as JPEGs in app-private storage; the mission stores the absolute path.
 * Picked images are decoded with their EXIF rotation applied, downscaled, and re-encoded so the
 * original content URI is never needed again.
 */
class ReferencePhotoStore(context: Context) : ReferencePhotoSource {
    private val appContext = context.applicationContext
    private val directory: File
        get() = File(appContext.filesDir, DIRECTORY).apply { mkdirs() }

    suspend fun import(uri: Uri): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val source = ImageDecoder.createSource(appContext.contentResolver, uri)
                val bitmap =
                    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val scale = scaleFor(info.size.width, info.size.height, MAX_SIDE)
                        if (scale < 1f) {
                            decoder.setTargetSize(
                                (info.size.width * scale).toInt(),
                                (info.size.height * scale).toInt(),
                            )
                        }
                    }
                write(bitmap)
            }
                .getOrNull()
        }

    suspend fun save(bitmap: Bitmap): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val scale = scaleFor(bitmap.width, bitmap.height, MAX_SIDE)
                val scaled =
                    if (scale < 1f) {
                        Bitmap.createScaledBitmap(
                            bitmap,
                            (bitmap.width * scale).toInt(),
                            (bitmap.height * scale).toInt(),
                            true,
                        )
                    } else {
                        bitmap
                    }
                write(scaled)
            }
                .getOrNull()
        }

    override suspend fun load(path: String): ReferencePhoto? =
        withContext(Dispatchers.IO) {
            val file = File(path)
            if (path.isBlank() || !file.isFile) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= DISPLAY_SIDE) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeFile(path, options) ?: return@withContext null
            ReferencePhoto(bitmap.asImageBitmap(), bitmap.dHash())
        }

    private fun write(bitmap: Bitmap): String {
        val file = File(directory, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        return file.absolutePath
    }

    private fun scaleFor(width: Int, height: Int, maxSide: Int): Float =
        maxSide.toFloat() / maxOf(width, height)
}

/** Hashes the centered square of the bitmap, pre-scaled so the 9x8 grid averages evenly. */
fun Bitmap.dHash(): Long {
    val side = minOf(width, height)
    val square = Bitmap.createBitmap(this, (width - side) / 2, (height - side) / 2, side, side)
    val small = Bitmap.createScaledBitmap(square, HASH_SIDE, HASH_SIDE, true)
    val pixels = IntArray(HASH_SIDE * HASH_SIDE)
    small.getPixels(pixels, 0, HASH_SIDE, 0, 0, HASH_SIDE, HASH_SIDE)
    val luma =
        IntArray(pixels.size) { i ->
            val color = pixels[i]
            val red = (color shr 16) and 0xFF
            val green = (color shr 8) and 0xFF
            val blue = color and 0xFF
            (red * 299 + green * 587 + blue * 114) / 1000
        }
    return DHash.compute(luma, HASH_SIDE, HASH_SIDE)
}
