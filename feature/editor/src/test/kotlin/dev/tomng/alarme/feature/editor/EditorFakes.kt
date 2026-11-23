package dev.tomng.alarme.feature.editor

import dev.tomng.alarme.core.alarmengine.scheduling.AlarmScheduler
import dev.tomng.alarme.core.alarmengine.scheduling.RescheduleReason
import dev.tomng.alarme.core.alarmengine.scheduling.RingDelivery
import dev.tomng.alarme.core.alarmengine.scheduling.SchedulingResult
import dev.tomng.alarme.core.data.repository.AlarmRepository
import dev.tomng.alarme.core.data.repository.RingtoneRepository
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

internal class FakeAlarmRepository : AlarmRepository {
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

internal class FakeRingtoneRepository : RingtoneRepository {
    val rows = MutableStateFlow<List<CatalogRingtone>>(emptyList())

    override fun observeAll(): Flow<List<CatalogRingtone>> = rows

    override suspend fun getById(id: Long): CatalogRingtone? =
        rows.value.firstOrNull { it.id == id }

    override suspend fun getAll(): List<CatalogRingtone> = rows.value

    override suspend fun insert(kind: RingtoneKind, uri: String, title: String): Long = 0L

    override suspend fun delete(id: Long) = Unit

    override suspend fun seedBundled(entries: List<Pair<String, String>>) = Unit
}

internal class RecordingScheduler : AlarmScheduler {
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
