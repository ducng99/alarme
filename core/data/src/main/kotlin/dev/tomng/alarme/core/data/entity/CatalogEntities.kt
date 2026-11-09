package dev.tomng.alarme.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Registered barcode target for the Barcode mission. */
@Entity(tableName = "Barcode")
data class BarcodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val name: String,
    val code: String,
    val createdAt: Long,
)

/** Local sound catalogue entry (bundled, device, imported file, recorded, silent, random). */
@Entity(tableName = "Ringtone")
data class RingtoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val uri: String,
    val title: String,
    val createdAt: Long,
)

/** Ring history entry; trimmed of the reference's telemetry columns (installationId/ownerId). */
@Entity(tableName = "AlarmEvent")
data class AlarmEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val event: String,
    val ringRootId: String,
    val timestamp: Long,
    val triggerType: String,
    val missionsJson: String?,
    val alarmName: String,
    val alarmEmoji: String,
    val scheduleId: String,
)
