package dev.tomng.alarme.core.alarmengine.dispatch

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.tomng.alarme.core.alarmengine.notifications.AlarmNotifications
import dev.tomng.alarme.core.alarmengine.receiver.SkipAlarmReceiver
import dev.tomng.alarme.core.common.di.ApplicationScope
import dev.tomng.alarme.core.data.repository.AlarmRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The early-wake prepare action: a "Skip it?" reminder for the next alarm, five (or fifteen)
 * minutes before it rings. Only skippable alarms (enabled, repeating) get the push — the action is
 * the point of it.
 */
@Singleton
class ReminderActions
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val alarmRepository: AlarmRepository,
    private val notifications: AlarmNotifications,
    @ApplicationScope private val scope: CoroutineScope,
) : PrepareActions {

    override fun onPrepare() {
        scope.launch {
            val next = alarmRepository.observeNextAlarm().first() ?: return@launch
            val ringAt = next.nextOccurrence ?: return@launch
            if (!next.enabled || !next.daysOfWeek.isRepeating) return@launch
            notifications.ensureChannels()
            notifications.postReminder(
                notifications.reminderNotification(
                    alarmName = next.name,
                    ringAt = java.time.Instant.ofEpochMilli(ringAt.toEpochMilliseconds()),
                    skipIntent = skipIntent(next.id),
                ),
            )
        }
    }

    private fun skipIntent(alarmId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarmId.toInt(),
            Intent(context, SkipAlarmReceiver::class.java)
                .putExtra(SkipAlarmReceiver.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
