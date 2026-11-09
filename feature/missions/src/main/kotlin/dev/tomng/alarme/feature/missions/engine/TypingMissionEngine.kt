package dev.tomng.alarme.feature.missions.engine

import kotlin.random.Random

/**
 * The typing mission. Difficulty is the typing, not the matching: compare the input prefix against
 * the target character-by-character, require exact equality (after quote normalisation) to submit,
 * and emit a timer reset on every keystroke so the timeout never fires mid-sentence.
 */
object TypingMissionEngine {

    data class TypingProgress(
        val matchedPrefix: String,
        val remaining: String,
        val complete: Boolean,
    )

    /** The configured sentences, repeated in shuffled batches until [rounds] exist. */
    fun buildRotation(sentences: List<String>, rounds: Int, random: Random): List<String> {
        if (sentences.isEmpty() || rounds <= 0) return emptyList()
        val rotation = mutableListOf<String>()
        while (rotation.size < rounds) {
            rotation += sentences.shuffled(random)
        }
        return rotation.take(rounds)
    }

    /** Typographic quotes become their ASCII equivalents on both the target and the input. */
    fun normalise(text: String): String =
        text
            .replace('\u2018', '\'') // '
            .replace('\u2019', '\'') // '
            .replace('\u201C', '"') // "
            .replace('\u201D', '"') // "

    fun progress(target: String, input: String): TypingProgress {
        val normalisedTarget = normalise(target)
        val normalisedInput = normalise(input)
        val prefixLength =
            normalisedInput
                .commonPrefixWith(normalisedTarget)
                .length
                .coerceAtMost(normalisedInput.length)
        return TypingProgress(
            matchedPrefix = normalisedInput.substring(0, prefixLength),
            remaining = normalisedTarget.substring(prefixLength),
            complete = normalisedInput.isNotEmpty() && normalisedInput == normalisedTarget,
        )
    }
}
