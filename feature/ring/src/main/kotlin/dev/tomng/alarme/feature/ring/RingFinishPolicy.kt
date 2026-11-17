package dev.tomng.alarme.feature.ring

import dev.tomng.alarme.core.alarmengine.statemachine.RingState

enum class FinishDecision {
    STAY,
    IMMEDIATE,
    AFTER_GRACE,
}

object RingFinishPolicy {

    /** The screen lives while the alarm rings and through the 100 s check confirmation. */
    fun keepsRingScreenAlive(state: RingState): Boolean =
        state is RingState.Ringing || state is RingState.WakeUpCheck

    fun decide(isAlive: Boolean, hasShownSession: Boolean): FinishDecision =
        when {
            isAlive -> FinishDecision.STAY
            hasShownSession -> FinishDecision.IMMEDIATE
            else -> FinishDecision.AFTER_GRACE
        }
}
