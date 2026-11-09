package dev.tomng.alarme.core.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import dev.tomng.alarme.core.data.entity.AlarmInfoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {

    @Insert suspend fun insert(entity: AlarmInfoEntity): Long

    @Update suspend fun update(entity: AlarmInfoEntity)

    @Query("SELECT * FROM AlarmInfo WHERE id = :id") suspend fun getById(id: Long): AlarmInfoEntity?

    @Query("SELECT * FROM AlarmInfo ORDER BY time ASC, id ASC")
    suspend fun getAll(): List<AlarmInfoEntity>

    @Query("SELECT * FROM AlarmInfo ORDER BY time ASC, id ASC")
    fun observeAll(): Flow<List<AlarmInfoEntity>>

    @Query("SELECT * FROM AlarmInfo WHERE enabled = 1 AND isSkipped = 0 ORDER BY time ASC LIMIT 1")
    suspend fun getNext(): AlarmInfoEntity?

    @Query("SELECT * FROM AlarmInfo WHERE enabled = 1 AND isSkipped = 0 ORDER BY time ASC LIMIT 1")
    fun observeNext(): Flow<AlarmInfoEntity?>

    @Query("UPDATE AlarmInfo SET isSkipped = :skipped, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSkipped(id: Long, skipped: Boolean, updatedAt: Long)

    @Query("UPDATE AlarmInfo SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean, updatedAt: Long)

    @Delete suspend fun delete(entity: AlarmInfoEntity)

    @Query("DELETE FROM AlarmInfo WHERE id = :id") suspend fun deleteById(id: Long)

    /** Wipes the alarm list (missions cascade); used by backup restore. */
    @Query("DELETE FROM AlarmInfo") suspend fun deleteAll()
}
