package dev.tomng.alarme.core.sensors.squat

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The squat rule: the light sensor (or proximity) reads "covered", and every cover -> uncover
 * transition is one repetition. Repetitions faster than the plausible floor are cheats: they are
 * not counted and flag the cheat penalty.
 */
class SquatRepCounterTest {

    private val counter = SquatRepCounter(goal = 3, minRepIntervalMillis = 500)

    @Test
    fun `a cover then uncover transition counts one repetition`() {
        counter.onCoverChanged(covered = true, timestampMillis = 1_000)
        counter.onCoverChanged(covered = false, timestampMillis = 1_500)
        assertThat(counter.reps).isEqualTo(1)
        assertThat(counter.done).isFalse()
    }

    @Test
    fun `repeated covered events do not double count`() {
        counter.onCoverChanged(covered = true, timestampMillis = 1_000)
        counter.onCoverChanged(covered = true, timestampMillis = 1_100)
        counter.onCoverChanged(covered = false, timestampMillis = 1_500)
        counter.onCoverChanged(covered = false, timestampMillis = 1_600)
        assertThat(counter.reps).isEqualTo(1)
    }

    @Test
    fun `starting uncovered then covering does not count`() {
        counter.onCoverChanged(covered = false, timestampMillis = 1_000)
        counter.onCoverChanged(covered = true, timestampMillis = 1_200)
        assertThat(counter.reps).isEqualTo(0)
    }

    @Test
    fun `reaching the goal marks done`() {
        repeat(3) { rep ->
            counter.onCoverChanged(covered = true, timestampMillis = 1_000L + rep * 1_000)
            counter.onCoverChanged(covered = false, timestampMillis = 1_500L + rep * 1_000)
        }
        assertThat(counter.reps).isEqualTo(3)
        assertThat(counter.done).isTrue()
    }

    @Test
    fun `an implausibly fast repetition is a cheat and does not count`() {
        counter.onCoverChanged(covered = true, timestampMillis = 1_000)
        counter.onCoverChanged(covered = false, timestampMillis = 1_400)
        assertThat(counter.reps).isEqualTo(0)
        assertThat(counter.lastVerdict).isEqualTo(SquatRepCounter.Verdict.CHEAT)
    }

    @Test
    fun `a cheat does not break later counting`() {
        counter.onCoverChanged(covered = true, timestampMillis = 1_000)
        counter.onCoverChanged(covered = false, timestampMillis = 1_400) // cheat
        counter.onCoverChanged(covered = true, timestampMillis = 2_000)
        counter.onCoverChanged(covered = false, timestampMillis = 2_600)
        assertThat(counter.reps).isEqualTo(1)
        assertThat(counter.lastVerdict).isEqualTo(SquatRepCounter.Verdict.REP)
    }

    @Test
    fun `extra transitions after done are ignored`() {
        repeat(3) { rep ->
            counter.onCoverChanged(covered = true, timestampMillis = 1_000L + rep * 1_000)
            counter.onCoverChanged(covered = false, timestampMillis = 1_500L + rep * 1_000)
        }
        counter.onCoverChanged(covered = true, timestampMillis = 5_000)
        counter.onCoverChanged(covered = false, timestampMillis = 5_600)
        assertThat(counter.reps).isEqualTo(3)
        assertThat(counter.done).isTrue()
    }
}
