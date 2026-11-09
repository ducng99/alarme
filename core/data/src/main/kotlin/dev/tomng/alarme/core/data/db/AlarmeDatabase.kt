package dev.tomng.alarme.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.tomng.alarme.core.data.dao.AlarmDao
import dev.tomng.alarme.core.data.dao.AlarmEventDao
import dev.tomng.alarme.core.data.dao.BarcodeDao
import dev.tomng.alarme.core.data.dao.MissionDao
import dev.tomng.alarme.core.data.dao.RingtoneDao
import dev.tomng.alarme.core.data.dao.TypingPhraseDao
import dev.tomng.alarme.core.data.entity.AlarmEventEntity
import dev.tomng.alarme.core.data.entity.AlarmInfoEntity
import dev.tomng.alarme.core.data.entity.BarcodeEntity
import dev.tomng.alarme.core.data.entity.MissionInfoEntity
import dev.tomng.alarme.core.data.entity.MissionTypingPhraseRefEntity
import dev.tomng.alarme.core.data.entity.MissionWordBeatRoundSetRefEntity
import dev.tomng.alarme.core.data.entity.MissionWordBeatVolumeRefEntity
import dev.tomng.alarme.core.data.entity.RingtoneEntity
import dev.tomng.alarme.core.data.entity.TypingCustomPhraseEntity

/** Schema v1 — no migrations yet; they become explicit from v2 onwards. */
@Database(
    entities =
        [
            AlarmInfoEntity::class,
            MissionInfoEntity::class,
            MissionTypingPhraseRefEntity::class,
            TypingCustomPhraseEntity::class,
            MissionWordBeatRoundSetRefEntity::class,
            MissionWordBeatVolumeRefEntity::class,
            BarcodeEntity::class,
            RingtoneEntity::class,
            AlarmEventEntity::class,
        ],
    version = 1,
    exportSchema = false,
)
abstract class AlarmeDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    abstract fun missionDao(): MissionDao

    abstract fun typingPhraseDao(): TypingPhraseDao

    abstract fun barcodeDao(): BarcodeDao

    abstract fun ringtoneDao(): RingtoneDao

    abstract fun alarmEventDao(): AlarmEventDao

    companion object {
        const val NAME = "alarme.db"
    }
}
