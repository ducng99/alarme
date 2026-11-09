package dev.tomng.alarme.feature.missions.engine

import dev.tomng.alarme.core.model.mission.TokenMode
import java.util.Locale

/**
 * The spoken-word matcher. At least [PASS_RATIO] of the target's tokens must appear in what the
 * recogniser heard — a multiset intersection, so repetitions in the heard text cannot inflate the
 * score. The 25 % bar is deliberately lenient: character overlap in Korean/Japanese is easy to hit
 * accidentally, and the mission should "just work" for sleepy people.
 */
object WordBeatMatcher {

    const val PASS_RATIO = 0.25f

    fun tokenize(text: String, mode: TokenMode): List<String> {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || ERROR_TEXT.matches(trimmed)) return emptyList()
        val lowered = trimmed.lowercase(Locale.ROOT)
        return when (mode) {
            TokenMode.CHARACTER -> lowered.filterNot { it.isWhitespace() }.map { it.toString() }

            TokenMode.WORD -> {
                val cleaned = lowered.map { if (it in WORD_CHARS) it else ' ' }.joinToString("")
                val raw = cleaned.split(' ').filter { it.isNotEmpty() }
                rejoinHyphenFragments(raw)
            }
        }
    }

    /** "foo", "-", "bar" fragments (a stray hyphen with spaces around it) become "foobar". */
    private fun rejoinHyphenFragments(tokens: List<String>): List<String> {
        val joined = mutableListOf<String>()
        var pending: String? = null
        for (token in tokens) {
            if (token == "-") {
                pending = joined.removeLastOrNull() ?: ""
                continue
            }
            if (pending != null) {
                joined += pending + token
                pending = null
            } else {
                joined += token
            }
        }
        pending?.let { joined += it }
        return joined.filterNot { it == "-" }
    }

    /** The fraction of target tokens that were heard, in 0..1. */
    fun score(target: String, heard: String, mode: TokenMode): Float {
        val targetTokens = tokenize(target, mode)
        if (targetTokens.isEmpty()) return 0f
        val heardCounts = tokenize(heard, mode).groupingBy { it }.eachCount().toMutableMap()
        val common = targetTokens.count { token ->
            val left = heardCounts[token] ?: 0
            if (left > 0) {
                heardCounts[token] = left - 1
                true
            } else {
                false
            }
        }
        return common.toFloat() / targetTokens.size
    }

    fun passes(target: String, heard: String, mode: TokenMode): Boolean =
        score(target, heard, mode) >= PASS_RATIO

    private val ERROR_TEXT = Regex("ERROR_[A-Z0-9_]+")
    private const val WORD_CHARS = "abcdefghijklmnopqrstuvwxyz'- "
}
