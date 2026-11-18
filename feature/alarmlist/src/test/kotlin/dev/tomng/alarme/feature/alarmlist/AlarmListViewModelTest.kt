package dev.tomng.alarme.feature.alarmlist

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.common.time.FakeAppClock
import dev.tomng.alarme.core.data.repository.AlarmRepository
import dev.tomng.alarme.core.data.repository.SkipAlarmUseCase
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.AlarmType
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test

/** The list's actions: toggle, skip/undo, delete, quick creation — each re-arming the scheduler. */
@OptIn(ExperimentalCoroutinesApi::class)
class AlarmListViewModelTest {

    private val clock = FakeAppClock(Instant.parse("2026-11-10T06:00:00Z"), TimeZone.UTC)
    private val repo = FakeRepo()
    private val scheduler = RecordingScheduler()
    private lateinit var viewModel: AlarmListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = AlarmListViewModel(repo, SkipAlarmUseCase(repo), scheduler, clock)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun alarm(
        id: Long = 7,
        enabled: Boolean = true,
        days: DaysOfWeek = DaysOfWeek.EVERY_DAY,
        type: AlarmType = AlarmType.NORMAL,
        skipped: Boolean = false,
    ) =
        Alarm(
            id = id,
            type = type,
            name = "work",
            hour = 7,
            minute = 0,
            enabled = enabled,
            daysOfWeek = days,
            isSkipped = skipped,
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )

    @Test
    fun `the list is unknown until the repository answers`() = runTest {
        val fresh =
            AlarmListViewModel(NeverRepository(repo), SkipAlarmUseCase(repo), scheduler, clock)
        backgroundScope.launch { fresh.alarms.collect {} }

        assertThat(fresh.alarms.value).isNull()
    }

    @Test
    fun `toggling an alarm persists and rearms`() = runTest {
        repo.alarms.value = listOf(alarm())

        viewModel.setEnabled(alarm(), enabled = false)

        assertThat(repo.alarms.value.single().enabled).isFalse()
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.TURN_OFF_ALARM)
    }

    @Test
    fun `skipping a repeating alarm marks it skipped and rearms`() = runTest {
        repo.alarms.value = listOf(alarm())

        viewModel.skip(alarm())

        assertThat(repo.alarms.value.single().isSkipped).isTrue()
        // The already-armed delivery must be replaced, or the skipped occurrence still rings.
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.PROCESS_SKIP)
    }

    @Test
    fun `skipping a one-shot alarm surfaces the denial`() = runTest {
        repo.alarms.value = listOf(alarm(days = DaysOfWeek.NONE))

        viewModel.skip(alarm(days = DaysOfWeek.NONE))

        assertThat(repo.alarms.value.single().isSkipped).isFalse()
        assertThat(viewModel.message.value).isEqualTo(AlarmListMessage.SkipNotRepeating)
    }

    @Test
    fun `skipping a disabled alarm surfaces the denial`() = runTest {
        repo.alarms.value = listOf(alarm(enabled = false))

        viewModel.skip(alarm(enabled = false))

        assertThat(viewModel.message.value).isEqualTo(AlarmListMessage.SkipDisabled)
    }

    @Test
    fun `undoing a skip clears the badge and rearms`() = runTest {
        repo.alarms.value = listOf(alarm(skipped = true))

        viewModel.undoSkip(alarm(skipped = true))

        assertThat(repo.alarms.value.single().isSkipped).isFalse()
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.PROCESS_SKIP)
    }

    @Test
    fun `duplicating an alarm stores a copy with a new id and rearms`() = runTest {
        repo.alarms.value = listOf(alarm())

        viewModel.duplicate(alarm())

        assertThat(repo.alarms.value).hasSize(2)
        val copy = repo.alarms.value.single { it.id != 7L }
        assertThat(copy.name).isEqualTo("work")
        assertThat(copy.hour).isEqualTo(7)
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.ADD_ALARM)
    }

    @Test
    fun `deleting an alarm removes it and rearms`() = runTest {
        repo.alarms.value = listOf(alarm())

        viewModel.delete(alarm())

        assertThat(repo.alarms.value).isEmpty()
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.DELETE_ALARM)
    }

    @Test
    fun `deleting an alarm offers an undo`() = runTest {
        repo.alarms.value = listOf(alarm())

        viewModel.delete(alarm())

        assertThat(viewModel.message.value).isEqualTo(AlarmListMessage.Deleted)
    }

    @Test
    fun `undoing a delete restores the alarm and rearms`() = runTest {
        repo.alarms.value = listOf(alarm())
        viewModel.delete(alarm())

        viewModel.undoDelete()

        assertThat(repo.alarms.value.single().name).isEqualTo("work")
        assertThat(scheduler.reasons)
            .containsExactly(RescheduleReason.DELETE_ALARM, RescheduleReason.ADD_ALARM)
            .inOrder()
    }

    @Test
    fun `undo does nothing once the deleted alarm has been forgotten`() = runTest {
        repo.alarms.value = listOf(alarm())
        viewModel.delete(alarm())
        viewModel.consumeMessage()

        viewModel.undoDelete()

        assertThat(repo.alarms.value).isEmpty()
    }

    @Test
    fun `a quick alarm is created one-shot at the requested offset`() = runTest {
        viewModel.createQuick(minutes = 8 * 60 + 30)

        val created = repo.alarms.value.single()
        assertThat(created.type).isEqualTo(AlarmType.QUICK)
        assertThat(created.daysOfWeek).isEqualTo(DaysOfWeek.NONE)
        assertThat(created.hour).isEqualTo(14)
        assertThat(created.minute).isEqualTo(30)
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.ADD_ALARM)
        assertThat(viewModel.message.value)
            .isEqualTo(AlarmListMessage.QuickCreated(Instant.parse("2026-11-10T14:30:00Z")))
    }

    @Test
    fun `undoing a quick alarm deletes it again`() = runTest {
        viewModel.createQuick(minutes = 60)

        viewModel.undoQuick()

        assertThat(repo.alarms.value).isEmpty()
        assertThat(scheduler.reasons)
            .containsExactly(RescheduleReason.ADD_ALARM, RescheduleReason.DELETE_ALARM)
            .inOrder()
    }

    @Test
    fun `turning an alarm on says when it will ring`() = runTest {
        repo.alarms.value = listOf(alarm(enabled = false))
        scheduler.next = alarm()

        viewModel.setEnabled(alarm(enabled = false), enabled = true)

        assertThat(viewModel.message.value)
            .isEqualTo(AlarmListMessage.TurnedOn(Instant.parse("2026-11-10T07:00:00Z")))
    }

    @Test
    fun `turning an alarm off says nothing`() = runTest {
        repo.alarms.value = listOf(alarm())

        viewModel.setEnabled(alarm(), enabled = false)

        assertThat(viewModel.message.value).isNull()
    }

    @Test
    fun `skipping confirms with an undoable message`() = runTest {
        repo.alarms.value = listOf(alarm())

        viewModel.skip(alarm())

        assertThat(viewModel.message.value).isEqualTo(AlarmListMessage.Skipped(7))
    }

    @Test
    fun `alarms are listed enabled first, soonest first, with their next ring`() = runTest {
        repo.alarms.value =
            listOf(
                alarm(id = 1, enabled = false).copy(hour = 5),
                alarm(id = 2).copy(hour = 9),
                alarm(id = 3).copy(hour = 6, minute = 30),
                alarm(id = 4).copy(hour = 5),
            )

        val listed = viewModel.alarms.first { !it.isNullOrEmpty() }!!

        // 06:00 now: 06:30 and 09:00 ring today, 05:00 tomorrow; disabled sink to the end.
        assertThat(listed.map { it.id }).containsExactly(3L, 2L, 4L, 1L).inOrder()
        assertThat(listed.first().nextOccurrence).isEqualTo(Instant.parse("2026-11-10T06:30:00Z"))
        assertThat(listed.last().nextOccurrence).isNull()
    }

    @Test
    fun `changing an alarm's sound persists the uri and rearms`() = runTest {
        repo.alarms.value = listOf(alarm())

        viewModel.setRingtone(alarm(), "uri_silent_ringtone")

        assertThat(repo.alarms.value.single().ringtoneUri).isEqualTo("uri_silent_ringtone")
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.MODIFY_ALARM)
    }

    @Test
    fun `a refused arming surfaces the not-armed message`() = runTest {
        repo.alarms.value = listOf(alarm())
        scheduler.next = alarm()
        scheduler.armed = false

        viewModel.setEnabled(alarm(), enabled = true)

        assertThat(viewModel.message.value).isEqualTo(AlarmListMessage.NotArmed)
    }

    @Test
    fun `a successful arming is not reported as a failure`() = runTest {
        repo.alarms.value = listOf(alarm())
        scheduler.next = alarm()

        viewModel.setEnabled(alarm(), enabled = true)

        assertThat(viewModel.message.value).isNotEqualTo(AlarmListMessage.NotArmed)
    }

    @Test
    fun `the next alarm is the earliest enabled one`() = runTest {
        repo.alarms.value =
            listOf(
                alarm(id = 1).copy(hour = 5),
                alarm(id = 2).copy(hour = 9),
                alarm(id = 3, enabled = false).copy(hour = 7),
            )

        assertThat(viewModel.nextAlarm.first { it != null }?.id).isEqualTo(2L)
    }

    @Test
    fun `a ring time that passes rolls over without any change to the alarms`() = runTest {
        repo.alarms.value = listOf(alarm(id = 1).copy(hour = 6, minute = 30))
        val collector =
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                viewModel.alarms.collect {}
            }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.nextAlarm.collect {}
        }
        assertThat(viewModel.alarms.value!!.single().nextOccurrence)
            .isEqualTo(Instant.parse("2026-11-10T06:30:00Z"))

        clock.advanceBy(30.minutes)
        advanceTimeBy(30.minutes + 1.milliseconds)

        assertThat(viewModel.alarms.value!!.single().nextOccurrence)
            .isEqualTo(Instant.parse("2026-11-11T06:30:00Z"))
        assertThat(viewModel.nextAlarm.value?.nextOccurrence)
            .isEqualTo(Instant.parse("2026-11-11T06:30:00Z"))
        collector.cancel()
    }

    private class NeverRepository(delegate: AlarmRepository) : AlarmRepository by delegate {
        override fun observeAlarms(): Flow<List<Alarm>> = kotlinx.coroutines.flow.emptyFlow()
    }

    private class FakeRepo : AlarmRepository {
        val alarms = MutableStateFlow<List<Alarm>>(emptyList())

        override fun observeAlarms(): Flow<List<Alarm>> = alarms

        override fun observeNextAlarm(): Flow<Alarm?> = alarms.map { list ->
            list.filter { it.enabled }.minByOrNull { it.nextOccurrence ?: Instant.DISTANT_FUTURE }
        }

        override suspend fun getAlarm(id: Long): Alarm? = alarms.value.firstOrNull { it.id == id }

        override suspend fun upsertAlarm(alarm: Alarm): Long {
            val id = if (alarm.id == Alarm.NEW_ID) 100L else alarm.id
            alarms.value = alarms.value.filterNot { it.id == id } + alarm.copy(id = id)
            return id
        }

        override suspend fun setSkipped(id: Long, skipped: Boolean) =
            mutate(id) { it.copy(isSkipped = skipped) }

        override suspend fun setEnabled(id: Long, enabled: Boolean) =
            mutate(id) { it.copy(enabled = enabled) }

        override suspend fun deleteAlarm(id: Long) {
            alarms.value = alarms.value.filterNot { it.id == id }
        }

        private fun mutate(id: Long, transform: (Alarm) -> Alarm) {
            alarms.value = alarms.value.map { if (it.id == id) transform(it) else it }
        }
    }

    private class RecordingScheduler : dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler {
        val reasons = mutableListOf<RescheduleReason>()
        var next: Alarm? = null
        var armed: Boolean = true

        override suspend fun rearm(
            reason: RescheduleReason,
        ): dev.tomng.alarme.core.alarmengine.scheduling.SchedulingResult {
            reasons += reason
            return dev.tomng.alarme.core.alarmengine.scheduling.SchedulingResult(
                next = next,
                nextAt = null,
                missed = emptyList(),
                armed = armed,
            )
        }

        override fun armWakeUpCheck(alarmId: Long, at: Instant) = Unit

        override fun cancelWakeUpCheck() = Unit

        override fun armSnooze(alarmId: Long, scheduleId: String?, at: Instant, count: Int) = Unit

        override fun cancelSnooze() = Unit

        override fun armRetryRing(
            delivery: dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery,
            at: Instant,
        ) = Unit
    }
}
