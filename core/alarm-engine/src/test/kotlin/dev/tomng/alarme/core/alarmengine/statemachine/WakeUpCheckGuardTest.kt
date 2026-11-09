package dev.tomng.alarme.core.alarmengine.statemachine

import com.google.common.truth.Truth.assertThat
import kotlin.time.Duration.Companion.seconds
import org.junit.Test

/** The editor's wake-up-check guard: only the checked alarm is blocked, and only while live. */
class WakeUpCheckGuardTest : RingReducerTestBase() {

    @Test
    fun `a running check blocks editing that alarm`() {
        val state = RingState.WakeUpCheck(alarm(), now)

        assertThat(WakeUpCheckGuard.isEditingBlocked(state, alarmId = 7L)).isTrue()
    }

    @Test
    fun `a pending check blocks editing that alarm`() {
        val state = RingState.WakeUpCheckPending(alarm(), now + 60.seconds)

        assertThat(WakeUpCheckGuard.isEditingBlocked(state, alarmId = 7L)).isTrue()
    }

    @Test
    fun `a check never blocks editing a different alarm`() {
        val state = RingState.WakeUpCheck(alarm(), now)

        assertThat(WakeUpCheckGuard.isEditingBlocked(state, alarmId = 8L)).isFalse()
    }

    @Test
    fun `idle and ringing never block editing`() {
        assertThat(WakeUpCheckGuard.isEditingBlocked(RingState.Idle, alarmId = 7L)).isFalse()
    }
}
