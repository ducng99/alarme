package dev.tomng.alarme.feature.missions.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.feature.missions.engine.DHash
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReferencePhotoStoreTest {

    private val store = ReferencePhotoStore(ApplicationProvider.getApplicationContext())

    private fun scene(width: Int, height: Int, mirrored: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(90, 120, 150))
        val paint = Paint()
        paint.color = Color.WHITE
        val left = if (mirrored) width * 0.55f else width * 0.15f
        canvas.drawRect(left, height * 0.1f, left + width * 0.3f, height * 0.6f, paint)
        paint.color = Color.BLACK
        val right = if (mirrored) width * 0.1f else width * 0.6f
        canvas.drawRect(right, height * 0.5f, right + width * 0.3f, height * 0.9f, paint)
        return bitmap
    }

    @Test
    fun `a saved reference loads back with a matching hash`() = runTest {
        val original = scene(800, 600)

        val path = store.save(original)!!
        val loaded = store.load(path)!!

        assertThat(File(path).isFile).isTrue()
        assertThat(DHash.matches(loaded.hash, original.dHash())).isTrue()
    }

    @Test
    fun `a large reference is downscaled before it is stored`() = runTest {
        val path = store.save(scene(4000, 3000))!!

        val loaded = store.load(path)!!

        assertThat(maxOf(loaded.image.width, loaded.image.height)).isAtMost(1280)
    }

    @Test
    fun `the same scene at another size matches and a different scene does not`() {
        val reference = scene(1200, 900).dHash()

        assertThat(DHash.matches(reference, scene(640, 480).dHash())).isTrue()
        assertThat(DHash.matches(reference, scene(1200, 900, mirrored = true).dHash())).isFalse()
    }

    @Test
    fun `a missing or blank path loads nothing`() = runTest {
        assertThat(store.load("")).isNull()
        assertThat(store.load("/does/not/exist.jpg")).isNull()
    }
}
