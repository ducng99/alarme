package dev.tomng.alarme.feature.missions.speech

import kotlinx.coroutines.flow.StateFlow

/** What the speech source last heard or hit. */
sealed interface SpeechEvent {
    data object Idle : SpeechEvent

    data class Recognized(val text: String) : SpeechEvent

    /** The recogniser heard nothing usable (no match / silence). */
    data object NoSpeech : SpeechEvent

    /** No engine on this device supports the language — the mission dead-ends. */
    data object Unavailable : SpeechEvent

    data object Error : SpeechEvent
}

/** The recogniser seam: production wraps the platform engine, tests push events. */
interface SpeechSource : AutoCloseable {
    val events: StateFlow<SpeechEvent>

    /** True when an on-device or online model exists for [locale] (e.g. "en-US"). */
    fun isLanguageAvailable(locale: String): Boolean

    fun startListening(locale: String)

    fun stopListening()
}
