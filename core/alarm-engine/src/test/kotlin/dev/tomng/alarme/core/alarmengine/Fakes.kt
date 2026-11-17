package dev.tomng.alarme.core.alarmengine

import dev.tomng.alarme.core.alarmengine.dispatch.RingDispatcher
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.alarmengine.scheduling.ScheduleIdStore
import dev.tomng.alarme.core.alarmengine.scheduling.SchedulingResult
import dev.tomng.alarme.core.alarmengine.scheduling.TriggerType
import dev.tomng.alarme.core.data.repository.AlarmEvent
import dev.tomng.alarme.core.data.repository.AlarmRepository
import dev.tomng.alarme.core.data.repository.RingEventRepository
import dev.tomng.alarme.core.data.repository.SettingsRepository
import dev.tomng.alarme.core.data.settings.AppSettings
import dev.tomng.alarme.core.data.settings.AudioOutputPreference
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.AutoDismissOption
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory alarm repository: [nextOccurrence] is whatever the test sets (as persisted). */
class FakeAlarmRepository : AlarmRepository {
    val alarms = MutableStateFlow<List<Alarm>>(emptyList())

    override fun observeAlarms(): Flow<List<Alarm>> = alarms

    override fun observeNextAlarm(): Flow<Alarm?> = alarms.map { list ->
        list
            .filter { it.enabled && !it.isSkipped }
            .minByOrNull { it.nextOccurrence ?: Instant.DISTANT_FUTURE }
    }

    override suspend fun getAlarm(id: Long): Alarm? = alarms.value.firstOrNull { it.id == id }

    override suspend fun upsertAlarm(alarm: Alarm): Long {
        alarms.value = alarms.value.filterNot { it.id == alarm.id } + alarm
        return alarm.id
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

class FakeSettingsRepository(private val id: String = "dev1") : SettingsRepository {
    override val settings = MutableStateFlow(AppSettings())

    override suspend fun setMute(enabled: Boolean, limit: Int) = Unit

    override suspend fun setMuteDuringMission(enabled: Boolean) = Unit

    override suspend fun setAutoDismiss(option: AutoDismissOption) = Unit

    override suspend fun setAudioOutput(output: AudioOutputPreference) = Unit

    override suspend fun setPreventPowerOff(enabled: Boolean) = Unit

    override suspend fun setPreventUninstall(enabled: Boolean) = Unit

    override suspend fun setDefaultRingtoneId(id: Long?) = Unit

    override suspend fun setOnboardingDone(done: Boolean) = Unit

    override suspend fun deviceId(): String = id
}

class FakeRingEventRepository : RingEventRepository {
    val events = mutableListOf<AlarmEvent>()

    override suspend fun logEvent(event: AlarmEvent): Long {
        events += event
        return events.size.toLong()
    }

    override fun observeRecent(limit: Int): Flow<List<AlarmEvent>> =
        MutableStateFlow(events.take(limit))

    override suspend fun purgeOlderThan(before: Instant) = Unit
}

class InMemoryScheduleIdStore : ScheduleIdStore {
    var value: String? = null

    override fun save(id: String) {
        value = id
    }

    override fun load(): String? = value

    override fun clear() {
        value = null
    }
}

class FakeAlarmScheduler : dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler {
    val reasons = mutableListOf<RescheduleReason>()
    var nextResult = SchedulingResult(next = null, nextAt = null, missed = emptyList())
    var lastWakeUpCheck: Pair<Long, Instant>? = null
    var wakeUpChecksCancelled = 0
    var retryDelivery: dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery? = null
    var retryArmedAt: Instant? = null
    val snoozes = mutableListOf<Triple<Long, Instant, Int>>()
    var snoozesCancelled = 0

    override suspend fun rearm(reason: RescheduleReason): SchedulingResult {
        reasons += reason
        return nextResult
    }

    override fun armWakeUpCheck(alarmId: Long, at: Instant) {
        lastWakeUpCheck = alarmId to at
    }

    override fun cancelWakeUpCheck() {
        wakeUpChecksCancelled++
    }

    override fun armSnooze(alarmId: Long, scheduleId: String?, at: Instant, count: Int) {
        snoozes += Triple(alarmId, at, count)
    }

    override fun cancelSnooze() {
        snoozesCancelled++
    }

    override fun armRetryRing(
        delivery: dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery,
        at: Instant,
    ) {
        retryDelivery = delivery
        retryArmedAt = at
    }
}

class InMemorySnoozeStore : dev.tomng.alarme.core.alarmengine.scheduling.SnoozeStore {
    private var record: dev.tomng.alarme.core.alarmengine.scheduling.SnoozeRecord? = null

    override fun save(record: dev.tomng.alarme.core.alarmengine.scheduling.SnoozeRecord) {
        this.record = record
    }

    override fun load(): dev.tomng.alarme.core.alarmengine.scheduling.SnoozeRecord? = record

    override fun clear() {
        record = null
    }
}

class RecordingRingDispatcher : RingDispatcher {
    val deliveries = mutableListOf<dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery>()

    override fun dispatch(delivery: dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery) {
        deliveries += delivery
    }
}

val TEST_TRIGGER: TriggerType
    get() = TriggerType.NORMAL
