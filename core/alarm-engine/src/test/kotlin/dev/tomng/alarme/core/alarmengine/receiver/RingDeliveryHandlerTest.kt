package dev.tomng.alarme.core.alarmengine.receiver

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.FakeAlarmScheduler
import dev.tomng.alarme.core.alarmengine.FakeRingEventRepository
import dev.tomng.alarme.core.alarmengine.InMemoryScheduleIdStore
import dev.tomng.alarme.core.alarmengine.RecordingRingDispatcher
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmIntents
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery
import dev.tomng.alarme.core.alarmengine.scheduling.RingDeliveryHandler
import dev.tomng.alarme.core.alarmengine.scheduling.TriggerType
import dev.tomng.alarme.core.common.time.FakeAppClock
import dev.tomng.alarme.core.data.repository.AlarmEventType
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RingDeliveryHandlerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = Instant.parse("2026-11-04T06:00:02Z")
    private val clock = FakeAppClock(now, TimeZone.UTC)
    private val store = InMemoryScheduleIdStore()
    private val scheduler = FakeAlarmScheduler()
    private val events = FakeRingEventRepository()
    private val dispatcher = RecordingRingDispatcher()
    private val snoozeStore = dev.tomng.alarme.core.alarmengine.InMemorySnoozeStore()

    @Before
    fun setUp() {
        store.value = null
    }

    @Test
    fun `valid delivery is dispatched`() = runTest {
        store.save("dev1_2_0600_2310")
        val intent =
            AlarmIntents.ringIntent(
                context,
                alarmId = 2,
                scheduleId = "dev1_2_0600_2310",
                alarmTimeMillis = Instant.parse("2026-11-04T06:00:00Z").toEpochMilliseconds(),
                triggerType = TriggerType.NORMAL,
            )

        handler().handle(intent)

        assertThat(dispatcher.deliveries)
            .containsExactly(
                RingDelivery(
                    alarmId = 2,
                    scheduleId = "dev1_2_0600_2310",
                    alarmTimeMillis = Instant.parse("2026-11-04T06:00:00Z").toEpochMilliseconds(),
                    triggerType = TriggerType.NORMAL,
                ),
            )
        assertThat(scheduler.reasons).isEmpty()
        assertThat(events.events).isEmpty()
    }

    @Test
    fun `stale schedule id is blocked and rescheduled`() = runTest {
        store.save("dev1_2_0600_2310")
        val stale =
            AlarmIntents.ringIntent(
                context,
                alarmId = 2,
                scheduleId = "dev1_2_0600_2200", // replaced arming
                alarmTimeMillis = Instant.parse("2026-11-04T06:00:00Z").toEpochMilliseconds(),
                triggerType = TriggerType.NORMAL,
            )

        handler().handle(stale)

        assertThat(dispatcher.deliveries).isEmpty()
        assertThat(events.events).hasSize(1)
        assertThat(events.events.single().event).isEqualTo(AlarmEventType.BLOCKED)
        assertThat(events.events.single().triggerType).isEqualTo("invalid_alarm")
        assertThat(events.events.single().scheduleId).isEqualTo("dev1_2_0600_2200")
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.FALSE_ALARM)
    }

    @Test
    fun `nothing armed blocks any delivery`() = runTest {
        val bogus =
            AlarmIntents.ringIntent(
                context,
                alarmId = 9,
                scheduleId = "dev1_9_0600_2310",
                alarmTimeMillis = now.toEpochMilliseconds(),
                triggerType = TriggerType.NORMAL,
            )

        handler().handle(bogus)

        assertThat(dispatcher.deliveries).isEmpty()
        assertThat(events.events.single().triggerType).isEqualTo("invalid_alarm")
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.FALSE_ALARM)
    }

    @Test
    fun `late delivery beyond the grace window is expired`() = runTest {
        store.save("dev1_2_0600_2310")
        val late =
            AlarmIntents.ringIntent(
                context,
                alarmId = 2,
                scheduleId = "dev1_2_0600_2310",
                alarmTimeMillis =
                    Instant.parse("2026-11-04T05:45:00Z").toEpochMilliseconds(), // 15 min late
                triggerType = TriggerType.NORMAL,
            )

        handler().handle(late)

        assertThat(dispatcher.deliveries).isEmpty()
        assertThat(events.events.single().event).isEqualTo(AlarmEventType.BLOCKED)
        assertThat(events.events.single().triggerType).isEqualTo("expired_alarm")
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.EXPIRED_ALARM)
    }

    @Test
    fun `unparseable intent is blocked without crashing`() = runTest {
        handler().handle(Intent(AlarmIntents.ACTION_RING))

        assertThat(dispatcher.deliveries).isEmpty()
        assertThat(events.events.single().triggerType).isEqualTo("invalid_alarm")
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.FALSE_ALARM)
    }

    @Test
    fun `a snooze delivery rings even though the armed id moved on`() = runTest {
        store.save("dev1_2_0700_0705")
        snoozeStore.save(
            dev.tomng.alarme.core.alarmengine.scheduling.SnoozeRecord(
                alarmId = 2,
                finishAtMillis = now.toEpochMilliseconds(),
                count = 1,
            ),
        )
        val intent =
            AlarmIntents.ringIntent(
                context,
                alarmId = 2,
                scheduleId = "dev1_2_0700_0649",
                alarmTimeMillis = now.toEpochMilliseconds(),
                triggerType = TriggerType.SNOOZE,
            )

        handler().handle(intent)

        assertThat(dispatcher.deliveries.map { it.alarmId }).containsExactly(2L)
        assertThat(events.events).isEmpty()
    }

    @Test
    fun `a snooze delivery without a pending snooze is blocked`() = runTest {
        store.save("dev1_2_0700_0705")
        val intent =
            AlarmIntents.ringIntent(
                context,
                alarmId = 2,
                scheduleId = "dev1_2_0700_0649",
                alarmTimeMillis = now.toEpochMilliseconds(),
                triggerType = TriggerType.SNOOZE,
            )

        handler().handle(intent)

        assertThat(dispatcher.deliveries).isEmpty()
        assertThat(events.events.map { it.event }).containsExactly(AlarmEventType.BLOCKED)
    }

    @Test
    fun `a snooze delivery for another alarm is blocked`() = runTest {
        store.save("dev1_3_0700_0705")
        snoozeStore.save(
            dev.tomng.alarme.core.alarmengine.scheduling.SnoozeRecord(
                alarmId = 3,
                finishAtMillis = now.toEpochMilliseconds(),
                count = 1,
            ),
        )
        val intent =
            AlarmIntents.ringIntent(
                context,
                alarmId = 2,
                scheduleId = "dev1_3_0700_0649",
                alarmTimeMillis = now.toEpochMilliseconds(),
                triggerType = TriggerType.SNOOZE,
            )

        handler().handle(intent)

        assertThat(dispatcher.deliveries).isEmpty()
    }

    @Test
    fun `a snooze delivery that arrives far too late expires`() = runTest {
        store.save("dev1_2_0700_0705")
        snoozeStore.save(
            dev.tomng.alarme.core.alarmengine.scheduling.SnoozeRecord(
                alarmId = 2,
                finishAtMillis = now.toEpochMilliseconds() - 600_000L,
                count = 1,
            ),
        )
        val intent =
            AlarmIntents.ringIntent(
                context,
                alarmId = 2,
                scheduleId = "dev1_2_0700_0649",
                alarmTimeMillis = now.toEpochMilliseconds() - 600_000L,
                triggerType = TriggerType.SNOOZE,
            )

        handler().handle(intent)

        assertThat(dispatcher.deliveries).isEmpty()
        assertThat(events.events.map { it.triggerType }).containsExactly("expired_alarm")
    }

    private fun handler() =
        RingDeliveryHandler(
            scheduleIdStore = store,
            scheduler = scheduler,
            ringEventRepository = events,
            ringDispatcher = dispatcher,
            snoozeStore = snoozeStore,
            clock = clock,
        )
}
