package dev.tomng.alarme.core.alarmengine.receiver

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.power.Wakelocks
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmIntents
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WarmupActionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `acquires a one minute partial wakelock when requested`() {
        val action = WarmupAction(Wakelocks(context))

        action.onWarmup(AlarmIntents.warmupIntent(context, acquireWakeLock = true))

        val wakeLock = ShadowPowerManager.getLatestWakeLock()
        assertThat(wakeLock).isNotNull()
        assertThat(wakeLock!!.isHeld).isTrue()
    }

    @Test
    fun `no wakelock when the OEM rule says otherwise`() {
        val action = WarmupAction(Wakelocks(context))

        action.onWarmup(AlarmIntents.warmupIntent(context, acquireWakeLock = false))

        assertThat(ShadowPowerManager.getLatestWakeLock()).isNull()
    }
}
