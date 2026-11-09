package dev.tomng.alarme.feature.missions.engine

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.mission.ImageRecognitionObject
import org.junit.Test

/**
 * The image-recognition rule: one of the configured objects must be recognised inside the target
 * box with confidence >= 0.7. Labels are matched by name (case-insensitive) — label indices shift
 * between model versions, names do not.
 */
class ImageRecognitionVerifierTest {

    private fun label(text: String, confidence: Float) =
        ImageRecognitionVerifier.RecognizedLabel(text = text, confidence = confidence)

    @Test
    fun `a configured object at or above the threshold matches`() {
        val match =
            ImageRecognitionVerifier.match(
                labels = listOf(label("Cat", 0.7f), label("Couch", 0.9f)),
                targets = setOf(ImageRecognitionObject.COUCH),
            )
        assertThat(match).isEqualTo(ImageRecognitionObject.COUCH)
    }

    @Test
    fun `below the threshold does not match`() {
        val match =
            ImageRecognitionVerifier.match(
                labels = listOf(label("Cat", 0.69f)),
                targets = setOf(ImageRecognitionObject.CAT),
            )
        assertThat(match).isNull()
    }

    @Test
    fun `the label name match ignores case`() {
        val match =
            ImageRecognitionVerifier.match(
                labels = listOf(label("cAt", 0.8f)),
                targets = setOf(ImageRecognitionObject.CAT),
            )
        assertThat(match).isEqualTo(ImageRecognitionObject.CAT)
    }

    @Test
    fun `an unconfigured object never matches`() {
        val match =
            ImageRecognitionVerifier.match(
                labels = listOf(label("Dog", 0.99f)),
                targets = setOf(ImageRecognitionObject.CAT),
            )
        assertThat(match).isNull()
    }

    @Test
    fun `either of two configured objects matches`() {
        val labels = listOf(label("Dog", 0.91f))
        assertThat(
                ImageRecognitionVerifier.match(
                    labels,
                    setOf(ImageRecognitionObject.CAT, ImageRecognitionObject.DOG),
                ),
            )
            .isEqualTo(ImageRecognitionObject.DOG)
    }

    @Test
    fun `no targets configured never matches`() {
        assertThat(ImageRecognitionVerifier.match(listOf(label("Cat", 0.99f)), emptySet())).isNull()
    }

    @Test
    fun `every curated object has a non-blank label name`() {
        ImageRecognitionObject.entries.forEach { object_ ->
            assertThat(object_.mlKitLabel).isNotEmpty()
        }
    }
}
