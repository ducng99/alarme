package dev.tomng.alarme.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import dev.tomng.alarme.core.data.entity.MissionInfoEntity
import dev.tomng.alarme.core.data.entity.MissionTypingPhraseRefEntity
import dev.tomng.alarme.core.data.entity.MissionWordBeatRoundSetRefEntity
import dev.tomng.alarme.core.data.entity.MissionWordBeatVolumeRefEntity

@Dao
interface MissionDao {

    @Insert suspend fun insertAll(missions: List<MissionInfoEntity>): List<Long>

    @Query("DELETE FROM MissionInfo WHERE alarmId = :alarmId")
    suspend fun deleteForAlarm(alarmId: Long)

    @Query("SELECT COUNT(*) FROM MissionInfo WHERE alarmId = :alarmId")
    suspend fun countForAlarm(alarmId: Long): Int

    @Query("SELECT * FROM MissionInfo WHERE alarmId = :alarmId ORDER BY sortOrder ASC")
    suspend fun missionsForAlarm(alarmId: Long): List<MissionInfoEntity>

    @Query("SELECT * FROM MissionInfo ORDER BY alarmId ASC, sortOrder ASC")
    suspend fun getAll(): List<MissionInfoEntity>

    @Insert suspend fun insertTypingRefs(refs: List<MissionTypingPhraseRefEntity>): List<Long>

    @Insert
    suspend fun insertWordBeatRoundSets(refs: List<MissionWordBeatRoundSetRefEntity>): List<Long>

    @Insert
    suspend fun insertWordBeatVolumes(refs: List<MissionWordBeatVolumeRefEntity>): List<Long>

    @Query(
        "SELECT r.missionId AS missionId, p.id AS phraseId, p.phrase AS phrase, p.type AS type " +
            "FROM MissionTypingPhraseRef r JOIN TypingCustomPhrase p ON p.id = r.phraseId " +
            "WHERE r.missionId IN (:missionIds) ORDER BY r.id ASC",
    )
    suspend fun typingPhrasesForMissions(missionIds: List<Long>): List<MissionPhraseJoinRow>

    @Query(
        "SELECT * FROM MissionWordBeatRoundSetRef WHERE missionId IN (:missionIds) ORDER BY id ASC",
    )
    suspend fun wordBeatRoundSetsForMissions(
        missionIds: List<Long>,
    ): List<MissionWordBeatRoundSetRefEntity>

    @Query("SELECT * FROM MissionWordBeatVolumeRef WHERE missionId IN (:missionIds)")
    suspend fun wordBeatVolumesForMissions(
        missionIds: List<Long>,
    ): List<MissionWordBeatVolumeRefEntity>

    @Query("SELECT COUNT(*) FROM MissionWordBeatRoundSetRef WHERE missionId = :missionId")
    suspend fun wordBeatRoundSetCount(missionId: Long): Int

    @Query("SELECT COUNT(*) FROM MissionWordBeatVolumeRef WHERE missionId = :missionId")
    suspend fun wordBeatVolumeCount(missionId: Long): Int
}

/** Joined row: typing phrase plus the mission it belongs to. */
data class MissionPhraseJoinRow(
    val missionId: Long,
    val phraseId: String,
    val phrase: String,
    val type: String,
)
