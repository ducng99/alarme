package dev.tomng.alarme.feature.editor

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.model.alarm.Alarm
import dev.tomng.alarme.core.model.alarm.AlarmType
import dev.tomng.alarme.core.model.alarm.DaysOfWeek
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.MissionType
import dev.tomng.alarme.core.model.mission.TypingSentence
import dev.tomng.alarme.core.model.mission.TypingSentenceType
import kotlin.time.Instant
import kotlinx.datetime.DayOfWeek
import org.junit.Test

class AlarmDraftTest {

    private val now = Instant.parse("2026-03-01T06:00:00Z")

    private fun alarm(
        missions: List<Mission> = emptyList(),
        days: DaysOfWeek = DaysOfWeek.EVERY_DAY,
    ) =
        Alarm(
            id = 7,
            type = AlarmType.NORMAL,
            emoji = "🌅",
            name = "Gym",
            hour = 6,
            minute = 30,
            daysOfWeek = days,
            enabled = false,
            missions = missions,
            ringtoneUri = "file:///sound.mp3",
            volumeRatio = 0.4f,
            vibrate = true,
            memoOption = dev.tomng.alarme.core.model.alarm.MemoOption("up!", ttsEnabled = true),
            snoozeOption =
                dev.tomng.alarme.core.model.alarm.SnoozeOption(
                    enabled = false,
                    minutes = 9,
                    limit = 2,
                ),
            crescendoOption =
                dev.tomng.alarme.core.model.alarm.CrescendoOption(enabled = true, seconds = 30),
            backupSoundEnabled = true,
            backupSoundUri = "file:///loud.mp3",
            timeAnnouncementEnabled = true,
            wakeUpCheck = dev.tomng.alarme.core.model.alarm.WakeUpCheck(minutes = 5),
            isSkipped = true,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
        )

    @Test
    fun `a draft round-trips every field of an alarm`() {
        val original = alarm(missions = listOf(Mission.Math(rounds = 3, difficulty = 2)))

        val restored = AlarmDraft.fromAlarm(original).toAlarm(now)

        assertThat(restored.copy(updatedAt = original.updatedAt)).isEqualTo(original)
    }

    @Test
    fun `a new draft is flagged as new and saves with the current time`() {
        val draft = AlarmDraft(name = "  Wake  ", hour = 8, minute = 5)

        val saved = draft.toAlarm(now)

        assertThat(draft.isNew).isTrue()
        assertThat(saved.id).isEqualTo(Alarm.NEW_ID)
        assertThat(saved.name).isEqualTo("Wake")
        assertThat(saved.createdAt).isEqualTo(now)
        assertThat(saved.updatedAt).isEqualTo(now)
    }

    @Test
    fun `an existing draft keeps its creation time`() {
        val created = Instant.parse("2025-12-24T00:00:00Z")

        val saved = AlarmDraft(id = 3, createdAt = created).toAlarm(now)

        assertThat(saved.createdAt).isEqualTo(created)
        assertThat(saved.updatedAt).isEqualTo(now)
    }

    @Test
    fun `days map to the bitmask and back`() {
        val draft = AlarmDraft(days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))

        val saved = draft.toAlarm(now)

        assertThat(saved.daysOfWeek).isEqualTo(DaysOfWeek.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
        assertThat(AlarmDraft.fromAlarm(saved).days)
            .containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
    }

    @Test
    fun `no days means a one-shot alarm`() {
        assertThat(AlarmDraft(days = emptySet()).toAlarm(now).daysOfWeek.isRepeating).isFalse()
    }

    @Test
    fun `a blank draft is saveable`() {
        assertThat(AlarmDraft().errors()).isEmpty()
    }

    @Test
    fun `a mission without its payload blocks saving`() {
        val draft = AlarmDraft(missions = listOf(Mission.Barcode()))

        assertThat(draft.errors()).containsExactly(DraftError.IncompleteMission)
    }

    @Test
    fun `a complete mission chain is saveable`() {
        val draft =
            AlarmDraft(
                missions =
                    listOf(
                        Mission.Math(),
                        Mission.Shake(count = 20),
                        Mission.Typing(
                            typingSentences =
                                listOf(TypingSentence(TypingSentenceType.SHORT, "up")),
                        ),
                    ),
            )

        assertThat(draft.errors()).isEmpty()
    }

    @Test
    fun `the mission cap is five`() {
        val five = List(5) { Mission.Math() }

        assertThat(AlarmDraft(missions = five).canAddMission).isFalse()
        assertThat(AlarmDraft(missions = five).errors()).isEmpty()
        assertThat(AlarmDraft(missions = five + Mission.Math()).errors())
            .containsExactly(DraftError.TooManyMissions)
    }

    @Test
    fun `every mission type starts with a payload the domain accepts`() {
        MissionType.entries.forEach { type ->
            val mission = MissionDefaults.of(type)
            val draft = AlarmDraft(missions = listOf(mission))
            val needsInput =
                mission is Mission.Barcode ||
                    mission is Mission.Photo ||
                    mission is Mission.ImageRecognition

            assertThat(draft.errors().isEmpty()).isEqualTo(!needsInput)
        }
    }

    @Test
    fun `typing defaults carry built-in sentences`() {
        val typing = MissionDefaults.of(MissionType.TYPING) as Mission.Typing

        assertThat(typing.typingSentences).isNotEmpty()
        assertThat(typing.typingSentences.first().type).isEqualTo(TypingSentenceType.SHORT)
    }

    @Test
    fun `word beat defaults carry round set ids for the chosen language`() {
        val wordBeat = MissionDefaults.of(MissionType.WORD_BEAT) as Mission.WordBeat

        assertThat(wordBeat.selectedRoundSetIds)
            .isEqualTo(MissionDefaults.roundSetIds(wordBeat.language))
    }

    @Test
    fun `the sentence catalogue has content for every preset tone`() {
        TypingSentenceType.entries
            .filter { it != TypingSentenceType.CUSTOM }
            .forEach { type -> assertThat(TypingSentenceCatalog.sentences(type)).isNotEmpty() }
        assertThat(TypingSentenceCatalog.sentences(TypingSentenceType.CUSTOM)).isEmpty()
    }
}
