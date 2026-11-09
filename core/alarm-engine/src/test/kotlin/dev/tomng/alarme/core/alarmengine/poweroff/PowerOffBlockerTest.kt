package dev.tomng.alarme.core.alarmengine.poweroff

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PowerOffBlockerTest {

    private val fighting =
        BlockerInputs(
            enabled = true,
            dismissed = false,
            screenKeeperPaused = false,
            windowFocused = false,
        )

    @Test
    fun `a focused ring window never blocks`() {
        assertThat(PowerOffBlocker.decide(fighting.copy(windowFocused = true)))
            .isEqualTo(BlockerDecision.Idle)
    }

    @Test
    fun `a disabled toggle never blocks`() {
        assertThat(PowerOffBlocker.decide(fighting.copy(enabled = false)))
            .isEqualTo(BlockerDecision.Idle)
    }

    @Test
    fun `a dismissed alarm is not fought for`() {
        assertThat(PowerOffBlocker.decide(fighting.copy(dismissed = true)))
            .isEqualTo(BlockerDecision.Idle)
    }

    @Test
    fun `a paused screen keeper is not fought for`() {
        assertThat(PowerOffBlocker.decide(fighting.copy(screenKeeperPaused = true)))
            .isEqualTo(BlockerDecision.Idle)
    }

    @Test
    fun `our own window in front is not a cheat attempt`() {
        assertThat(
                PowerOffBlocker.shouldBlockForeground(
                    "dev.tomng.alarme.debug",
                    "dev.tomng.alarme.debug",
                ),
            )
            .isFalse()
    }

    @Test
    fun `another app in front is a cheat attempt`() {
        assertThat(
                PowerOffBlocker.shouldBlockForeground(
                    "com.android.systemui",
                    "dev.tomng.alarme.debug",
                ),
            )
            .isTrue()
    }

    @Test
    fun `an unknown foreground is not fought`() {
        assertThat(PowerOffBlocker.shouldBlockForeground(null, "dev.tomng.alarme.debug")).isFalse()
    }

    @Test
    fun `focus lost to another app while ringing blocks`() {
        assertThat(PowerOffBlocker.decide(fighting)).isEqualTo(BlockerDecision.Block)
    }

    @Test
    fun `the block sequence is back then home three times`() {
        val steps = PowerOffBlocker.blockSequence()

        assertThat(steps.map { it.action })
            .containsExactly(
                GlobalAction.BACK,
                GlobalAction.HOME,
                GlobalAction.BACK,
                GlobalAction.HOME,
                GlobalAction.BACK,
                GlobalAction.HOME,
            )
            .inOrder()
    }

    @Test
    fun `the block sequence paces itself`() {
        val steps = PowerOffBlocker.blockSequence()

        assertThat(steps.dropLast(1).map { it.pauseAfterMillis })
            .containsExactly(100L, 100L, 100L, 100L, 100L)
        assertThat(steps.last().pauseAfterMillis).isEqualTo(0L)
    }
}
