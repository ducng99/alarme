package dev.tomng.alarme.feature.missions.engine

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.mission.TypingSentence
import dev.tomng.alarme.core.model.mission.TypingSentenceType
import kotlin.random.Random
import org.junit.Test

/**
 * The typing mission: the configured sentences repeat in shuffled batches until the round count is
 * reached; the input is compared character-by-character against the target with smart quotes
 * normalised on both sides.
 */
class TypingMissionEngineTest {

    @Test
    fun `sentence rotation repeats and shuffles until rounds are covered`() {
        val sentences = listOf(sentence("alpha"), sentence("beta"))
        val rotation =
            TypingMissionEngine.buildRotation(
                sentences.map { it.sentence },
                rounds = 5,
                random = Random(3),
            )

        assertThat(rotation).hasSize(5)
        assertThat(rotation.toSet()).containsExactly("alpha", "beta")
    }

    @Test
    fun `rotation with one sentence still covers all rounds`() {
        val rotation =
            TypingMissionEngine.buildRotation(listOf("only"), rounds = 3, random = Random(1))
        assertThat(rotation).containsExactly("only", "only", "only").inOrder()
    }

    @Test
    fun `each shuffled batch keeps the sentence order within itself randomised but complete`() {
        val sentences = listOf("a", "b", "c", "d")
        val rotation = TypingMissionEngine.buildRotation(sentences, rounds = 8, random = Random(9))
        assertThat(rotation).hasSize(8)
        // The first four and the second four are each a permutation of the whole set.
        assertThat(rotation.take(4).sorted()).isEqualTo(sentences.sorted())
        assertThat(rotation.drop(4).sorted()).isEqualTo(sentences.sorted())
    }

    @Test
    fun `smart quotes are normalised on both sides`() {
        assertThat(TypingMissionEngine.normalise("don’t say “hi”")).isEqualTo("don't say \"hi\"")
        assertThat(TypingMissionEngine.normalise("plain")).isEqualTo("plain")
        assertThat(TypingMissionEngine.normalise("‘single’ “double”"))
            .isEqualTo("'single' \"double\"")
    }

    @Test
    fun `progress splits input into matched prefix and remainder`() {
        val progress = TypingMissionEngine.progress("hello", "hel")
        assertThat(progress.matchedPrefix).isEqualTo("hel")
        assertThat(progress.remaining).isEqualTo("lo")
        assertThat(progress.complete).isFalse()
    }

    @Test
    fun `a wrong character stops the prefix there`() {
        val progress = TypingMissionEngine.progress("hello", "hex")
        assertThat(progress.matchedPrefix).isEqualTo("he")
        assertThat(progress.remaining).isEqualTo("llo")
        assertThat(progress.complete).isFalse()
    }

    @Test
    fun `exact equality completes the round`() {
        assertThat(TypingMissionEngine.progress("hello", "hello").complete).isTrue()
        assertThat(TypingMissionEngine.progress("hello", "hello ").complete).isFalse()
        assertThat(TypingMissionEngine.progress("hello", "hell").complete).isFalse()
    }

    @Test
    fun `completion is checked after normalisation`() {
        val target = "don’t stop"
        val typed = "don't stop"
        assertThat(TypingMissionEngine.progress(target, typed).complete).isTrue()
    }

    private fun sentence(text: String) =
        TypingSentence(type = TypingSentenceType.SHORT, sentence = text)
}
