package dev.tomng.alarme.feature.editor

import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.model.mission.MissionType
import dev.tomng.alarme.core.model.mission.TypingSentence
import dev.tomng.alarme.core.model.mission.TypingSentenceType
import dev.tomng.alarme.core.model.mission.WordBeatLanguage
import dev.tomng.alarme.core.model.mission.WordBeatVolumeLevel

/** Built-in sentences offered by the typing mission editor, grouped by tone. */
object TypingSentenceCatalog {

    fun sentences(type: TypingSentenceType): List<String> =
        when (type) {
            TypingSentenceType.SHORT ->
                listOf("I am awake", "Get up now", "No more sleep", "Time to move")
            TypingSentenceType.MOTIVATIONAL ->
                listOf(
                    "The day will not wait for me",
                    "I chose this morning last night",
                    "Small steps now, a better day later",
                )
            TypingSentenceType.AFFIRMATION ->
                listOf(
                    "I am capable of hard things",
                    "I keep the promises I make to myself",
                    "I am rested enough to begin",
                )
            TypingSentenceType.LOVE ->
                listOf(
                    "Someone is glad I am awake",
                    "I am worth getting up for",
                    "Today I get to be kind",
                )
            TypingSentenceType.STUDY ->
                listOf(
                    "Review the notes before breakfast",
                    "One page now beats none later",
                    "Focus first, scroll later",
                )
            TypingSentenceType.DIFFICULT ->
                listOf(
                    "Sphinx of black quartz, judge my vow",
                    "Pack my box with five dozen liquor jugs",
                    "How vexingly quick daft zebras jump",
                )
            TypingSentenceType.CUSTOM -> emptyList()
        }

    fun pack(type: TypingSentenceType): List<TypingSentence> =
        sentences(type).map { TypingSentence(type = type, sentence = it) }
}

/**
 * The mission a freshly added slot starts with. Payloads that need user input (a scanned barcode, a
 * captured photo, chosen objects) start empty and are flagged as incomplete by the draft.
 */
object MissionDefaults {

    fun of(type: MissionType): Mission =
        when (type) {
            MissionType.MATH -> Mission.Math()
            MissionType.MEMORY -> Mission.Memory()
            MissionType.SHAKE -> Mission.Shake()
            MissionType.STEP -> Mission.Step()
            MissionType.SQUAT -> Mission.Squat()
            MissionType.TYPING ->
                Mission.Typing(
                    typingSentences = TypingSentenceCatalog.pack(TypingSentenceType.SHORT),
                )
            MissionType.BARCODE -> Mission.Barcode()
            MissionType.PHOTO -> Mission.Photo()
            MissionType.WORD_BEAT ->
                Mission.WordBeat(
                    language = WordBeatLanguage.ENGLISH,
                    selectedRoundSetIds = roundSetIds(WordBeatLanguage.ENGLISH),
                    volumeLevel = WordBeatVolumeLevel.MEDIUM,
                )
        }

    /** Opaque ids for the built-in round sets of a language; the engine reads the language. */
    fun roundSetIds(language: WordBeatLanguage): List<String> =
        listOf("${language.name.lowercase()}-default")
}
