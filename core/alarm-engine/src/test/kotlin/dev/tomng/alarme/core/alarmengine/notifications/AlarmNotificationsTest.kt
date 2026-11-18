package dev.tomng.alarme.core.alarmengine.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmNotificationsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun notifications(ringIntent: Intent = Intent("dev.tomng.alarme.action.OPEN_RING")) =
        AlarmNotifications(context) { ringIntent }

    @Test
    fun `ensures the alarm status channel is high priority`() {
        notifications().ensureChannels()

        val channel =
            context
                .getSystemService(NotificationManager::class.java)
                .getNotificationChannel(AlarmNotifications.CHANNEL_ALARM_STATUS)

        assertThat(channel).isNotNull()
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
    }

    @Test
    fun `the ring screen intent reuses the existing instance`() {
        val notifications = notifications()
        notifications.ensureChannels()

        val fullScreen =
            notifications.ringingNotification(alarmName = "work").build().contentIntent.let {
                shadowOf(it).savedIntent
            }

        assertThat(fullScreen.flags and Intent.FLAG_ACTIVITY_REORDER_TO_FRONT).isNotEqualTo(0)
        assertThat(fullScreen.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun `ringing notification is an ongoing alarm with a full screen intent`() {
        val notifications = notifications()
        notifications.ensureChannels()

        val notification = notifications.ringingNotification(alarmName = "work").build()

        assertThat(notification.channelId).isEqualTo(AlarmNotifications.CHANNEL_ALARM_STATUS)
        assertThat(notification.category).isEqualTo(android.app.Notification.CATEGORY_ALARM)
        assertThat(notification.flags and android.app.Notification.FLAG_ONGOING_EVENT)
            .isNotEqualTo(0)
        assertThat(notification.fullScreenIntent).isNotNull()
    }
}
