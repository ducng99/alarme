package dev.tomng.alarme.core.model.alarm

import kotlinx.serialization.Serializable

/** Gradually raises the ring volume over [seconds] so the wake-up is gentle. */
@Serializable
data class CrescendoOption(
    val enabled: Boolean = false,
    val seconds: Int = DEFAULT_SECONDS,
) {
    init {
        require(seconds in MIN_SECONDS..MAX_SECONDS) {
            "crescendo seconds must be in $MIN_SECONDS..$MAX_SECONDS"
        }
    }

    companion object {
        const val DEFAULT_SECONDS = 15
        const val MIN_SECONDS = 1
        const val MAX_SECONDS = 600
    }
}

/** A free-form memo shown (and optionally spoken via TTS) while the alarm rings. */
@Serializable
data class MemoOption(
    val memo: String = "",
    val ttsEnabled: Boolean = false,
)
