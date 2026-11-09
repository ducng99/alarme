package dev.tomng.alarme.core.audio.tts

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalTime
import org.junit.Test

/** Utterance list for one TTS round (label first, then the current time). */
class TtsUtterancesTest {

    private val time = LocalTime(6, 42)

    @Test
    fun `label then time`() {
        assertThat(
                TtsUtterances.build(
                    label = "take the bins out",
                    speakLabel = true,
                    speakTime = true,
                    time = time,
                ),
            )
            .containsExactly("take the bins out", "6:42 AM")
            .inOrder()
    }

    @Test
    fun `label only`() {
        assertThat(
                TtsUtterances.build(
                    label = "meeting",
                    speakLabel = true,
                    speakTime = false,
                    time = time,
                ),
            )
            .containsExactly("meeting")
    }

    @Test
    fun `time only`() {
        assertThat(
                TtsUtterances.build(
                    label = "unused",
                    speakLabel = false,
                    speakTime = true,
                    time = time,
                ),
            )
            .containsExactly("6:42 AM")
    }

    @Test
    fun `blank label is skipped`() {
        assertThat(
                TtsUtterances.build(label = "  ", speakLabel = true, speakTime = true, time = time),
            )
            .containsExactly("6:42 AM")
    }

    @Test
    fun `nothing enabled is empty`() {
        assertThat(
                TtsUtterances.build(
                    label = "x",
                    speakLabel = false,
                    speakTime = false,
                    time = time,
                ),
            )
            .isEmpty()
    }

    @Test
    fun `afternoon time renders as PM`() {
        assertThat(
                TtsUtterances.build(
                    label = "",
                    speakLabel = false,
                    speakTime = true,
                    time = LocalTime(18, 5),
                ),
            )
            .containsExactly("6:05 PM")
    }
}
