package dev.tomng.alarme.core.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * The alarm record — the full column set minus wallpaper/weather columns (out of scope) and minus
 * `alertType` (video alarms excluded). [time] is the denormalised next-trigger epoch millis
 * (computed by the repository on save); [label] stores the memo text, [timePressure] the
 * time-announcement TTS flag, [labelReminder] the memo-TTS flag.
 */
@Entity(tableName = "AlarmInfo")
data class AlarmInfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val emoji: String,
    val name: String,
    val hour: Int,
    val minutes: Int,
    val time: Long,
    val enabled: Boolean,
    val daysOfWeek: Int,
    val alert: String?,
    val volume: Double,
    val vibrate: Boolean,
    val label: String,
    val isSnoozeEnable: Boolean,
    val snoozeDuration: Double,
    val snoozeLimitCount: Int,
    val wakeUpCheck: Int,
    val isCrescendoEnable: Boolean,
    val crescendoDuration: Int,
    val backupSound: Boolean,
    val backupSoundUri: String? = null,
    val timePressure: Boolean,
    val labelReminder: Boolean,
    val isSkipped: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)
