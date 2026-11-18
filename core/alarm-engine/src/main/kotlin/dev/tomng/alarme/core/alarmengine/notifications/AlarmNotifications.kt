package dev.tomng.alarme.core.alarmengine.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomng.alarme.core.alarmengine.R
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the intent that opens the ring screen (full-screen intent target). */
fun interface RingScreenIntentFactory {
    fun ringIntent(): Intent
}

/**
 * Alarm notification channels + the ringing notification (id 1001, category ALARM, full-screen
 * intent fallback for background starts).
 */
@Singleton
class AlarmNotifications
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val ringScreenIntentFactory: RingScreenIntentFactory,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Volatile private var ringScreenShown = false
    @Volatile private var postedRingingName: String? = null
    @Volatile private var ringingPosted = false

    fun ensureChannels() {
        if (
            notificationManager.getNotificationChannel(CHANNEL_ALARM_STATUS) != null &&
                notificationManager.getNotificationChannel(CHANNEL_ALARM_QUIET) != null &&
                notificationManager.getNotificationChannel(CHANNEL_NEXT_ALARM) != null
        ) {
            return
        }
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALARM_STATUS,
                context.getString(R.string.alarme_notif_channel_status),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                    CHANNEL_ALARM_QUIET,
                    context.getString(R.string.alarme_notif_channel_quiet),
                    NotificationManager.IMPORTANCE_LOW,
                )
                .apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NEXT_ALARM,
                context.getString(R.string.alarme_notif_channel_next),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    /**
     * The ongoing "alarm is ringing" notification (id [RINGING_NOTIFICATION_ID]). While the ring
     * screen is in front it goes to the quiet channel without a full-screen intent, so no heads-up
     * banner covers the ring screen.
     */
    fun ringingNotification(alarmName: String?): NotificationCompat.Builder {
        val open =
            PendingIntent.getActivity(
                context,
                0,
                ringScreenIntent(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val quiet = ringScreenShown
        val builder =
            NotificationCompat.Builder(
                    context,
                    if (quiet) CHANNEL_ALARM_QUIET else CHANNEL_ALARM_STATUS,
                )
                .setSmallIcon(R.drawable.ic_stat_alarm)
                .setContentTitle(context.getString(R.string.alarme_notif_ring_title))
                .setContentText(alarmName.orEmpty())
                .setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        if (quiet) {
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
        } else {
            builder.setFullScreenIntent(open, true)
        }
        return builder
    }

    /** Posts the ringing notification and remembers it so it can be quietened later. */
    fun postRinging(alarmName: String?) {
        postedRingingName = alarmName
        ringingPosted = true
        notificationManager.notify(RINGING_NOTIFICATION_ID, ringingNotification(alarmName).build())
    }

    /** The ring screen is in front: replace the heads-up ringing notification with a quiet one. */
    fun onRingScreenShown() {
        if (ringScreenShown) return
        ringScreenShown = true
        if (ringingPosted) {
            notificationManager.notify(
                RINGING_NOTIFICATION_ID,
                ringingNotification(postedRingingName).build(),
            )
        }
    }

    fun onRingScreenHidden() {
        ringScreenShown = false
    }

    /**
     * Every ring-screen launch reuses the one instance: without REORDER_TO_FRONT each posted
     * notification stacks another copy of the screen on the task.
     */
    private fun ringScreenIntent(): Intent =
        ringScreenIntentFactory
            .ringIntent()
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)

    /**
     * The wake-up check prompt (id 1002): same channel and category as the ring, but no sound and
     * no full-screen intent — it is the quiet one, a tap opens the check.
     */
    fun wakeUpCheckNotification(alarmName: String?): NotificationCompat.Builder {
        val open =
            PendingIntent.getActivity(
                context,
                0,
                ringScreenIntent(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        return NotificationCompat.Builder(context, CHANNEL_ALARM_STATUS)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.alarme_wuc_notif_title))
            .setContentText(context.getString(R.string.alarme_wuc_notif_body))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.alarme_wuc_notif_body)),
            )
            .setSubText(alarmName)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    fun postWakeUpCheck(builder: NotificationCompat.Builder) {
        notificationManager.notify(WAKE_UP_CHECK_NOTIFICATION_ID, builder.build())
    }

    /** The ongoing "snoozed until HH:mm" form of the ring notification (same id, id 1001). */
    fun postSnoozed(alarmName: String?, until: java.time.Instant) {
        ringingPosted = false
        notificationManager.notify(
            RINGING_NOTIFICATION_ID,
            snoozedNotification(alarmName, until).build(),
        )
    }

    private fun snoozedNotification(
        alarmName: String?,
        until: java.time.Instant,
    ): NotificationCompat.Builder {
        val open =
            PendingIntent.getActivity(
                context,
                0,
                ringScreenIntent(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val untilText =
            java.time.format.DateTimeFormatter.ofPattern("h:mm a")
                .format(java.time.Instant.ofEpochMilli(until.toEpochMilli()).atZone(zone()))
        return NotificationCompat.Builder(context, CHANNEL_ALARM_QUIET)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.alarme_notif_snoozed_title))
            .setContentText(
                context.getString(R.string.alarme_notif_snoozed_until, untilText),
            )
            .setSubText(alarmName)
            .setCategory(Notification.CATEGORY_ALARM)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    private fun zone(): java.time.ZoneId = java.time.ZoneId.systemDefault()

    /** The pre-alarm reminder (id 1003): "%s alarm is coming up. Skip it?" */
    fun reminderNotification(
        alarmName: String,
        ringAt: java.time.Instant,
        skipIntent: PendingIntent,
    ): NotificationCompat.Builder {
        val timeText =
            java.time.format.DateTimeFormatter.ofPattern("h:mm a")
                .format(java.time.Instant.ofEpochMilli(ringAt.toEpochMilli()).atZone(zone()))
        return NotificationCompat.Builder(context, CHANNEL_NEXT_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(context.getString(R.string.alarme_notif_reminder_title, timeText))
            .setContentText(context.getString(R.string.alarme_notif_reminder_body, alarmName))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .addAction(0, context.getString(R.string.alarme_notif_reminder_skip), skipIntent)
    }

    fun postReminder(builder: NotificationCompat.Builder) {
        notificationManager.notify(REMINDER_NOTIFICATION_ID, builder.build())
    }

    companion object {
        /** The ringing notification id (1001). */
        const val RINGING_NOTIFICATION_ID = 1001

        /** The wake-up check notification id (1002). */
        const val WAKE_UP_CHECK_NOTIFICATION_ID = 1002

        /** The pre-alarm reminder notification id (1003). */
        const val REMINDER_NOTIFICATION_ID = 1003
        const val CHANNEL_ALARM_STATUS = "alarm_status"
        const val CHANNEL_ALARM_QUIET = "alarm_quiet"
        const val CHANNEL_NEXT_ALARM = "channel_next_alarm"
    }
}
