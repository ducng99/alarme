package dev.tomng.alarme.core.alarmengine.statemachine

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Receives every effect the reducer emits, in order, synchronously on the submitting thread. */
fun interface RingEffectListener {
    fun onEffect(effect: RingEffect)
}

/**
 * The process-wide live ring session: the delivery service, the ring screen and the effect executor
 * all share one holder, so UI and audio can never disagree about where a ring is.
 *
 * [submit] updates [state] synchronously and notifies listeners before returning — register
 * listeners (the executor) before submitting the first event.
 */
@Singleton
class RingSessionStore @Inject constructor() {

    private val _state = MutableStateFlow<RingState>(RingState.Idle)
    val state: StateFlow<RingState> = _state.asStateFlow()

    /**
     * When true, volume keys behave normally instead of being swallowed by the ring screen (used by
     * volume-test screens).
     */
    @Volatile var volumeKeyBypass: Boolean = false

    private val listeners = mutableListOf<RingEffectListener>()

    fun addEffectListener(listener: RingEffectListener) {
        synchronized(this) { listeners += listener }
    }

    fun removeEffectListener(listener: RingEffectListener) {
        synchronized(this) { listeners -= listener }
    }

    /**
     * Serialised: the UI submits from the main thread while the services submit from
     * [dev.tomng.alarme.core.common.di.ApplicationScope] coroutines, and a lost read-modify-write
     * would drop a dismiss while the ring keeps playing.
     */
    fun submit(event: RingEvent) {
        synchronized(this) {
            val transition = RingStateMachine.reduce(_state.value, event)
            _state.value = transition.state
            transition.effects.forEach { effect ->
                listeners.toList().forEach { it.onEffect(effect) }
            }
        }
    }
}
