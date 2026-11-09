package dev.tomng.alarme.core.data.repository

import dev.tomng.alarme.core.common.time.AppClock
import dev.tomng.alarme.core.data.dao.AlarmDao
import dev.tomng.alarme.core.data.dao.MissionDao
import dev.tomng.alarme.core.data.dao.TypingPhraseDao
import dev.tomng.alarme.core.data.mapper.AlarmEntityMapper
import dev.tomng.alarme.core.data.mapper.AlarmRowBundle
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.scheduling.NextOccurrenceCalculator
import kotlin.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Source of truth for alarms and their mission chains. */
interface AlarmRepository {
    fun observeAlarms(): Flow<List<Alarm>>

    fun observeNextAlarm(): Flow<Alarm?>

    suspend fun getAlarm(id: Long): Alarm?

    suspend fun upsertAlarm(alarm: Alarm): Long

    suspend fun setSkipped(id: Long, skipped: Boolean)

    suspend fun setEnabled(id: Long, enabled: Boolean)

    suspend fun deleteAlarm(id: Long)
}

class AlarmRepositoryImpl
@javax.inject.Inject
constructor(
    private val alarmDao: AlarmDao,
    private val missionDao: MissionDao,
    private val typingPhraseDao: TypingPhraseDao,
    private val clock: AppClock,
) : AlarmRepository {

    override fun observeAlarms(): Flow<List<Alarm>> =
        alarmDao.observeAll().map { infos -> infos.mapNotNull { loadBundle(it.id)?.toAlarm() } }

    /**
     * The next alarm is recomputed from the current time, never read from the stored `time` column:
     * that column is only refreshed on upsert, so it goes stale (and into the past) as occurrences
     * pass. A skipped alarm still has an occurrence (the one after the skipped day), so it stays in
     * the running like the scheduler does.
     */
    override fun observeNextAlarm(): Flow<Alarm?> =
        observeAlarms().map { alarms ->
            alarms
                .filter { it.enabled }
                .map { alarm ->
                    alarm to
                        NextOccurrenceCalculator.nextOccurrence(alarm, clock.now(), clock.zone())
                }
                .minByOrNull { (_, occurrence) -> occurrence ?: Instant.DISTANT_FUTURE }
                ?.let { (alarm, occurrence) -> alarm.copy(nextOccurrence = occurrence) }
        }

    override suspend fun getAlarm(id: Long): Alarm? = loadBundle(id)?.toAlarm()

    override suspend fun upsertAlarm(alarm: Alarm): Long {
        val nextOccurrence =
            NextOccurrenceCalculator.nextOccurrence(alarm, clock.now(), clock.zone())
        val write = AlarmEntityMapper.toEntity(alarm, nextOccurrence?.toEpochMilliseconds() ?: -1L)

        if (alarm.id == Alarm.NEW_ID) {
            val alarmId = alarmDao.insert(write.info)
            insertMissions(alarmId, write)
            return alarmId
        }

        val existing = alarmDao.getById(alarm.id)
        if (existing == null) {
            val alarmId = alarmDao.insert(write.info.copy(id = 0L))
            insertMissions(alarmId, write)
            return alarmId
        }
        alarmDao.update(write.info)
        missionDao.deleteForAlarm(alarm.id)
        insertMissions(alarm.id, write)
        return alarm.id
    }

    override suspend fun setSkipped(id: Long, skipped: Boolean) {
        alarmDao.setSkipped(id, skipped, clock.now().toEpochMilliseconds())
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        alarmDao.setEnabled(id, enabled, clock.now().toEpochMilliseconds())
    }

    override suspend fun deleteAlarm(id: Long) {
        alarmDao.deleteById(id)
    }

    private suspend fun insertMissions(
        alarmId: Long,
        write: dev.tomng.alarme.core.data.mapper.AlarmWriteBundle,
    ) {
        if (write.missions.isEmpty()) return
        val missionIds = missionDao.insertAll(write.missions.map { it.copy(alarmId = alarmId) })
        write.typingPhraseIdsByMissionIndex.forEach { (index, phraseIds) ->
            val missionId = missionIds.getOrNull(index) ?: return@forEach
            typingPhraseDao.insertPhrases(
                write.typingPhrases.filter { it.id in phraseIds },
            )
            missionDao.insertTypingRefs(
                phraseIds.map { phraseId ->
                    dev.tomng.alarme.core.data.entity.MissionTypingPhraseRefEntity(
                        missionId = missionId,
                        phraseId = phraseId,
                    )
                },
            )
        }
        write.wordBeatRoundSetIdsByMissionIndex.forEach { (index, roundSetIds) ->
            val missionId = missionIds.getOrNull(index) ?: return@forEach
            missionDao.insertWordBeatRoundSets(
                roundSetIds.map { roundSetId ->
                    dev.tomng.alarme.core.data.entity.MissionWordBeatRoundSetRefEntity(
                        missionId = missionId,
                        roundSetId = roundSetId,
                    )
                },
            )
        }
        write.wordBeatVolumeByMissionIndex.forEach { (index, volumeLevel) ->
            val missionId = missionIds.getOrNull(index) ?: return@forEach
            missionDao.insertWordBeatVolumes(
                listOf(
                    dev.tomng.alarme.core.data.entity.MissionWordBeatVolumeRefEntity(
                        missionId = missionId,
                        volumeLevel = volumeLevel,
                    ),
                ),
            )
        }
    }

    private suspend fun loadBundle(id: Long): AlarmRowBundle? {
        val info = alarmDao.getById(id) ?: return null
        val missions = missionDao.missionsForAlarm(id)
        val missionIds = missions.map { it.id }
        val typingPhrases =
            if (missionIds.isEmpty()) emptyList()
            else missionDao.typingPhrasesForMissions(missionIds)
        val roundSets =
            if (missionIds.isEmpty()) emptyList()
            else missionDao.wordBeatRoundSetsForMissions(missionIds)
        val volumes =
            if (missionIds.isEmpty()) emptyList()
            else missionDao.wordBeatVolumesForMissions(missionIds)
        return AlarmRowBundle(
            info = info,
            missions = missions,
            typingPhrases = typingPhrases,
            wordBeatRoundSets = roundSets.map { it.missionId to it.roundSetId },
            wordBeatVolumes = volumes.map { it.missionId to it.volumeLevel },
        )
    }

    private fun AlarmRowBundle.toAlarm(): Alarm = AlarmEntityMapper.toDomain(this)
}
