package dev.tomng.alarme.core.data.mapper

import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.AlarmType
import dev.tomng.alarme.core.model.alarm.CrescendoOption
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import dev.tomng.alarme.core.model.alarm.MemoOption
import dev.tomng.alarme.core.model.alarm.SnoozeOption
import dev.tomng.alarme.core.model.alarm.WakeUpCheck
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.TypingSentence
import dev.tomng.alarme.core.model.mission.TypingSentenceType
import dev.tomng.alarme.core.model.mission.WordBeatLanguage
import dev.tomng.alarme.core.model.mission.WordBeatVolumeLevel
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmMapperTest {

    private fun roundTrip(
        alarm: dev.tomng.alarme.core.model.alarm.Alarm,
        nextTimeMillis: Long,
    ): dev.tomng.alarme.core.model.alarm.Alarm {
        val write = AlarmEntityMapper.toEntity(alarm, nextTimeMillis)
        val missionIds = write.missions.mapIndexed { index, _ -> (index + 1).toLong() }
        val bundle =
            dev.tomng.alarme.core.data.mapper.AlarmRowBundle.from(write, alarm.id, missionIds)
        return AlarmEntityMapper.toDomain(bundle)
    }

    private fun alarm(
        missions: List<Mission> = emptyList(),
        skip: Boolean = false,
        nextTimeMillis: Long = 0L,
    ) =
        Alarm(
            id = 7L,
            type = AlarmType.NORMAL,
            emoji = "☕",
            name = "coffee first",
            hour = 6,
            minute = 30,
            daysOfWeek = DaysOfWeek.ofSet(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)),
            enabled = true,
            missions = missions,
            ringtoneUri = "file:///ring.mp3",
            volumeRatio = 0.7f,
            vibrate = true,
            memoOption = MemoOption("stretch", ttsEnabled = true),
            snoozeOption = SnoozeOption(enabled = true, minutes = 9, limit = 4),
            crescendoOption = CrescendoOption(enabled = true, seconds = 45),
            backupSoundEnabled = true,
            backupSoundUri = "file:///loud.mp3",
            timeAnnouncementEnabled = true,
            wakeUpCheck = WakeUpCheck(5),
            isSkipped = skip,
            nextOccurrence =
                if (nextTimeMillis == 0L) null else Instant.fromEpochMilliseconds(nextTimeMillis),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-02-02T12:00:00Z"),
        )

    @Test
    fun `alarm fields round trip without missions`() {
        val restored = roundTrip(alarm(nextTimeMillis = 123_456), 123_456)
        assertEquals(alarm(nextTimeMillis = 123_456), restored)
    }

    @Test
    fun `every mission type round trips`() {
        val missions =
            listOf(
                Mission.Math(timeLimitSeconds = 60, rounds = 3, difficulty = 2),
                Mission.Memory(timeLimitSeconds = 60, rounds = 2, difficulty = 5),
                Mission.Shake(timeLimitSeconds = 30, count = 12),
                Mission.Step(timeLimitSeconds = 120, count = 20),
                Mission.Squat(timeLimitSeconds = 120, count = 8),
                Mission.Typing(
                    timeLimitSeconds = 90,
                    rounds = 2,
                    typingSentences =
                        listOf(
                            TypingSentence(TypingSentenceType.SHORT, "wake up"),
                            TypingSentence(TypingSentenceType.CUSTOM, "rise and shine now"),
                        ),
                ),
                Mission.Barcode(timeLimitSeconds = 60, barcodeId = 3L, barcodeValue = "EAN-4001"),
                Mission.Photo(timeLimitSeconds = 60, photoPath = "content://shot"),
                Mission.WordBeat(
                    timeLimitSeconds = 120,
                    rounds = 4,
                    language = WordBeatLanguage.KOREAN,
                    selectedRoundSetIds = listOf("animals", "numbers"),
                    volumeLevel = WordBeatVolumeLevel.HIGH,
                ),
            )
        val restored = roundTrip(alarm(missions, nextTimeMillis = 1L), 1)
        assertEquals(missions, restored.missions)
    }

    @Test
    fun `random mission count round trips`() {
        val random =
            alarm(listOf(Mission.Shake(count = 5), Mission.Math()), nextTimeMillis = 1L)
                .copy(randomMissionCount = 1)

        assertEquals(1, roundTrip(random, 1L).randomMissionCount)
    }

    @Test
    fun `quick one-shot alarm round trips`() {
        val quick =
            alarm(nextTimeMillis = 5L)
                .copy(id = 99L, type = AlarmType.QUICK, daysOfWeek = DaysOfWeek.NONE)
        val restored = roundTrip(quick, 5L)
        assertEquals(quick, restored)
    }

    @Test
    fun `unknown mission rows are dropped on restore`() {
        val write =
            AlarmEntityMapper.toEntity(alarm(listOf(Mission.Shake(count = 5))), nextTimeMillis = 0L)
        val bundle =
            dev.tomng.alarme.core.data.mapper.AlarmRowBundle.from(
                write,
                alarmId = 7L,
                missionIdsBySortOrder = listOf(1L),
            )
        val corrupted =
            bundle.copy(
                missions = bundle.missions.map { it.copy(missionType = "TAPTAP_LEGACY") },
            )
        assertEquals(emptyList<Mission>(), AlarmEntityMapper.toDomain(corrupted).missions)
    }

    @Test
    fun `time column is denormalised on the entity`() {
        val bundle = AlarmEntityMapper.toEntity(alarm(), nextTimeMillis = 42L)
        assertEquals(42L, bundle.info.time)
    }

    @Test
    fun `skip flag round trips`() {
        val restored = roundTrip(alarm(skip = true), 0L)
        assertEquals(true, restored.isSkipped)
    }
}
