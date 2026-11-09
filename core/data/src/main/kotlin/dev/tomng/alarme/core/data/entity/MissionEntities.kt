package dev.tomng.alarme.core.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Flattened per-type mission row (one row per mission, type-specific columns unused for other
 * types). Deviations from the reference schema, documented for the trimmed domain: [sortOrder]
 * makes chain order explicit, [language] belongs to the WordBeat mission, [barcodeId] links to the
 * Barcode table.
 */
@Entity(
    tableName = "MissionInfo",
    foreignKeys =
        [
            ForeignKey(
                entity = AlarmInfoEntity::class,
                parentColumns = ["id"],
                childColumns = ["alarmId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("alarmId")],
)
data class MissionInfoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val alarmId: Long,
    val missionType: String,
    val sortOrder: Int,
    val numOfRounds: Int = 0,
    val difficulty: Int = 0,
    val barcodeId: Long = 0L,
    val barcodeQR: String = "",
    val photoPath: String = "",
    val typingType: String = "",
    val imageRecognitionObjectIds: String = "",
    val language: String = "",
    val timeLimitSeconds: Int = 0,
)

@Entity(
    tableName = "MissionTypingPhraseRef",
    foreignKeys =
        [
            ForeignKey(
                entity = MissionInfoEntity::class,
                parentColumns = ["id"],
                childColumns = ["missionId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("missionId")],
)
data class MissionTypingPhraseRefEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val missionId: Long,
    val phraseId: String,
)

@Entity(tableName = "TypingCustomPhrase")
data class TypingCustomPhraseEntity(
    @PrimaryKey val id: String,
    val phrase: String,
    val type: String,
    val createdTime: Long,
)

@Entity(
    tableName = "MissionWordBeatRoundSetRef",
    foreignKeys =
        [
            ForeignKey(
                entity = MissionInfoEntity::class,
                parentColumns = ["id"],
                childColumns = ["missionId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("missionId")],
)
data class MissionWordBeatRoundSetRefEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val missionId: Long,
    val roundSetId: String,
)

@Entity(
    tableName = "MissionWordBeatVolumeRef",
    foreignKeys =
        [
            ForeignKey(
                entity = MissionInfoEntity::class,
                parentColumns = ["id"],
                childColumns = ["missionId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("missionId")],
)
data class MissionWordBeatVolumeRefEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val missionId: Long,
    val volumeLevel: String,
)
