package dev.tomng.alarme.core.alarmengine.receiver

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.FakeAlarmScheduler
import dev.tomng.alarme.core.alarmengine.FakeRingEventRepository
import dev.tomng.alarme.core.alarmengine.InMemorySnoozeStore
import dev.tomng.alarme.core.alarmengine.scheduling.BootHandler
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.alarmengine.scheduling.SchedulingResult
import dev.tomng.alarme.core.alarmengine.scheduling.SnoozeRecord
import dev.tomng.alarme.core.common.time.FakeAppClock
import dev.tomng.alarme.core.data.repository.AlarmEventType
import dev.tomng.alarme.core.model.alarm.Alarm
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.TimeZone
import org.junit.Test

class BootHandlerTest {

    private val clock = FakeAppClock(Instant.parse("2026-11-04T06:30:00Z"), TimeZone.UTC)

    private fun idStore(
        id: String? = null,
    ): dev.tomng.alarme.core.alarmengine.InMemoryScheduleIdStore =
        dev.tomng.alarme.core.alarmengine.InMemoryScheduleIdStore().apply { id?.let { value = it } }

    private fun alarm(id: Long) =
        Alarm(
            id = id,
            name = "alarm $id",
            hour = 6,
            minute = 0,
            createdAt = Instant.parse("2026-11-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-11-01T00:00:00Z"),
        )

    @Test
    fun `boot rearms and logs missed alarms`() = runTest {
        val scheduler = FakeAlarmScheduler()
        scheduler.nextResult =
            SchedulingResult(next = alarm(1), nextAt = clock.now(), missed = listOf(alarm(3)))
        val events = FakeRingEventRepository()

        BootHandler(scheduler, events, idStore(), InMemorySnoozeStore(), clock).onBoot()

        assertThat(scheduler.reasons).containsExactly(RescheduleReason.REBOOT)
        assertThat(events.events).hasSize(1)
        assertThat(events.events.single().event).isEqualTo(AlarmEventType.MISSED)
        assertThat(events.events.single().triggerType).isEqualTo("missed_alarm")
    }

    @Test
    fun `boot with nothing missed only rearms`() = runTest {
        val scheduler = FakeAlarmScheduler()
        val events = FakeRingEventRepository()

        BootHandler(scheduler, events, idStore(), InMemorySnoozeStore(), clock).onBoot()

        assertThat(scheduler.reasons).containsExactly(RescheduleReason.REBOOT)
        assertThat(events.events).isEmpty()
    }

    @Test
    fun `a reboot mid-snooze re-arms the snooze for its stored finish`() = runTest {
        val scheduler = FakeAlarmScheduler()
        scheduler.nextResult =
            SchedulingResult(next = alarm(7), nextAt = clock.now, missed = emptyList())
        val snoozeStore = InMemorySnoozeStore()
        snoozeStore.save(
            SnoozeRecord(
                alarmId = 7,
                finishAtMillis = (clock.now + 4.minutes).toEpochMilliseconds(),
                count = 2,
            ),
        )

        BootHandler(scheduler, FakeRingEventRepository(), idStore("sid"), snoozeStore, clock)
            .onBoot()

        assertThat(scheduler.snoozes).hasSize(1)
        val (alarmId, at, count) = scheduler.snoozes.single()
        assertThat(alarmId).isEqualTo(7)
        assertThat(at).isEqualTo(clock.now + 4.minutes)
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `a snooze whose finish passed while off re-arms for right now`() = runTest {
        val scheduler = FakeAlarmScheduler()
        val snoozeStore = InMemorySnoozeStore()
        snoozeStore.save(
            SnoozeRecord(
                alarmId = 7,
                finishAtMillis = (clock.now - 2.minutes).toEpochMilliseconds(),
                count = 1,
            ),
        )

        scheduler.nextResult =
            SchedulingResult(next = alarm(7), nextAt = clock.now, missed = emptyList())
        BootHandler(scheduler, FakeRingEventRepository(), idStore("sid"), snoozeStore, clock)
            .onBoot()

        val (alarmId, at, count) = scheduler.snoozes.single()
        assertThat(alarmId).isEqualTo(7)
        assertThat(at).isEqualTo(clock.now)
        assertThat(count).isEqualTo(1)
    }

    @Test
    fun `boot without a stored snooze arms none`() = runTest {
        val scheduler = FakeAlarmScheduler()

        BootHandler(scheduler, FakeRingEventRepository(), idStore(), InMemorySnoozeStore(), clock)
            .onBoot()

        assertThat(scheduler.snoozes).isEmpty()
    }
}
