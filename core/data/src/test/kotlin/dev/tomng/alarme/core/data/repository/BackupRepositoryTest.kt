package dev.tomng.alarme.core.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.common.time.FakeAppClock
import dev.tomng.alarme.core.data.db.AlarmeDatabase
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import dev.tomng.alarme.core.model.alarm.SnoozeOption
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.TypingSentence
import dev.tomng.alarme.core.model.mission.TypingSentenceType
import dev.tomng.alarme.core.model.mission.WordBeatLanguage
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupRepositoryTest {

    private lateinit var db: AlarmeDatabase
    private lateinit var alarms: AlarmRepository
    private lateinit var ringtones: RingtoneRepository
    private lateinit var backup: BackupRepository
    private lateinit var clock: FakeAppClock

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room.inMemoryDatabaseBuilder(context, AlarmeDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        clock = FakeAppClock(now = Instant.parse("2026-11-12T19:00:00Z"), zone = TimeZone.UTC)
        alarms = AlarmRepositoryImpl(db.alarmDao(), db.missionDao(), db.typingPhraseDao(), clock)
        ringtones = RingtoneRepositoryImpl(db.ringtoneDao())
        backup =
            BackupRepositoryImpl(
                database = db,
                alarmRepository = alarms,
                ringtoneRepository = ringtones,
                alarmDao = db.alarmDao(),
                ringtoneDao = db.ringtoneDao(),
            )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun alarm(
        hour: Int,
        minute: Int = 0,
        name: String = "alarm",
        missions: List<Mission> = emptyList(),
        ringtoneUri: String? = null,
    ) =
        Alarm(
            id = Alarm.NEW_ID,
            name = name,
            hour = hour,
            minute = minute,
            daysOfWeek = DaysOfWeek.EVERY_DAY,
            missions = missions,
            ringtoneUri = ringtoneUri,
            snoozeOption = SnoozeOption(enabled = true, minutes = 9, limit = 1),
            createdAt = clock.now(),
            updatedAt = clock.now(),
        )

    @Test
    fun `snapshot reads every alarm with its missions`() = runTest {
        alarms.upsertAlarm(
            alarm(
                hour = 6,
                missions =
                    listOf(
                        Mission.Math(rounds = 2, difficulty = 3),
                        Mission.Typing(
                            rounds = 1,
                            typingSentences =
                                listOf(TypingSentence(TypingSentenceType.CUSTOM, "wake up")),
                        ),
                        Mission.WordBeat(
                            rounds = 2,
                            language = WordBeatLanguage.KOREAN,
                            selectedRoundSetIds = listOf("r1"),
                        ),
                    ),
            ),
        )
        alarms.upsertAlarm(alarm(hour = 7, name = "second"))

        val snapshot = backup.snapshot()

        assertThat(snapshot.alarms).hasSize(2)
        assertThat(snapshot.alarms[0].missions).hasSize(3)
        assertThat(snapshot.alarms[0].missions[1])
            .isEqualTo(
                Mission.Typing(
                    rounds = 1,
                    typingSentences = listOf(TypingSentence(TypingSentenceType.CUSTOM, "wake up")),
                ),
            )
        assertThat(snapshot.alarms[0].snoozeOption).isEqualTo(SnoozeOption(true, 9, 1))
    }

    @Test
    fun `restore replaces the alarm list and reassigns ids`() = runTest {
        val originalId = alarms.upsertAlarm(alarm(hour = 6, name = "kept"))
        val snapshot = backup.snapshot()

        // Wipe, then restore.
        db.alarmDao().deleteAll()
        assertThat(alarms.observeAlarms().first()).isEmpty()

        val result = backup.restore(snapshot.alarms, snapshot.ringtones)

        assertThat(result.alarms).hasSize(1)
        assertThat(result.alarms[0].name).isEqualTo("kept")
        assertThat(result.alarms[0].id).isNotEqualTo(originalId)
        assertThat(alarms.observeAlarms().first().map { it.name }).containsExactly("kept")
    }

    @Test
    fun `restore keeps mission chains intact`() = runTest {
        alarms.upsertAlarm(
            alarm(
                hour = 6,
                missions =
                    listOf(
                        Mission.Math(rounds = 3, difficulty = 5),
                        Mission.Barcode(barcodeId = 4L, barcodeValue = "999"),
                    ),
            ),
        )
        val snapshot = backup.snapshot()

        val result = backup.restore(snapshot.alarms, snapshot.ringtones)

        assertThat(result.alarms[0].missions)
            .containsExactly(
                Mission.Math(rounds = 3, difficulty = 5),
                Mission.Barcode(barcodeId = 4L, barcodeValue = "999"),
            )
            .inOrder()
    }

    @Test
    fun `restore adds missing ringtone rows and skips existing ones`() = runTest {
        val existingId = ringtones.insert(RingtoneKind.MUSIC, "file:///a.mp3", "A")
        val payload =
            listOf(
                CatalogRingtone(existingId, RingtoneKind.MUSIC, "file:///a.mp3", "A", clock.now()),
                CatalogRingtone(0L, RingtoneKind.RECORD, "file:///b.m4a", "B", clock.now()),
            )

        val result = backup.restore(emptyList(), payload)

        assertThat(result.ringtoneRowsAdded).isEqualTo(1)
        assertThat(ringtones.getAll().map { it.uri })
            .containsExactly("file:///a.mp3", "file:///b.m4a")
    }

    @Test
    fun `restore of an empty payload clears the list`() = runTest {
        alarms.upsertAlarm(alarm(hour = 6))

        val result = backup.restore(emptyList(), emptyList())

        assertThat(result.alarms).isEmpty()
        assertThat(alarms.observeAlarms().first()).isEmpty()
    }
}
