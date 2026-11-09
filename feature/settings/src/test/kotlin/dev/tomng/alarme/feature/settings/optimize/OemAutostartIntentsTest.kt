package dev.tomng.alarme.feature.settings.optimize

import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OemAutostartIntentsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val intents = OemAutostartIntents(context)

    @Test
    fun `every vendor guide has at least one candidate`() {
        for (guide in OemGuide.entries - OemGuide.GENERIC) {
            assertThat(intents.candidates(guide)).isNotEmpty()
        }
    }

    @Test
    fun `the generic guide has no vendor candidates`() {
        assertThat(intents.candidates(OemGuide.GENERIC)).isEmpty()
    }

    @Test
    fun `an unresolvable vendor screen falls back to app details`() {
        val intent = intents.resolve(OemGuide.XIAOMI)

        assertThat(intent.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        assertThat(intent.data?.toString()).isEqualTo("package:${context.packageName}")
    }

    @Test
    fun `a resolvable vendor screen wins over the fallback`() {
        val candidate = intents.candidates(OemGuide.XIAOMI).first()
        org.robolectric.Shadows.shadowOf(context.packageManager)
            .addActivityIfNotPresent(candidate.component!!)

        assertThat(intents.resolve(OemGuide.XIAOMI).component).isEqualTo(candidate.component)
    }
}
