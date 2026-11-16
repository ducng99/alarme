package dev.tomng.alarme.core.audio.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AlarmSoundCandidatesTest {

    private val settingsAlias = "content://settings/system/alarm_sound"
    private val systemDefault = "content://media/internal/audio/media/217"
    private val bundled = "android.resource://dev.tomng.alarme/raw/alarme_radar"

    @Test
    fun `the settings alias is replaced by the actual system default`() {
        val candidates =
            alarmSoundCandidates(
                requested = settingsAlias,
                systemDefault = systemDefault,
                bundledFallback = bundled,
            )

        assertThat(candidates).containsExactly(systemDefault, bundled).inOrder()
    }

    @Test
    fun `a chosen sound falls back to the system default then the bundled tone`() {
        val candidates =
            alarmSoundCandidates(
                requested = "file:///data/music/song.mp3",
                systemDefault = systemDefault,
                bundledFallback = bundled,
            )

        assertThat(candidates)
            .containsExactly("file:///data/music/song.mp3", systemDefault, bundled)
            .inOrder()
    }

    @Test
    fun `a missing system default skips straight to the bundled tone`() {
        val candidates =
            alarmSoundCandidates(
                requested = settingsAlias,
                systemDefault = null,
                bundledFallback = bundled,
            )

        assertThat(candidates).containsExactly(bundled)
    }

    @Test
    fun `a system default that is itself the settings alias is ignored`() {
        val candidates =
            alarmSoundCandidates(
                requested = bundled,
                systemDefault = settingsAlias,
                bundledFallback = bundled,
            )

        assertThat(candidates).containsExactly(bundled)
    }
}
