package dev.tomng.alarme.core.alarmengine.scheduling

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.FakeAlarmRepository
import dev.tomng.alarme.core.alarmengine.FakeSettingsRepository
import dev.tomng.alarme.core.alarmengine.InMemoryScheduleIdStore
import dev.tomng.alarme.core.alarmengine.receiver.AlarmPrepareReceiver
import dev.tomng.alarme.core.alarmengine.receiver.AlarmRingReceiver
import dev.tomng.alarme.core.alarmengine.receiver.AlarmWarmupReceiver
import dev.tomng.alarme.core.alarmengine.receiver.WakeUpCheckReceiver
import dev.tomng.alarme.core.common.time.FakeAppClock
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import dev.tomng.alarme.core.model.scheduling.ScheduleIds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmSchedulerImplTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val clock = FakeAppClock(Instant.parse("2026-11-03T23:10:00Z"), TimeZone.UTC) // Tuesday
    private val alarmRepo = FakeAlarmRepository()
    private val settingsRepo = FakeSettingsRepository("dev1")
    private val store = InMemoryScheduleIdStore()
    private var warmUpRule: () -> WarmUpOptions? = { null }
    private lateinit var scheduler: AlarmSchedulerImpl
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setUp() {
        alarmManager = context.getSystemService(AlarmManager::class.java)
        scheduler =
            AlarmSchedulerImpl(
                context = context,
                alarms = AndroidArmTarget(alarmManager),
                alarmRepository = alarmRepo,
                settingsRepository = settingsRepo,
                scheduleIdStore = store,
                warmUpRule = { warmUpRule() },
                clock = clock,
            )
    }

    private fun alarm(
        id: Long,
        hour: Int,
        minute: Int = 0,
        enabled: Boolean = true,
        skipped: Boolean = false,
        nextOccurrence: Instant? = null,
        days: DaysOfWeek = DaysOfWeek.EVERY_DAY,
    ) =
        Alarm(
            id = id,
            name = "alarm $id",
            hour = hour,
            minute = minute,
            daysOfWeek = days,
            enabled = enabled,
            isSkipped = skipped,
            nextOccurrence = nextOccurrence,
            createdAt = Instant.parse("2026-11-01T00:00:00Z"),
            updatedAt = if (skipped) clock.now() else Instant.parse("2026-11-01T00:00:00Z"),
        )

    /** Scheduled alarm whose wrapped broadcast targets [receiver], or null. */
    private fun shadowAlarmFor(
        receiver: Class<*>,
    ): org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm? =
        shadowOf(alarmManager).scheduledAlarms.firstOrNull { alarm ->
            val intent = alarm.operation?.let { shadowOf(it).savedIntents.firstOrNull() }
            intent?.component?.className == receiver.name
        }

    @Test
    fun `rearm arms setAlarmClock for the earliest upcoming alarm`() = runTest {
        alarmRepo.alarms.value =
            listOf(alarm(id = 1, hour = 6, minute = 30), alarm(id = 2, hour = 5, minute = 45))

        val result = scheduler.rearm(RescheduleReason.ADD_ALARM)

        val expected = Instant.parse("2026-11-04T05:45:00Z")
        assertThat(result.next!!.id).isEqualTo(2)
        assertThat(result.nextAt).isEqualTo(expected)

        val ring = shadowAlarmFor(AlarmRingReceiver::class.java)!!
        assertThat(ring.triggerAtTime).isEqualTo(expected.toEpochMilliseconds())
        assertThat(ring.alarmClockInfo).isNotNull() // setAlarmClock, not setExact

        val expectedId = ScheduleIds.format("dev1", 2, expected, clock.now(), clock.zone())
        assertThat(store.load()).isEqualTo(expectedId.raw)

        val intent = shadowOf(ring.operation!!).savedIntents.first()
        assertThat(intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1)).isEqualTo(2)
        assertThat(intent.getStringExtra(AlarmIntents.EXTRA_SCHEDULE_ID)).isEqualTo(expectedId.raw)
        assertThat(intent.getLongExtra(AlarmIntents.EXTRA_ALARM_TIME, -1))
            .isEqualTo(expected.toEpochMilliseconds())
        assertThat(intent.getStringExtra(AlarmIntents.EXTRA_TRIGGER_TYPE))
            .isEqualTo(TriggerType.NORMAL.name)
    }

    @Test
    fun `a refused arm is reported instead of pretending the alarm is scheduled`() = runTest {
        alarmRepo.alarms.value = listOf(alarm(id = 1, hour = 6, minute = 30))
        val refusing =
            AlarmSchedulerImpl(
                context = context,
                alarms =
                    AndroidArmTarget(
                        armClock = { _, _ -> false },
                        armExact = { _, _, _ -> false },
                        cancelArm = {},
                    ),
                alarmRepository = alarmRepo,
                settingsRepository = settingsRepo,
                scheduleIdStore = store,
                warmUpRule = { warmUpRule() },
                clock = clock,
            )

        val result = refusing.rearm(RescheduleReason.ADD_ALARM)

        assertThat(result.next?.id).isEqualTo(1L)
        assertThat(result.armed).isFalse()
    }

    @Test
    fun `disabled alarms are never armed`() = runTest {
        alarmRepo.alarms.value = listOf(alarm(id = 1, hour = 6, enabled = false))

        val result = scheduler.rearm(RescheduleReason.TURN_OFF_ALARM)

        assertThat(result.next).isNull()
        assertThat(shadowOf(alarmManager).scheduledAlarms).isEmpty()
        assertThat(store.load()).isNull()
    }

    @Test
    fun `rearm replaces the previous arming and mints a fresh schedule id`() = runTest {
        alarmRepo.alarms.value = listOf(alarm(id = 1, hour = 6))
        scheduler.rearm(RescheduleReason.ADD_ALARM)
        val firstId = store.load()!!

        clock.advanceBy(1.minutes)
        alarmRepo.alarms.value = listOf(alarm(id = 1, hour = 7))
        scheduler.rearm(RescheduleReason.MODIFY_ALARM)

        val ring = shadowAlarmFor(AlarmRingReceiver::class.java)!!
        assertThat(ring.triggerAtTime)
            .isEqualTo(Instant.parse("2026-11-04T07:00:00Z").toEpochMilliseconds())
        assertThat(store.load()).isNotEqualTo(firstId)
        // exactly one ring alarm remains armed
        assertThat(
                shadowOf(alarmManager).scheduledAlarms.count { it.alarmClockInfo != null },
            )
            .isEqualTo(1)
    }

    @Test
    fun `skipped alarm arms the occurrence after the skipped one`() = runTest {
        // Monday-only alarm, skipped, armed from Tuesday Nov 3 -> next Monday Nov 16 semantics
        val monday = DaysOfWeek.of(kotlinx.datetime.DayOfWeek.MONDAY)
        alarmRepo.alarms.value = listOf(alarm(id = 5, hour = 6, days = monday, skipped = true))

        val result = scheduler.rearm(RescheduleReason.PROCESS_SKIP)

        // Tue Nov 3 -> next Monday is Nov 9, but it is skipped -> Nov 16
        assertThat(result.nextAt).isEqualTo(Instant.parse("2026-11-16T06:00:00Z"))
    }

    @Test
    fun `warm-up alarm armed at lead time before the ring`() = runTest {
        warmUpRule = { WarmUpOptions(leadMillis = 30_000, acquireWakeLock = true) }
        alarmRepo.alarms.value = listOf(alarm(id = 1, hour = 6))

        scheduler.rearm(RescheduleReason.ADD_ALARM)

        val warmup = shadowAlarmFor(AlarmWarmupReceiver::class.java)!!
        assertThat(warmup.alarmClockInfo).isNull() // setExactAndAllowWhileIdle
        assertThat(warmup.triggerAtTime)
            .isEqualTo(Instant.parse("2026-11-04T05:59:30Z").toEpochMilliseconds())
        val intent = shadowOf(warmup.operation!!).savedIntents.first()
        assertThat(intent.getBooleanExtra(AlarmIntents.EXTRA_ACQUIRE_WAKE_LOCK, false)).isTrue()
    }

    @Test
    fun `no warm-up alarm without an OEM rule`() = runTest {
        warmUpRule = { null }
        alarmRepo.alarms.value = listOf(alarm(id = 1, hour = 6))

        scheduler.rearm(RescheduleReason.ADD_ALARM)

        assertThat(shadowAlarmFor(AlarmWarmupReceiver::class.java)).isNull()
    }

    @Test
    fun `prepare alarm armed 5 minutes before inside the early-wake window`() = runTest {
        warmUpRule = { null }
        alarmRepo.alarms.value = listOf(alarm(id = 1, hour = 6))

        scheduler.rearm(RescheduleReason.ADD_ALARM)

        val prepare = shadowAlarmFor(AlarmPrepareReceiver::class.java)!!
        assertThat(prepare.triggerAtTime)
            .isEqualTo(Instant.parse("2026-11-04T05:55:00Z").toEpochMilliseconds())
    }

    @Test
    fun `prepare alarm armed 15 minutes before on OEM warm-up devices`() = runTest {
        warmUpRule = { WarmUpOptions(leadMillis = 30_000, acquireWakeLock = true) }
        alarmRepo.alarms.value = listOf(alarm(id = 1, hour = 6))

        scheduler.rearm(RescheduleReason.ADD_ALARM)

        val prepare = shadowAlarmFor(AlarmPrepareReceiver::class.java)!!
        assertThat(prepare.triggerAtTime)
            .isEqualTo(Instant.parse("2026-11-04T05:45:00Z").toEpochMilliseconds())
    }

    @Test
    fun `no prepare alarm outside the early-wake window`() = runTest {
        alarmRepo.alarms.value = listOf(alarm(id = 1, hour = 15))

        scheduler.rearm(RescheduleReason.ADD_ALARM)

        assertThat(shadowAlarmFor(AlarmPrepareReceiver::class.java)).isNull()
    }

    @Test
    fun `missed alarms are reported`() = runTest {
        val missed =
            alarm(
                id = 3,
                hour = 6,
                nextOccurrence = Instant.parse("2026-11-03T06:00:00Z"), // due 17 h ago
            )
        alarmRepo.alarms.value = listOf(missed)

        val result = scheduler.rearm(RescheduleReason.REBOOT)

        assertThat(result.missed.map { it.id }).containsExactly(3L)
        // and the alarm is re-armed for its next future occurrence
        assertThat(result.nextAt).isEqualTo(Instant.parse("2026-11-04T06:00:00Z"))
    }

    @Test
    fun `armWakeUpCheck arms an exact alarm for the wuc receiver`() {
        val at = Instant.parse("2026-11-04T06:05:00Z")

        scheduler.armWakeUpCheck(alarmId = 7, at = at)
        val wuc = shadowAlarmFor(WakeUpCheckReceiver::class.java)!!
        assertThat(wuc.alarmClockInfo).isNull()
        assertThat(wuc.triggerAtTime).isEqualTo(at.toEpochMilliseconds())
        val intent = shadowOf(wuc.operation!!).savedIntents.first()
        assertThat(intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1)).isEqualTo(7)

        scheduler.cancelWakeUpCheck()
        assertThat(shadowAlarmFor(WakeUpCheckReceiver::class.java)).isNull()
    }

    @Test
    fun `armSnooze arms an alarm-clock snooze delivery alongside the next occurrence`() = runTest {
        alarmRepo.alarms.value = listOf(alarm(id = 7, hour = 7))
        scheduler.rearm(RescheduleReason.DISMISS)
        val at = clock.now + 5.minutes

        scheduler.armSnooze(
            alarmId = 7,
            scheduleId = store.load()!!,
            at = at,
            count = 2,
        )

        val snooze = scheduledAlarmWithTrigger(TriggerType.SNOOZE)!!
        assertThat(snooze.alarmClockInfo).isNotNull()
        assertThat(snooze.triggerAtTime).isEqualTo(at.toEpochMilliseconds())
        val intent = shadowOf(snooze.operation!!).savedIntents.first()
        assertThat(intent.getLongExtra(AlarmIntents.EXTRA_ALARM_ID, -1)).isEqualTo(7)
        assertThat(intent.getStringExtra(AlarmIntents.EXTRA_SCHEDULE_ID)).isEqualTo(store.load())
        assertThat(intent.getIntExtra(AlarmIntents.EXTRA_SNOOZE_COUNT, -1)).isEqualTo(2)

        // The next normal occurrence stays armed: a crash mid-snooze must not lose tomorrow.
        assertThat(scheduledAlarmWithTrigger(TriggerType.NORMAL)).isNotNull()

        scheduler.cancelSnooze()
        assertThat(scheduledAlarmWithTrigger(TriggerType.SNOOZE)).isNull()
        assertThat(scheduledAlarmWithTrigger(TriggerType.NORMAL)).isNotNull()
    }

    private fun scheduledAlarmWithTrigger(
        trigger: TriggerType,
    ): org.robolectric.shadows.ShadowAlarmManager.ScheduledAlarm? =
        shadowOf(alarmManager).scheduledAlarms.firstOrNull { alarm ->
            val intent = alarm.operation?.let { shadowOf(it).savedIntents.firstOrNull() }
            intent?.getStringExtra(AlarmIntents.EXTRA_TRIGGER_TYPE) == trigger.name
        }
}
