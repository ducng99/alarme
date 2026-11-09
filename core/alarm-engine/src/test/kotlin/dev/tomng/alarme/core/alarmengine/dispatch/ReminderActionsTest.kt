package dev.tomng.alarme.core.alarmengine.dispatch

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.FakeAlarmRepository
import dev.tomng.alarme.core.alarmengine.notifications.AlarmNotifications
import dev.tomng.alarme.core.alarmengine.receiver.SkipAlarmReceiver
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The pre-alarm reminder: a "Skip it?" push for the next skippable alarm, whose action targets the
 * skip receiver with that alarm's id.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderActionsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarmRepo = FakeAlarmRepository()
    private val notifications =
        AlarmNotifications(context) { android.content.Intent("dev.tomng.alarme.action.OPEN_RING") }
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        notifications.ensureChannels()
    }

    private fun alarm(
        id: Long = 7,
        enabled: Boolean = true,
        days: DaysOfWeek = DaysOfWeek.EVERY_DAY,
        nextOccurrence: Instant? = Instant.parse("2026-11-11T07:00:00Z"),
    ) =
        Alarm(
            id = id,
            name = "work",
            hour = 7,
            minute = 0,
            enabled = enabled,
            daysOfWeek = days,
            nextOccurrence = nextOccurrence,
            createdAt = Instant.parse("2026-11-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-11-01T00:00:00Z"),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun actions() =
        ReminderActions(
            context = context,
            alarmRepository = alarmRepo,
            notifications = notifications,
            scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()),
        )

    @Test
    fun `the reminder posts a skip action for the next repeating alarm`() = runTest {
        alarmRepo.alarms.value = listOf(alarm())

        actions().onPrepare()

        val posted = shadowOf(manager).getNotification(1003)
        assertThat(posted).isNotNull()
        assertThat(posted!!.category).isEqualTo(Notification.CATEGORY_REMINDER)
        assertThat(posted.actions).hasLength(1)
        val action = posted.actions.single()
        assertThat(action.title.toString()).isEqualTo("Skip alarm")
        val intent = shadowOf(action.actionIntent).savedIntent
        assertThat(intent.component?.className).isEqualTo(SkipAlarmReceiver::class.java.name)
        assertThat(intent.getLongExtra(SkipAlarmReceiver.EXTRA_ALARM_ID, -1L)).isEqualTo(7L)
    }

    @Test
    fun `a one-shot next alarm gets no reminder`() = runTest {
        alarmRepo.alarms.value = listOf(alarm(days = DaysOfWeek.NONE))

        actions().onPrepare()

        assertThat(shadowOf(manager).getNotification(1003)).isNull()
    }

    @Test
    fun `no next alarm means no reminder`() = runTest {
        actions().onPrepare()

        assertThat(shadowOf(manager).getNotification(1003)).isNull()
    }
}
