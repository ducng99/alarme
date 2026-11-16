package dev.tomng.alarme.core.audio.di

import android.os.Looper
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingAudioScopeTest {

    @Test
    fun `ring audio work runs on the main thread where the players live`() {
        val scope = AudioProviderModule.ringAudioScope()
        var ranOnMain: Boolean? = null

        scope.launch { ranOnMain = Looper.myLooper() == Looper.getMainLooper() }
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(ranOnMain).isTrue()
        scope.cancel()
    }
}
