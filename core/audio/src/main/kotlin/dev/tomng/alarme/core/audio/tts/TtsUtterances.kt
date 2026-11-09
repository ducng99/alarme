package dev.tomng.alarme.core.audio.tts

import kotlinx.datetime.LocalTime

/** Composes one round of spoken announcements (label first, then the time). */
object TtsUtterances {

    private val TIME_FORMAT = LocalTime.Format {
        amPmHour()
        chars(":")
        minute()
        chars(" ")
        amPmMarker("AM", "PM")
    }

    fun build(
        label: String,
        speakLabel: Boolean,
        speakTime: Boolean,
        time: LocalTime,
    ): List<String> {
        return buildList {
            if (speakLabel && label.isNotBlank()) add(label)
            if (speakTime) add(speakableTime(time))
        }
    }

    /** "06:42 AM" -> "6:42 AM" — no leading zero, so the engine speaks it naturally. */
    private fun speakableTime(time: LocalTime): String {
        val formatted = TIME_FORMAT.format(time)
        return if (formatted[0] == '0' && formatted[1].isDigit()) formatted.substring(1)
        else formatted
    }
}
