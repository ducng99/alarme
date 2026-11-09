package dev.tomng.alarme.feature.missions.engine

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.mission.TokenMode
import org.junit.Test

/**
 * The spoken-word rule: at least 25 % of the target's tokens must appear in what was recognised —
 * order-insensitive, case-insensitive, punctuation-insensitive. Korean and Japanese tokenise per
 * character, English per word.
 */
class WordBeatMatcherTest {

    @Test
    fun `english word tokenisation keeps only letters apostrophes and hyphens`() {
        assertThat(WordBeatMatcher.tokenize("Hello, World! it's co-op time.", TokenMode.WORD))
            .containsExactly("hello", "world", "it's", "co-op", "time")
            .inOrder()
    }

    @Test
    fun `english hyphen fragments rejoin`() {
        // After stripping punctuation a stray hyphen can split a word: "foo - bar" -> foo, -, bar
        // -> the join rule reassembles the fragments into one word (hyphen dropped).
        assertThat(WordBeatMatcher.tokenize("foo - bar baz", TokenMode.WORD))
            .containsExactly("foobar", "baz")
            .inOrder()
    }

    @Test
    fun `character tokenisation drops whitespace and yields one token per character`() {
        assertThat(WordBeatMatcher.tokenize("안녕 하세요", TokenMode.CHARACTER))
            .containsExactly("안", "녕", "하", "세", "요")
            .inOrder()
        assertThat(WordBeatMatcher.tokenize("おはよう", TokenMode.CHARACTER))
            .containsExactly("お", "は", "よ", "う")
            .inOrder()
    }

    @Test
    fun `empty or error text produces no tokens`() {
        assertThat(WordBeatMatcher.tokenize("", TokenMode.WORD)).isEmpty()
        assertThat(WordBeatMatcher.tokenize("   ", TokenMode.WORD)).isEmpty()
        assertThat(WordBeatMatcher.tokenize("ERROR_NO_MATCH", TokenMode.WORD)).isEmpty()
        assertThat(WordBeatMatcher.tokenize("ERROR_NETWORK_TIMEOUT", TokenMode.CHARACTER)).isEmpty()
    }

    @Test
    fun `tokenisation lowercases with a root locale`() {
        assertThat(WordBeatMatcher.tokenize("GRAVITY", TokenMode.WORD)).containsExactly("gravity")
    }

    @Test
    fun `overlap is a multiset intersection over the target`() {
        // Target has 4 tokens; hearing two of them = 50 %.
        assertThat(
                WordBeatMatcher.score(
                    target = "the quick brown fox",
                    heard = "the fox",
                    mode = TokenMode.WORD,
                ),
            )
            .isEqualTo(0.5f)
    }

    @Test
    fun `repeated heard tokens cannot double count a target token`() {
        assertThat(
                WordBeatMatcher.score(
                    target = "one two three",
                    heard = "one one one one",
                    mode = TokenMode.WORD,
                ),
            )
            .isEqualTo(1f / 3f)
    }

    @Test
    fun `the pass bar is twenty five percent of the target tokens`() {
        // 3 of 10 = 30 % -> pass; 2 of 10 = 20 % -> fail.
        val target = (1..10).joinToString(" ") { "word$it" }
        assertThat(
                WordBeatMatcher.passes(target, "word1 word2 word3", TokenMode.WORD),
            )
            .isTrue()
        assertThat(WordBeatMatcher.passes(target, "word1 word2", TokenMode.WORD)).isFalse()
    }

    @Test
    fun `an empty target never passes`() {
        assertThat(WordBeatMatcher.passes("", "anything", TokenMode.WORD)).isFalse()
        assertThat(WordBeatMatcher.passes("ERROR_RECOGNIZER_BUSY", "anything", TokenMode.WORD))
            .isFalse()
    }

    @Test
    fun `korean character overlap is measured per character`() {
        // Target 6 characters; hearing 3 of them = 50 %.
        assertThat(
                WordBeatMatcher.passes(
                    target = "좋은 아침이에요",
                    heard = "좋 아 침",
                    mode = TokenMode.CHARACTER,
                ),
            )
            .isTrue()
    }

    @Test
    fun `nothing heard fails`() {
        assertThat(WordBeatMatcher.passes("wake up now", "", TokenMode.WORD)).isFalse()
    }
}
