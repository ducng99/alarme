package dev.tomng.alarme.feature.ring

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.statemachine.RingState
import org.junit.Test

class RingFinishPolicyTest {

    @Test
    fun `ringing session keeps the screen`() {
        val state = RingFinishPolicy.decide(isAlive = true, hasShownSession = false)

        assertThat(state).isEqualTo(FinishDecision.STAY)
    }

    @Test
    fun `ended session that was shown finishes immediately`() {
        val state = RingFinishPolicy.decide(isAlive = false, hasShownSession = true)

        assertThat(state).isEqualTo(FinishDecision.IMMEDIATE)
    }

    @Test
    fun `never shown session waits out the grace`() {
        val state = RingFinishPolicy.decide(isAlive = false, hasShownSession = false)

        assertThat(state).isEqualTo(FinishDecision.AFTER_GRACE)
    }

    @Test
    fun `idle and pending states are not alive`() {
        assertThat(RingFinishPolicy.keepsRingScreenAlive(RingState.Idle)).isFalse()
    }
}
