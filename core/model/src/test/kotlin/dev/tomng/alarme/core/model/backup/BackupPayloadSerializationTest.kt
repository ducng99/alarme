package dev.tomng.alarme.core.model.backup

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.AlarmType
import dev.tomng.alarme.core.model.alarm.CrescendoOption
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import dev.tomng.alarme.core.model.alarm.MemoOption
import dev.tomng.alarme.core.model.alarm.SnoozeOption
import dev.tomng.alarme.core.model.alarm.WakeUpCheck
import dev.tomng.alarme.core.model.mission.ImageRecognitionObject
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.TypingSentence
import dev.tomng.alarme.core.model.mission.TypingSentenceType
import dev.tomng.alarme.core.model.mission.WordBeatLanguage
import dev.tomng.alarme.core.model.mission.WordBeatVolumeLevel
import dev.tomng.alarme.core.model.ringtone.CatalogRingtone
import dev.tomng.alarme.core.model.ringtone.RingtoneKind
import dev.tomng.alarme.core.model.ringtone.RingtoneUris
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.json.Json
import org.junit.Test

class BackupPayloadSerializationTest {

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val now = Instant.parse("2026-11-12T19:30:00Z")

    private fun alarm(id: Long, missions: List<Mission> = emptyList()) =
        Alarm(
            id = id,
            type = AlarmType.NORMAL,
            emoji = "🌅",
            name = "Morning run",
            hour = 6,
            minute = 45,
            daysOfWeek = DaysOfWeek.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
            enabled = true,
            missions = missions,
            ringtoneUri = "file:///data/user/0/dev.tomng.alarme/files/ringtone/music/run.mp3",
            volumeRatio = 0.8f,
            vibrate = true,
            memoOption = MemoOption(memo = "Get up!", ttsEnabled = true),
            snoozeOption = SnoozeOption(enabled = true, minutes = 7, limit = 2),
            crescendoOption = CrescendoOption(enabled = true, seconds = 30),
            backupSoundEnabled = true,
            timeAnnouncementEnabled = true,
            wakeUpCheck = WakeUpCheck(minutes = 3),
            isSkipped = true,
            createdAt = now,
            updatedAt = now,
        )

    @Test
    fun `payload round-trips through json`() {
        val payload =
            BackupPayload(
                exportedAt = now,
                alarms =
                    listOf(
                        alarm(1L),
                        alarm(
                            2L,
                            missions =
                                listOf(
                                    Mission.Math(rounds = 3, difficulty = 4),
                                    Mission.Memory(rounds = 2, difficulty = 5),
                                    Mission.Shake(count = 20),
                                    Mission.Step(count = 30),
                                    Mission.Squat(count = 12),
                                    Mission.Typing(
                                        rounds = 2,
                                        typingSentences =
                                            listOf(
                                                TypingSentence(
                                                    TypingSentenceType.CUSTOM,
                                                    "I am awake",
                                                ),
                                            ),
                                    ),
                                    Mission.Barcode(barcodeId = 7L, barcodeValue = "123456"),
                                    Mission.Photo(photoPath = "/tmp/x.jpg"),
                                    Mission.ImageRecognition(
                                        objects = listOf(ImageRecognitionObject.SINK),
                                    ),
                                    Mission.WordBeat(
                                        rounds = 2,
                                        language = WordBeatLanguage.JAPANESE,
                                        selectedRoundSetIds = listOf("a", "b"),
                                        volumeLevel = WordBeatVolumeLevel.HIGH,
                                    ),
                                ),
                        ),
                    ),
                ringtones =
                    listOf(
                        CatalogRingtone(
                            id = 3L,
                            kind = RingtoneKind.MUSIC,
                            uri =
                                "file:///data/user/0/dev.tomng.alarme/files/ringtone/music/run.mp3",
                            title = "Run",
                            createdAt = now,
                        ),
                    ),
                audio =
                    listOf(
                        BackupAudioEntry(
                            uri =
                                "file:///data/user/0/dev.tomng.alarme/files/ringtone/music/run.mp3",
                            entryName = "audio/run.mp3",
                        ),
                    ),
            )

        val decoded = json.decodeFromString<BackupPayload>(json.encodeToString(payload))

        assertThat(decoded).isEqualTo(payload)
        assertThat(decoded.alarms[1].missions).hasSize(10)
    }

    @Test
    fun `pseudo ringtone uris survive the round-trip`() {
        val payload =
            BackupPayload(
                exportedAt = now,
                alarms =
                    listOf(
                        alarm(1L).copy(ringtoneUri = RingtoneUris.SILENT),
                        alarm(2L).copy(ringtoneUri = RingtoneUris.RANDOM),
                        alarm(3L).copy(ringtoneUri = null),
                    ),
            )

        val decoded = json.decodeFromString<BackupPayload>(json.encodeToString(payload))

        assertThat(decoded.alarms.map { it.ringtoneUri })
            .containsExactly(RingtoneUris.SILENT, RingtoneUris.RANDOM, null)
            .inOrder()
    }

    @Test
    fun `next occurrence is not exported`() {
        val payload = BackupPayload(exportedAt = now, alarms = listOf(alarm(1L)))

        val text = json.encodeToString(payload)

        assertThat(text).doesNotContain("nextOccurrence")
    }

    @Test
    fun `unknown fields from a newer writer are ignored`() {
        val text =
            """
            {"formatVersion":1,"exportedAt":"2026-11-12T19:30:00Z","alarms":[],"ringtones":[],
             "audio":[],"somethingNew":{"a":1}}
            """
                .trimIndent()

        val decoded = json.decodeFromString<BackupPayload>(text)

        assertThat(decoded.alarms).isEmpty()
    }

    @Test
    fun `timestamps are iso strings`() {
        val text = json.encodeToString(BackupPayload(exportedAt = now, alarms = listOf(alarm(1L))))

        assertThat(text).contains("\"2026-11-12T19:30:00Z\"")
    }
}
