package dev.tomng.alarme.feature.settings.permissions

import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidPermissionIntentsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val intents = AndroidPermissionIntents(context)

    @Test
    fun `notifications open the app's notification settings`() {
        val intent = intents.forItem(PermissionItem.NOTIFICATIONS)

        assertThat(intent.action).isEqualTo(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        assertThat(intent.getStringExtra(Settings.EXTRA_APP_PACKAGE)).isEqualTo(context.packageName)
    }

    @Test
    fun `exact alarms open the exact-alarm access screen`() {
        assertThat(intents.forItem(PermissionItem.EXACT_ALARM).action)
            .isEqualTo(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
    }

    @Test
    fun `full-screen intents open the full-screen intent access screen`() {
        assertThat(intents.forItem(PermissionItem.FULL_SCREEN_INTENT).action)
            .isEqualTo(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
    }

    @Test
    fun `overlay opens the overlay screen scoped to the app`() {
        val intent = intents.forItem(PermissionItem.OVERLAY)

        assertThat(intent.action).isEqualTo(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        assertThat(intent.data).isEqualTo(Uri.parse("package:${context.packageName}"))
    }

    @Test
    fun `do not disturb opens the policy access screen`() {
        assertThat(intents.forItem(PermissionItem.DND).action)
            .isEqualTo(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    @Test
    fun `battery opens the battery optimization list`() {
        assertThat(intents.forItem(PermissionItem.BATTERY).action)
            .isEqualTo(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }

    @Test
    fun `camera and microphone open the app details page`() {
        for (item in listOf(PermissionItem.CAMERA, PermissionItem.MICROPHONE)) {
            val intent = intents.forItem(item)

            assertThat(intent.action).isEqualTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            assertThat(intent.data).isEqualTo(Uri.parse("package:${context.packageName}"))
        }
    }
}
