package dev.tomng.alarme.feature.missions.engine

import com.google.common.truth.Truth.assertThat
import kotlin.random.Random
import org.junit.Test

/**
 * The arithmetic ladder. The value here is the exact difficulty progression and the "no zero, no
 * negative distractors" rule — it keeps the mission answerable at 4 a.m.
 */
class MathMissionEngineTest {

    @Test
    fun `difficulty ladder shapes and operand ranges`() {
        val engine = MathMissionEngine()
        repeat(200) {
            val q = engine.nextQuestion(difficulty = 0)
            assertThat(q.answer).isAtLeast(2)
            assertThat(q.answer).isAtMost(18)
            assertThat(q.text).matches("\\d\\+\\d=")

            val q1 = engine.nextQuestion(difficulty = 1)
            assertThat(q1.answer).isAtLeast(2)
            assertThat(q1.answer).isAtMost(18)

            val q2 = engine.nextQuestion(difficulty = 2)
            assertThat(q2.answer).isAtLeast(20)
            assertThat(q2.answer).isAtMost(198)
            assertThat(q2.text).matches("\\d\\d\\+\\d\\d=")

            val q3 = engine.nextQuestion(difficulty = 3)
            assertThat(q3.answer).isAtLeast(30)
            assertThat(q3.answer).isAtMost(297)
            assertThat(q3.text).matches("\\d\\d\\+\\d\\d\\+\\d\\d=")

            val q4 = engine.nextQuestion(difficulty = 4)
            // (10..99)x(1..9)+(10..99): minimum 10x1+10 = 20
            assertThat(q4.answer).isAtLeast(20)
            assertThat(q4.text).matches("\\(\\d\\d x \\d\\)\\+\\d\\d=")

            val q5 = engine.nextQuestion(difficulty = 5)
            // (10..99)x(10..99)+(100..999): minimum 10x10+100 = 200
            assertThat(q5.answer).isAtLeast(200)
            assertThat(q5.text).matches("\\(\\d\\d x \\d\\d\\)\\+\\d\\d\\d=")

            val q6 = engine.nextQuestion(difficulty = 6)
            // (100..999)x(10..99)+(1000..9999): minimum 100x10+1000 = 2000
            assertThat(q6.answer).isAtLeast(2000)
            assertThat(q6.text).matches("\\(\\d\\d\\d x \\d\\d\\)\\+\\d\\d\\d\\d=")
        }
    }

    @Test
    fun `answers actually satisfy the shown equation`() {
        val engine = MathMissionEngine()
        repeat(100) { difficulty ->
            val q = engine.nextQuestion(difficulty = difficulty % 7)
            val match = Regex("(\\(|)(\\d+)(?: x (\\d+))?\\)?\\+?").findAll(q.text)
            assertThat(match.any()).isTrue()
            // Re-evaluate from the text: strip the trailing '=' and 'x' -> '*'.
            val expr = q.text.removeSuffix("=").replace(" x ", "*")
            val evaluated = evaluate(expr)
            assertThat(evaluated).isEqualTo(q.answer)
        }
    }

    @Test
    fun `difficulty above the ladder falls back to level one`() {
        val engine = MathMissionEngine()
        val q = engine.nextQuestion(difficulty = 9)
        assertThat(q.answer).isAtLeast(2)
        assertThat(q.answer).isAtMost(18)
    }

    @Test
    fun `difficulty zero is multiple choice with sane options`() {
        val engine = MathMissionEngine()
        repeat(100) {
            val q = engine.nextQuestion(difficulty = 0)
            val options = q.options
            assertThat(options).isNotNull()
            assertThat(options!!.size).isAnyOf(3, 4)
            assertThat(options).containsNoDuplicates()
            assertThat(options).contains(q.answer)
            options.forEach { assertThat(it).isAtLeast(1L) }
        }
    }

    @Test
    fun `multiple choice options stay in a plausible band`() {
        val engine = MathMissionEngine()
        repeat(100) {
            val q = engine.nextQuestion(difficulty = 0)
            q.options!!.forEach { option ->
                // Max answer is 18 (9+9); the largest family distractor is 9x9. Anything else is
                // a near miss around the answer.
                assertThat(option).isAtLeast(1L)
                assertThat(option).isAtMost(81L)
            }
        }
    }

    @Test
    fun `difficulty one and up offer no options`() {
        val engine = MathMissionEngine()
        assertThat(engine.nextQuestion(difficulty = 1).options).isNull()
    }

    @Test
    fun `verification accepts the answer and rejects anything else`() {
        val engine = MathMissionEngine()
        val q = engine.nextQuestion(difficulty = 2)
        assertThat(engine.check(q, q.answer)).isTrue()
        assertThat(engine.check(q, q.answer + 1)).isFalse()
        assertThat(engine.check(q, q.answer - 1)).isFalse()
    }

    @Test
    fun `deterministic with a seeded random`() {
        val a = MathMissionEngine(Random(42))
        val b = MathMissionEngine(Random(42))
        repeat(10) { assertThat(a.nextQuestion(4)).isEqualTo(b.nextQuestion(4)) }
    }

    /** Tiny expression evaluator for "a+b", "a+b+c" and "(a*b)+c". */
    private fun evaluate(expr: String): Long {
        val sumParts = expr.split("+")
        var sum = 0L
        for (part in sumParts) {
            sum +=
                if (part.startsWith("(")) {
                    val mul = part.removeSurrounding("(", ")").split("*")
                    mul[0].toLong() * mul[1].toLong()
                } else {
                    part.toLong()
                }
        }
        return sum
    }
}
