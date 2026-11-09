package dev.tomng.alarme.feature.missions.engine

import kotlin.random.Random

/**
 * Generates the arithmetic questions. The ladder keeps single-digit sums at the easy end and
 * four-digit products at the hard end; difficulty 0 is multiple choice so it can be solved half
 * asleep, everything above is typed on a keypad.
 */
class MathMissionEngine(private val random: Random = Random.Default) {

    /** One question: the shown text, the answer, and (difficulty 0 only) the choices. */
    data class MathQuestion(
        val text: String,
        val answer: Long,
        val options: List<Long>? = null,
    )

    fun nextQuestion(difficulty: Int): MathQuestion {
        val level =
            when {
                difficulty < MIN_DIFFICULTY -> MIN_DIFFICULTY
                difficulty > MAX_DIFFICULTY -> FALLBACK_DIFFICULTY
                else -> difficulty
            }
        return when (level) {
            0 -> sum2(DIGITS_1, multipleChoice = true)
            1 -> sum2(DIGITS_1, multipleChoice = false)
            2 -> sum2(digits(2), multipleChoice = false)
            3 -> sum3(digits(2))
            4 -> productPlus(hi = digits(2), lo = digits(1), plus = digits(2))
            5 -> productPlus(hi = digits(2), lo = digits(2), plus = digits(3))
            else -> productPlus(hi = digits(3), lo = digits(2), plus = digits(4))
        }
    }

    fun check(question: MathQuestion, input: Long): Boolean = input == question.answer

    private fun sum2(range: IntRange, multipleChoice: Boolean): MathQuestion {
        val a = range.random(random)
        val b = range.random(random)
        val answer = (a + b).toLong()
        val text = "$a+$b="
        return if (multipleChoice) {
            MathQuestion(text, answer, optionsFor(a, b, answer))
        } else {
            MathQuestion(text, answer)
        }
    }

    private fun sum3(range: IntRange): MathQuestion {
        val a = range.random(random)
        val b = range.random(random)
        val c = range.random(random)
        return MathQuestion("$a+$b+$c=", (a + b + c).toLong())
    }

    private fun productPlus(hi: IntRange, lo: IntRange, plus: IntRange): MathQuestion {
        val a = hi.random(random)
        val b = lo.random(random)
        val c = plus.random(random)
        return MathQuestion("($a x $b)+$c=", (a.toLong() * b + c))
    }

    /**
     * Difficulty 0 choices: half the time four near-miss distractors, otherwise the classic
     * families {|a-b|, a*b, 2a, 2b} filtered to >= 1 and != answer, from which two survive. Either
     * way the answer is included exactly once and everything is shuffled.
     */
    private fun optionsFor(a: Int, b: Int, answer: Long): List<Long> {
        val families: List<Long> =
            listOf(kotlin.math.abs(a - b).toLong(), a.toLong() * b, 2L * a, 2L * b)
                .filter { it >= 1 && it != answer }
                .distinct()
        val picked: MutableSet<Long> =
            (if (random.nextBoolean() || families.size < 2) {
                    emptyList()
                } else {
                    families.shuffled(random).take(2)
                })
                .toMutableSet()
        while (picked.size < 2) {
            picked += nearMisses(answer, count = 4).filter { it >= 1 && it != answer }
        }
        return (picked.take(3) + answer).distinct().shuffled(random)
    }

    private fun nearMisses(answer: Long, count: Int): List<Long> =
        List(count) { offset ->
                answer +
                    random.nextLong(
                        -answerPlusMinus(answer).toLong(),
                        answerPlusMinus(answer).toLong() + 1,
                    )
            }
            .map { it.coerceAtLeast(1L) }
            .distinct()

    private fun answerPlusMinus(answer: Long): Long = (answer / 3).coerceIn(1L, 6L)

    private fun digits(n: Int): IntRange = DIGIT_RANGES.getValue(n)

    companion object {
        const val MIN_DIFFICULTY = 0
        const val MAX_DIFFICULTY = 6
        const val FALLBACK_DIFFICULTY = 1

        private val DIGITS_1 = 1..9
        private val DIGIT_RANGES =
            mapOf(
                0 to DIGITS_1,
                1 to DIGITS_1,
                2 to 10..99,
                3 to 100..999,
                4 to 1000..9999,
            )
    }
}
