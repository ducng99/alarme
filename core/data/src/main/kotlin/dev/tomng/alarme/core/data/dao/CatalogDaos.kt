package dev.tomng.alarme.core.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.tomng.alarme.core.data.entity.AlarmEventEntity
import dev.tomng.alarme.core.data.entity.BarcodeEntity
import dev.tomng.alarme.core.data.entity.RingtoneEntity
import dev.tomng.alarme.core.data.entity.TypingCustomPhraseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TypingPhraseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhrases(phrases: List<TypingCustomPhraseEntity>)

    @Query("SELECT * FROM TypingCustomPhrase ORDER BY createdTime ASC")
    suspend fun getAll(): List<TypingCustomPhraseEntity>

    @Query("DELETE FROM TypingCustomPhrase WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao
interface BarcodeDao {

    @Insert suspend fun insert(barcode: BarcodeEntity): Long

    @Query("SELECT * FROM Barcode ORDER BY createdAt ASC") suspend fun getAll(): List<BarcodeEntity>

    @Query("SELECT * FROM Barcode WHERE id = :id") suspend fun getById(id: Long): BarcodeEntity?

    @Query("DELETE FROM Barcode WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface RingtoneDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(ringtone: RingtoneEntity): Long

    @Query("SELECT * FROM Ringtone ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<RingtoneEntity>>

    @Query("SELECT * FROM Ringtone WHERE type = :type LIMIT 1")
    suspend fun firstByType(type: String): RingtoneEntity?

    @Query("SELECT * FROM Ringtone ORDER BY createdAt ASC")
    suspend fun getAll(): List<RingtoneEntity>

    @Query("SELECT * FROM Ringtone WHERE id = :id") suspend fun getById(id: Long): RingtoneEntity?

    @Query("SELECT * FROM Ringtone WHERE uri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): RingtoneEntity?

    @Query("DELETE FROM Ringtone WHERE id = :id") suspend fun deleteById(id: Long)
}

@Dao
interface AlarmEventDao {

    @Insert suspend fun insert(event: AlarmEventEntity): Long

    @Query("SELECT * FROM AlarmEvent ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AlarmEventEntity>>

    @Query("DELETE FROM AlarmEvent WHERE timestamp < :before")
    suspend fun purgeOlderThan(before: Long)
}
