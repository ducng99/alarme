package dev.tomng.alarme.core.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.tomng.alarme.core.data.db.AlarmeDatabase
import dev.tomng.alarme.core.data.entity.AlarmInfoEntity
import dev.tomng.alarme.core.data.entity.BarcodeEntity
import dev.tomng.alarme.core.data.entity.MissionInfoEntity
import dev.tomng.alarme.core.data.entity.MissionTypingPhraseRefEntity
import dev.tomng.alarme.core.data.entity.MissionWordBeatRoundSetRefEntity
import dev.tomng.alarme.core.data.entity.MissionWordBeatVolumeRefEntity
import dev.tomng.alarme.core.data.entity.RingtoneEntity
import dev.tomng.alarme.core.data.entity.TypingCustomPhraseEntity
import dev.tomng.alarme.core.model.mission.MissionType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmDaoTest {

    private lateinit var db: AlarmeDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, AlarmeDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun alarmEntity(id: Long = 0, name: String = "n", hour: Int = 6, time: Long = 1000L) =
        AlarmInfoEntity(
            id = id,
            type = "NORMAL",
            emoji = "⏰",
            name = name,
            hour = hour,
            minutes = 30,
            time = time,
            enabled = true,
            daysOfWeek = 127,
            alert = null,
            volume = 0.8,
            vibrate = false,
            label = "",
            isSnoozeEnable = true,
            snoozeDuration = 5.0,
            snoozeLimitCount = 3,
            wakeUpCheck = 0,
            isCrescendoEnable = false,
            crescendoDuration = 15,
            backupSound = false,
            timePressure = false,
            labelReminder = false,
            isSkipped = false,
            createdAt = 0L,
            updatedAt = 0L,
        )

    @Test
    fun insertAndFetchRoundTrip() = runTest {
        val id = db.alarmDao().insert(alarmEntity(name = "first"))
        val loaded = db.alarmDao().getById(id)
        assertTrue(loaded != null)
        assertEquals("first", loaded!!.name)
    }

    @Test
    fun updateReplacesFieldsForSameId() = runTest {
        val id = db.alarmDao().insert(alarmEntity(name = "before"))
        db.alarmDao().update(alarmEntity(id = id, name = "after", hour = 7))
        val loaded = db.alarmDao().getById(id)!!
        assertEquals("after", loaded.name)
        assertEquals(7, loaded.hour)
        assertEquals(1, db.alarmDao().getAll().size)
    }

    @Test
    fun missionsCascadeOnAlarmDelete() = runTest {
        val alarmId = db.alarmDao().insert(alarmEntity())
        val missionId =
            db.missionDao()
                .insertAll(
                    listOf(
                        MissionInfoEntity(
                            alarmId = alarmId,
                            missionType = MissionType.SHAKE.name,
                            sortOrder = 0,
                            numOfRounds = 10,
                            difficulty = 0,
                            barcodeId = 0L,
                            barcodeQR = "",
                            photoPath = "",
                            typingType = "",
                            imageRecognitionObjectIds = "",
                            language = "",
                            timeLimitSeconds = 30,
                        ),
                    ),
                )
                .first()
        db.missionDao()
            .insertWordBeatRoundSets(
                listOf(
                    MissionWordBeatRoundSetRefEntity(missionId = missionId, roundSetId = "animals"),
                ),
            )
        db.missionDao()
            .insertWordBeatVolumes(
                listOf(MissionWordBeatVolumeRefEntity(missionId = missionId, volumeLevel = "HIGH")),
            )

        db.alarmDao().deleteById(alarmId)

        assertEquals(0, db.missionDao().countForAlarm(alarmId))
        assertEquals(0, db.missionDao().wordBeatRoundSetCount(missionId))
        assertEquals(0, db.missionDao().wordBeatVolumeCount(missionId))
    }

    @Test
    fun setSkippedPersists() = runTest {
        val id = db.alarmDao().insert(alarmEntity())
        db.alarmDao().setSkipped(id, skipped = true, updatedAt = 1L)
        assertEquals(true, db.alarmDao().getById(id)!!.isSkipped)
    }

    @Test
    fun observeAlarmsSortedByTime() = runTest {
        db.alarmDao().insert(alarmEntity(name = "late", time = 9000))
        db.alarmDao().insert(alarmEntity(name = "early", time = 1000))
        val alarms = db.alarmDao().getAll()
        assertEquals(listOf("early", "late"), alarms.map { it.name })
    }

    @Test
    fun observeNextAlarmPicksEarliestEnabledNotSkipped() = runTest {
        db.alarmDao()
            .insert(alarmEntity(name = "skipped", time = 500, hour = 5).copy(isSkipped = true))
        db.alarmDao().insert(alarmEntity(name = "disabled", time = 600).copy(enabled = false))
        db.alarmDao().insert(alarmEntity(name = "next", time = 700))
        db.alarmDao().insert(alarmEntity(name = "later", time = 800))
        assertEquals("next", db.alarmDao().getNext()!!.name)
    }

    @Test
    fun typingPhrasesAndRefsAndCatalogTables() = runTest {
        val alarmId = db.alarmDao().insert(alarmEntity())
        val missionId =
            db.missionDao()
                .insertAll(
                    listOf(
                        MissionInfoEntity(
                            alarmId = alarmId,
                            missionType = MissionType.TYPING.name,
                            sortOrder = 0,
                            numOfRounds = 1,
                            difficulty = 0,
                            barcodeId = 0L,
                            barcodeQR = "",
                            photoPath = "",
                            typingType = "CUSTOM",
                            imageRecognitionObjectIds = "",
                            language = "",
                            timeLimitSeconds = 60,
                        ),
                    ),
                )
                .first()
        db.typingPhraseDao()
            .insertPhrases(
                listOf(
                    TypingCustomPhraseEntity(
                        id = "p1",
                        phrase = "wake up",
                        type = "SHORT",
                        createdTime = 1L,
                    ),
                ),
            )
        db.missionDao()
            .insertTypingRefs(
                listOf(MissionTypingPhraseRefEntity(missionId = missionId, phraseId = "p1")),
            )
        val phrases = db.missionDao().typingPhrasesForMissions(listOf(missionId))
        assertEquals(listOf("wake up"), phrases.map { it.phrase })

        db.ringtoneDao()
            .insert(
                RingtoneEntity(type = "BUNDLED", uri = "res://x", title = "Radar", createdAt = 1L),
            )
        db.barcodeDao()
            .insert(BarcodeEntity(type = "EAN13", name = "shampoo", code = "4001", createdAt = 1L))
        assertEquals(1, db.ringtoneDao().getAll().size)
        assertEquals("shampoo", db.barcodeDao().getAll().first().name)
    }
}
