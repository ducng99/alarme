package dev.tomng.alarme.core.data.repository

import dev.tomng.alarme.core.data.dao.AlarmEventDao
import dev.tomng.alarme.core.data.entity.AlarmEventEntity
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** One entry in the ring history (RING / DISMISS / SNOOZE / ...). */
data class AlarmEvent(
    val id: Long,
    val event: AlarmEventType,
    val ringRootId: String,
    val timestamp: Instant,
    val triggerType: String,
    val missionsJson: String?,
    val alarmName: String,
    val alarmEmoji: String,
    val scheduleId: String,
)

enum class AlarmEventType {
    RING,
    DISMISS,
    SNOOZE,
    SKIP,
    WAKE_UP_CHECK,
    AUTO_DISMISS,
    BLOCKED,
    MISSED,
}

/** Append-only ring history. */
interface RingEventRepository {
    suspend fun logEvent(event: AlarmEvent): Long

    fun observeRecent(limit: Int): Flow<List<AlarmEvent>>

    suspend fun purgeOlderThan(before: Instant)
}

class RingEventRepositoryImpl
@javax.inject.Inject
constructor(
    private val dao: AlarmEventDao,
) : RingEventRepository {

    override suspend fun logEvent(event: AlarmEvent): Long = dao.insert(event.toEntity())

    override fun observeRecent(limit: Int): Flow<List<AlarmEvent>> =
        dao.observeRecent(limit).map { events -> events.map { it.toDomain() } }

    override suspend fun purgeOlderThan(before: Instant) {
        dao.purgeOlderThan(before.toEpochMilliseconds())
    }
}

private fun AlarmEvent.toEntity() =
    AlarmEventEntity(
        id = if (id == 0L) 0L else id,
        event = event.name,
        ringRootId = ringRootId,
        timestamp = timestamp.toEpochMilliseconds(),
        triggerType = triggerType,
        missionsJson = missionsJson,
        alarmName = alarmName,
        alarmEmoji = alarmEmoji,
        scheduleId = scheduleId,
    )

private fun AlarmEventEntity.toDomain() =
    AlarmEvent(
        id = id,
        event = runCatching { AlarmEventType.valueOf(event) }.getOrDefault(AlarmEventType.RING),
        ringRootId = ringRootId,
        timestamp = Instant.fromEpochMilliseconds(timestamp),
        triggerType = triggerType,
        missionsJson = missionsJson,
        alarmName = alarmName,
        alarmEmoji = alarmEmoji,
        scheduleId = scheduleId,
    )
