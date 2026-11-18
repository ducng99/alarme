package dev.tomng.alarme.core.alarmengine.notifications

import android.app.Notification
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

    @Test
    fun `the quiet channel is low priority so it never shows a banner`() {
        notifications().ensureChannels()

        val channel =
            context
                .getSystemService(NotificationManager::class.java)
                .getNotificationChannel(AlarmNotifications.CHANNEL_ALARM_QUIET)

        assertThat(channel).isNotNull()
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
    }

    @Test
    fun `showing the ring screen moves the ringing notification to the quiet channel`() {
        val notifications = notifications()
        notifications.ensureChannels()
        notifications.postRinging(alarmName = "work")

        notifications.onRingScreenShown()

        val posted = postedRinging()
        assertThat(posted.channelId).isEqualTo(AlarmNotifications.CHANNEL_ALARM_QUIET)
        assertThat(posted.fullScreenIntent).isNull()
        assertThat(posted.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
            .isEqualTo("work")
        assertThat(posted.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
    }

    @Test
    fun `a ringing notification built while the ring screen is shown is quiet`() {
        val notifications = notifications()
        notifications.ensureChannels()
        notifications.onRingScreenShown()

        val notification = notifications.ringingNotification(alarmName = "work").build()

        assertThat(notification.channelId).isEqualTo(AlarmNotifications.CHANNEL_ALARM_QUIET)
        assertThat(notification.fullScreenIntent).isNull()
    }

    @Test
    fun `a ringing notification built after the ring screen is hidden is full screen again`() {
        val notifications = notifications()
        notifications.ensureChannels()
        notifications.onRingScreenShown()
        notifications.onRingScreenHidden()

        val notification = notifications.ringingNotification(alarmName = "work").build()

        assertThat(notification.channelId).isEqualTo(AlarmNotifications.CHANNEL_ALARM_STATUS)
        assertThat(notification.fullScreenIntent).isNotNull()
    }

    @Test
    fun `the snoozed notification is quiet and survives the ring screen being shown`() {
        val notifications = notifications()
        notifications.ensureChannels()
        notifications.postSnoozed(
            alarmName = "work",
            until = java.time.Instant.parse("2026-11-17T06:10:00Z"),
        )

        notifications.onRingScreenShown()

        val posted = postedRinging()
        assertThat(posted.channelId).isEqualTo(AlarmNotifications.CHANNEL_ALARM_QUIET)
        assertThat(posted.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            .isEqualTo(
                context.getString(
                    dev.tomng.alarme.core.alarmengine.R.string.alarme_notif_snoozed_title,
                ),
            )
    }

    private fun postedRinging(): Notification =
        context
            .getSystemService(NotificationManager::class.java)
            .activeNotifications
            .single { it.id == AlarmNotifications.RINGING_NOTIFICATION_ID }
            .notification
}
