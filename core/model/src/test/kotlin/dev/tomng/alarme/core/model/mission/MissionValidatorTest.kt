package dev.tomng.alarme.core.model.mission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionValidatorTest {

    private fun validChain(): List<Mission> =
        listOf(
            Mission.Math(timeLimitSeconds = 60, rounds = 3, difficulty = 2),
            Mission.Shake(timeLimitSeconds = 30, count = 10),
        )

    @Test
    fun `empty chain is valid`() {
        assertTrue(MissionValidator.validate(emptyList()).isEmpty())
    }

    @Test
    fun `valid chain produces no errors`() {
        assertTrue(MissionValidator.validate(validChain()).isEmpty())
    }

    @Test
    fun `more than five missions is rejected`() {
        val chain = (1..6).map { Mission.Shake(timeLimitSeconds = 30, count = 10) }
        assertEquals(listOf(MissionError.TooManyMissions), MissionValidator.validate(chain))
    }

    @Test
    fun `five missions are accepted`() {
        val chain = (1..5).map { Mission.Shake(timeLimitSeconds = 30, count = 10) }
        assertTrue(MissionValidator.validate(chain).isEmpty())
    }

    @Test
    fun `math difficulty must be zero to six`() {
        assertTrue(MissionValidator.validate(listOf(Mission.Math(60, 3, 0))).isEmpty())
        assertTrue(MissionValidator.validate(listOf(Mission.Math(60, 3, 6))).isEmpty())
        assertTrue(
            MissionValidator.validate(listOf(Mission.Math(60, 3, 7)))
                .contains(MissionError.InvalidPayload),
        )
        assertTrue(
            MissionValidator.validate(listOf(Mission.Math(60, -1, 2)))
                .contains(MissionError.InvalidPayload),
        )
    }

    @Test
    fun `memory difficulty must be three to seven`() {
        assertTrue(MissionValidator.validate(listOf(Mission.Memory(60, 2, 3))).isEmpty())
        assertTrue(MissionValidator.validate(listOf(Mission.Memory(60, 2, 7))).isEmpty())
        assertTrue(
            MissionValidator.validate(listOf(Mission.Memory(60, 2, 2)))
                .contains(MissionError.InvalidPayload),
        )
        assertTrue(
            MissionValidator.validate(listOf(Mission.Memory(60, 2, 8)))
                .contains(MissionError.InvalidPayload),
        )
    }

    @Test
    fun `physical missions need a positive count`() {
        assertTrue(
            MissionValidator.validate(listOf(Mission.Shake(30, 0)))
                .contains(MissionError.InvalidPayload),
        )
        assertTrue(MissionValidator.validate(listOf(Mission.Step(30, 1))).isEmpty())
        assertTrue(
            MissionValidator.validate(listOf(Mission.Squat(30, -2)))
                .contains(MissionError.InvalidPayload),
        )
    }

    @Test
    fun `typing mission needs sentences`() {
        assertTrue(
            MissionValidator.validate(listOf(Mission.Typing(60, 1, emptyList()))).isNotEmpty(),
        )
        assertTrue(
            MissionValidator.validate(
                    listOf(
                        Mission.Typing(
                            timeLimitSeconds = 60,
                            rounds = 1,
                            typingSentences =
                                listOf(TypingSentence(TypingSentenceType.SHORT, "  ")),
                        ),
                    ),
                )
                .contains(MissionError.InvalidPayload),
        )
        assertTrue(
            MissionValidator.validate(
                    listOf(
                        Mission.Typing(
                            timeLimitSeconds = 60,
                            rounds = 1,
                            typingSentences =
                                listOf(TypingSentence(TypingSentenceType.CUSTOM, "rise and shine")),
                        ),
                    ),
                )
                .isEmpty(),
        )
    }

    @Test
    fun `barcode mission needs a value`() {
        assertTrue(
            MissionValidator.validate(
                    listOf(Mission.Barcode(30, barcodeId = 5, barcodeValue = "EAN-123")),
                )
                .isEmpty(),
        )
        assertTrue(
            MissionValidator.validate(
                    listOf(Mission.Barcode(30, barcodeId = 0, barcodeValue = "")),
                )
                .contains(MissionError.InvalidPayload),
        )
    }

    @Test
    fun `photo mission needs a path`() {
        assertTrue(MissionValidator.validate(listOf(Mission.Photo(30, "content://x"))).isEmpty())
        assertTrue(
            MissionValidator.validate(listOf(Mission.Photo(30, "")))
                .contains(MissionError.InvalidPayload),
        )
    }

    @Test
    fun `image recognition needs at least one target object`() {
        val cat = ImageRecognitionObject.CAT
        assertTrue(
            MissionValidator.validate(listOf(Mission.ImageRecognition(60, listOf(cat)))).isEmpty(),
        )
        assertTrue(
            MissionValidator.validate(listOf(Mission.ImageRecognition(60, emptyList())))
                .contains(MissionError.InvalidPayload),
        )
    }

    @Test
    fun `word beat needs at least one round set`() {
        val mission =
            Mission.WordBeat(
                timeLimitSeconds = 60,
                rounds = 3,
                language = WordBeatLanguage.ENGLISH,
                selectedRoundSetIds = listOf("animals"),
                volumeLevel = WordBeatVolumeLevel.MEDIUM,
            )
        assertTrue(MissionValidator.validate(listOf(mission)).isEmpty())
        assertTrue(
            MissionValidator.validate(listOf(mission.copy(selectedRoundSetIds = emptyList())))
                .contains(MissionError.InvalidPayload),
        )
    }

    @Test
    fun `time limit must be within bounds`() {
        assertTrue(
            MissionValidator.validate(listOf(Mission.Shake(-1, 5)))
                .contains(MissionError.InvalidPayload),
        )
        assertTrue(
            MissionValidator.validate(listOf(Mission.Shake(7201, 5)))
                .contains(MissionError.InvalidPayload),
        )
    }

    @Test
    fun `duplicated mission uses defaults`() {
        val original = Mission.Math(timeLimitSeconds = 120, rounds = 9, difficulty = 5)
        val copy = original.duplicatedWithDefaults()
        val defaults = copy as Mission.Math
        assertEquals(Mission.Math.DEFAULT_ROUNDS, defaults.rounds)
        assertEquals(Mission.Math.DEFAULT_DIFFICULTY, defaults.difficulty)
    }
}
