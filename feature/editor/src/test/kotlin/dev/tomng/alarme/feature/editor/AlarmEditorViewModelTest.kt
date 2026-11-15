package dev.tomng.alarme.feature.editor

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery
import dev.tomng.alarme.core.alarmengine.scheduling.SchedulingResult
import dev.tomng.alarme.core.common.time.FakeAppClock
import dev.tomng.alarme.core.data.repository.AlarmRepository
import dev.tomng.alarme.core.data.repository.RingtoneRepository
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.MissionType
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DayOfWeek
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlarmEditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val clock = FakeAppClock(now = Instant.parse("2026-05-05T05:05:00Z"))
    private val repository = FakeAlarmRepository()
    private val scheduler = RecordingScheduler()
    private val ringtones = FakeRingtoneRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AlarmEditorViewModel(repository, scheduler, clock, ringtones)

    @Test
    fun `a new editor starts blank`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()

        assertThat(vm.state.value.loading).isFalse()
        assertThat(vm.state.value.draft.isNew).isTrue()
        assertThat(vm.state.value.draft.hour).isEqualTo(AlarmDraft.DEFAULT_HOUR)
    }

    @Test
    fun `an existing alarm is loaded into the draft`() = runTest {
        repository.alarms[4] = alarm(id = 4, name = "Work", hour = 7, minute = 15)
        val vm = viewModel()

        vm.load(4)
        advanceUntilIdle()

        assertThat(vm.state.value.draft.name).isEqualTo("Work")
        assertThat(vm.state.value.draft.hour).isEqualTo(7)
        assertThat(vm.state.value.draft.isNew).isFalse()
    }

    @Test
    fun `loading twice does not reload`() = runTest {
        repository.alarms[4] = alarm(id = 4, name = "Work")
        val vm = viewModel()
        vm.load(4)
        advanceUntilIdle()
        vm.setName("Changed")

        vm.load(4)
        advanceUntilIdle()

        assertThat(vm.state.value.draft.name).isEqualTo("Changed")
    }

    @Test
    fun `field edits land in the draft`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()

        vm.setTime(9, 45)
        vm.setName("Run")
        vm.setEmoji("🏃")
        vm.toggleDay(DayOfWeek.TUESDAY)
        vm.setVibrate(true)
        vm.setVolume(0.25f)
        vm.setCrescendoEnabled(true)
        vm.setCrescendoSeconds(45)
        vm.setSnoozeEnabled(false)
        vm.setSnoozeMinutes(12)
        vm.setSnoozeLimit(1)
        vm.setWakeUpCheckMinutes(10)
        vm.setMemo("get up")
        vm.setTtsEnabled(true)
        vm.setBackupSoundEnabled(true)
        vm.setTimeAnnouncementEnabled(true)
        vm.setRingtone("file:///a.mp3")

        val draft = vm.state.value.draft
        assertThat(draft.hour).isEqualTo(9)
        assertThat(draft.minute).isEqualTo(45)
        assertThat(draft.name).isEqualTo("Run")
        assertThat(draft.emoji).isEqualTo("🏃")
        assertThat(draft.days).containsExactly(DayOfWeek.TUESDAY)
        assertThat(draft.vibrate).isTrue()
        assertThat(draft.volumeRatio).isEqualTo(0.25f)
        assertThat(draft.crescendoEnabled).isTrue()
        assertThat(draft.crescendoSeconds).isEqualTo(45)
        assertThat(draft.snoozeEnabled).isFalse()
        assertThat(draft.snoozeMinutes).isEqualTo(12)
        assertThat(draft.snoozeLimit).isEqualTo(1)
        assertThat(draft.wakeUpCheckMinutes).isEqualTo(10)
        assertThat(draft.memo).isEqualTo("get up")
        assertThat(draft.ttsEnabled).isTrue()
        assertThat(draft.backupSoundEnabled).isTrue()
        assertThat(draft.timeAnnouncementEnabled).isTrue()
        assertThat(draft.ringtoneUri).isEqualTo("file:///a.mp3")
    }

    @Test
    fun `numeric setters clamp to their domain range`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()

        vm.setVolume(4f)
        vm.setSnoozeMinutes(0)
        vm.setSnoozeLimit(99)
        vm.setCrescendoSeconds(0)
        vm.setWakeUpCheckMinutes(-5)

        val draft = vm.state.value.draft
        assertThat(draft.volumeRatio).isEqualTo(Alarm.VOLUME_MAX)
        assertThat(draft.snoozeMinutes).isEqualTo(AlarmEditorViewModel.MIN_SNOOZE)
        assertThat(draft.snoozeLimit).isEqualTo(AlarmEditorViewModel.MAX_SNOOZE_LIMIT)
        assertThat(draft.crescendoSeconds).isEqualTo(AlarmEditorViewModel.MIN_CRESCENDO)
        assertThat(draft.wakeUpCheckMinutes).isEqualTo(0)
    }

    @Test
    fun `every day and one shot presets replace the day set`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()

        vm.setEveryDay()
        assertThat(vm.state.value.draft.days).hasSize(7)

        vm.setOneShot()
        assertThat(vm.state.value.draft.days).isEmpty()
    }

    @Test
    fun `missions can be added, replaced and removed`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()

        vm.addMission(MissionType.MATH)
        vm.addMission(MissionType.SHAKE)
        assertThat(vm.state.value.draft.missions).hasSize(2)

        vm.replaceMission(1, Mission.Shake(count = 30))
        assertThat((vm.state.value.draft.missions[1] as Mission.Shake).count).isEqualTo(30)

        vm.removeMission(0)
        assertThat(vm.state.value.draft.missions).hasSize(1)
        assertThat(vm.state.value.draft.missions.single()).isInstanceOf(Mission.Shake::class.java)
    }

    @Test
    fun `the mission chain stops at five`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()

        repeat(7) { vm.addMission(MissionType.MATH) }

        assertThat(vm.state.value.draft.missions).hasSize(Mission.MAX_PER_ALARM)
    }

    @Test
    fun `out of range mission edits are ignored`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()
        vm.addMission(MissionType.MATH)

        vm.removeMission(5)
        vm.replaceMission(5, Mission.Shake())

        assertThat(vm.state.value.draft.missions).hasSize(1)
        assertThat(vm.state.value.draft.missions.single()).isInstanceOf(Mission.Math::class.java)
    }

    @Test
    fun `a freshly loaded draft has no changes until a field is edited`() = runTest {
        repository.alarms[4] = alarm(id = 4, name = "Work")
        val vm = viewModel()
        vm.load(4)
        advanceUntilIdle()

        assertThat(vm.state.value.hasChanges).isFalse()

        vm.setName("Gym")

        assertThat(vm.state.value.hasChanges).isTrue()

        vm.setName("Work")

        assertThat(vm.state.value.hasChanges).isFalse()
    }

    @Test
    fun `the preview rolls a time that already passed today over to tomorrow`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()

        vm.setTime(5, 0)

        assertThat(vm.state.value.nextRing).isEqualTo(Instant.parse("2026-05-06T05:00:00Z"))
    }

    @Test
    fun `a disabled draft has no ring preview`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()

        vm.setEnabled(false)

        assertThat(vm.state.value.nextRing).isNull()
    }

    @Test
    fun `saving a new alarm inserts it and re-arms`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()
        vm.setTime(6, 0)
        vm.setName("Early")

        vm.save()
        advanceUntilIdle()

        assertThat(repository.upserts).hasSize(1)
        assertThat(repository.upserts.single().name).isEqualTo("Early")
        assertThat(repository.upserts.single().createdAt).isEqualTo(clock.now())
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.ADD_ALARM)
        assertThat(vm.state.value.finished).isEqualTo(EditorOutcome.Saved)
    }

    @Test
    fun `a refused arming is reported instead of leaving silently`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()
        vm.setTime(6, 0)
        vm.setName("Early")
        scheduler.next = alarm(id = 1, name = "Early")
        scheduler.armed = false

        vm.save()
        advanceUntilIdle()

        assertThat(vm.state.value.notArmed).isTrue()

        vm.notArmedShown()

        assertThat(vm.state.value.notArmed).isFalse()
        assertThat(vm.state.value.finished).isEqualTo(EditorOutcome.Saved)
    }

    @Test
    fun `saving an existing alarm modifies it`() = runTest {
        repository.alarms[9] = alarm(id = 9, name = "Old")
        val vm = viewModel()
        vm.load(9)
        advanceUntilIdle()
        vm.setName("New")

        vm.save()
        advanceUntilIdle()

        assertThat(repository.upserts.single().id).isEqualTo(9)
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.MODIFY_ALARM)
    }

    @Test
    fun `an incomplete mission blocks the save`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()
        vm.addMission(MissionType.BARCODE)

        vm.save()
        advanceUntilIdle()

        assertThat(vm.state.value.errors).containsExactly(DraftError.IncompleteMission)
        assertThat(repository.upserts).isEmpty()
        assertThat(scheduler.reasons).isEmpty()
    }

    @Test
    fun `fixing the draft clears the error`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()
        vm.addMission(MissionType.BARCODE)
        vm.save()
        advanceUntilIdle()

        vm.replaceMission(0, Mission.Barcode(barcodeValue = "12345"))

        assertThat(vm.state.value.errors).isEmpty()
    }

    @Test
    fun `deleting removes the alarm and re-arms`() = runTest {
        repository.alarms[2] = alarm(id = 2, name = "Gone")
        val vm = viewModel()
        vm.load(2)
        advanceUntilIdle()

        vm.delete()
        advanceUntilIdle()

        assertThat(repository.deleted).containsExactly(2L)
        assertThat(scheduler.reasons).containsExactly(RescheduleReason.DELETE_ALARM)
        assertThat(vm.state.value.finished).isEqualTo(EditorOutcome.Deleted)
    }

    @Test
    fun `a new draft cannot be deleted`() = runTest {
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()

        vm.delete()
        advanceUntilIdle()

        assertThat(repository.deleted).isEmpty()
    }

    @Test
    fun `the sound title follows the draft and the catalogue`() = runTest {
        ringtones.rows.value =
            listOf(CatalogRingtone(1, RingtoneKind.MUSIC, "file:///a.mp3", "My song", clock.now()))
        val vm = viewModel()
        vm.load(Alarm.NEW_ID)
        advanceUntilIdle()

        vm.setRingtone("file:///a.mp3")
        advanceUntilIdle()

        assertThat(vm.soundTitle.value).isEqualTo("My song")
    }

    private fun alarm(id: Long, name: String, hour: Int = 7, minute: Int = 0) =
        Alarm(
            id = id,
            name = name,
            hour = hour,
            minute = minute,
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )

    private class FakeAlarmRepository : AlarmRepository {
        val alarms = mutableMapOf<Long, Alarm>()
        val upserts = mutableListOf<Alarm>()
        val deleted = mutableListOf<Long>()

        override fun observeAlarms(): Flow<List<Alarm>> = flowOf(alarms.values.toList())

        override fun observeNextAlarm(): Flow<Alarm?> = flowOf(null)

        override suspend fun getAlarm(id: Long): Alarm? = alarms[id]

        override suspend fun upsertAlarm(alarm: Alarm): Long {
            upserts += alarm
            return alarm.id
        }

        override suspend fun setSkipped(id: Long, skipped: Boolean) = Unit

        override suspend fun setEnabled(id: Long, enabled: Boolean) = Unit

        override suspend fun deleteAlarm(id: Long) {
            deleted += id
        }
    }

    private class FakeRingtoneRepository : RingtoneRepository {
        val rows = MutableStateFlow<List<CatalogRingtone>>(emptyList())

        override fun observeAll(): Flow<List<CatalogRingtone>> = rows

        override suspend fun getById(id: Long): CatalogRingtone? =
            rows.value.firstOrNull { it.id == id }

        override suspend fun getAll(): List<CatalogRingtone> = rows.value

        override suspend fun insert(kind: RingtoneKind, uri: String, title: String): Long = 0L

        override suspend fun delete(id: Long) = Unit

        override suspend fun seedBundled(entries: List<Pair<String, String>>) = Unit
    }

    private class RecordingScheduler : AlarmScheduler {
        val reasons = mutableListOf<RescheduleReason>()
        var next: Alarm? = null
        var armed: Boolean = true

        override suspend fun rearm(reason: RescheduleReason): SchedulingResult {
            reasons += reason
            return SchedulingResult(next = next, nextAt = null, missed = emptyList(), armed = armed)
        }

        override fun armWakeUpCheck(alarmId: Long, at: Instant) = Unit

        override fun cancelWakeUpCheck() = Unit

        override fun armSnooze(
            alarmId: Long,
            scheduleId: String?,
            at: Instant,
            count: Int,
        ) = Unit

        override fun cancelSnooze() = Unit

        override fun armRetryRing(delivery: RingDelivery, at: Instant) = Unit
    }
}
