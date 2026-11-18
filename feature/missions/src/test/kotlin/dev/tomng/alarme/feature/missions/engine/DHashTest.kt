package dev.tomng.alarme.feature.missions.engine

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

class DHashTest {

    private fun image(width: Int, height: Int, luma: (x: Int, y: Int) -> Int): IntArray =
        IntArray(width * height) { i -> luma(i % width, i / width).coerceIn(0, 255) }

    private fun scene(width: Int, height: Int): IntArray =
        image(width, height) { x, y ->
            val u = x * 100 / width
            val v = y * 100 / height
            when {
                u in 20..45 && v in 10..60 -> 220
                u in 55..90 && v in 40..80 -> 30
                else -> 90 + u
            }
        }

    @Test
    fun `a brightness shift keeps the hash`() {
        val base = scene(120, 120)
        val brighter = IntArray(base.size) { (base[it] + 20).coerceAtMost(255) }

        assertThat(DHash.distance(DHash.compute(base, 120, 120), DHash.compute(brighter, 120, 120)))
            .isAtMost(2)
    }

    @Test
    fun `the same scene at another resolution still matches`() {
        val small = DHash.compute(scene(90, 90), 90, 90)
        val large = DHash.compute(scene(360, 360), 360, 360)

        assertThat(DHash.matches(small, large)).isTrue()
    }

    @Test
    fun `a little sensor noise still matches`() {
        val random = Random(7)
        val base = scene(200, 200)
        val noisy = IntArray(base.size) { (base[it] + random.nextInt(-12, 13)).coerceIn(0, 255) }

        assertThat(DHash.matches(DHash.compute(base, 200, 200), DHash.compute(noisy, 200, 200)))
            .isTrue()
    }

    @Test
    fun `only the centered square is hashed so extra width on the sides is ignored`() {
        val square = scene(100, 100)
        val wide =
            image(160, 100) { x, y -> if (x in 30 until 130) square[y * 100 + x - 30] else 0 }

        assertThat(DHash.compute(wide, 160, 100)).isEqualTo(DHash.compute(square, 100, 100))
    }

    @Test
    fun `a mirrored scene does not match`() {
        val base = scene(120, 120)
        val mirrored = image(120, 120) { x, y -> base[y * 120 + (119 - x)] }

        assertThat(DHash.matches(DHash.compute(base, 120, 120), DHash.compute(mirrored, 120, 120)))
            .isFalse()
    }

    @Test
    fun `an unrelated random image does not match`() {
        val random = Random(42)
        val noise = IntArray(120 * 120) { random.nextInt(256) }

        assertThat(
                DHash.matches(
                    DHash.compute(scene(120, 120), 120, 120),
                    DHash.compute(noise, 120, 120),
                ),
            )
            .isFalse()
    }

    @Test
    fun `left-brighter pairs set bits and a flat image sets none`() {
        val flat = image(90, 90) { _, _ -> 128 }
        val fallingRamp = image(90, 90) { x, _ -> 255 - x * 2 }

        assertThat(DHash.compute(flat, 90, 90)).isEqualTo(0L)
        assertThat(DHash.compute(fallingRamp, 90, 90)).isEqualTo(-1L)
    }

    @Test
    fun `distance counts differing bits`() {
        assertThat(DHash.distance(0L, 0L)).isEqualTo(0)
        assertThat(DHash.distance(0L, -1L)).isEqualTo(64)
        assertThat(DHash.distance(0b1011L, 0b0001L)).isEqualTo(2)
    }

    @Test
    fun `matching uses the threshold inclusively`() {
        val atThreshold = (1L shl DHash.MATCH_THRESHOLD) - 1
        val beyond = (1L shl (DHash.MATCH_THRESHOLD + 1)) - 1

        assertThat(DHash.matches(0L, atThreshold)).isTrue()
        assertThat(DHash.matches(0L, beyond)).isFalse()
    }
}
