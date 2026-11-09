package dev.tomng.alarme.core.data.repository

import androidx.room.withTransaction
import dev.tomng.alarme.core.data.dao.AlarmDao
import dev.tomng.alarme.core.data.dao.RingtoneDao
import dev.tomng.alarme.core.data.db.AlarmeDatabase
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.backup.BackupSnapshot
import dev.tomng.alarme.core.model.backup.RestoreResult
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Reads and writes the whole alarm list for backup. Restore is a *replace*: the current alarms are
 * deleted first, so importing a file always yields exactly the file's contents (ids are reassigned
 * by the database — nothing outside the alarm list refers to them).
 */
interface BackupRepository {

    suspend fun snapshot(): BackupSnapshot

    suspend fun restore(alarms: List<Alarm>, ringtones: List<CatalogRingtone>): RestoreResult
}

class BackupRepositoryImpl
@Inject
constructor(
    private val database: AlarmeDatabase,
    private val alarmRepository: AlarmRepository,
    private val ringtoneRepository: RingtoneRepository,
    private val alarmDao: AlarmDao,
    private val ringtoneDao: RingtoneDao,
) : BackupRepository {

    override suspend fun snapshot(): BackupSnapshot =
        BackupSnapshot(
            alarms = alarmRepository.observeAlarms().first(),
            ringtones = ringtoneRepository.getAll(),
        )

    override suspend fun restore(
        alarms: List<Alarm>,
        ringtones: List<CatalogRingtone>,
    ): RestoreResult {
        val restoredIds = database.withTransaction {
            alarmDao.deleteAll()
            val ids = alarms.map { alarmRepository.upsertAlarm(it.copy(id = Alarm.NEW_ID)) }
            var added = 0
            for (ringtone in ringtones) {
                if (ringtoneDao.findByUri(ringtone.uri) == null) {
                    ringtoneRepository.insert(ringtone.kind, ringtone.uri, ringtone.title)
                    added++
                }
            }
            ids to added
        }
        val (ids, added) = restoredIds
        return RestoreResult(
            alarms = ids.mapNotNull { alarmRepository.getAlarm(it) },
            ringtoneRowsAdded = added,
        )
    }
}
