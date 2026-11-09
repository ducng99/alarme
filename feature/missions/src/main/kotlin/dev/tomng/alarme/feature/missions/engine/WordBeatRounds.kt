package dev.tomng.alarme.feature.missions.engine

import dev.tomng.alarme.core.model.mission.WordBeatLanguage

/**
 * The sentences to read aloud per round. Until the editor's sentence catalogue lands, each language
 * has a small built-in set; the sequence repeats shuffled to cover the round count.
 */
object WordBeatRounds {

    fun build(language: WordBeatLanguage, rounds: Int, seed: Long): List<String> {
        val sentences = SENTENCES.getValue(language)
        val rotation = mutableListOf<String>()
        while (rotation.size < rounds) {
            rotation += sentences.shuffled(kotlin.random.Random(seed + rotation.size))
        }
        return rotation.take(rounds.coerceAtLeast(1))
    }

    private val SENTENCES: Map<WordBeatLanguage, List<String>> =
        mapOf(
            WordBeatLanguage.ENGLISH to
                listOf(
                    "The sun is already up and so should you be",
                    "I am awake and ready for the day",
                    "One more minute is a lie I will not tell",
                    "Good morning, the world is waiting",
                ),
            WordBeatLanguage.KOREAN to
                listOf(
                    "좋은 아침이에요 일어날 시간이에요",
                    "나는 오늘 하루를 시작할 준비가 되었어요",
                    "더 잘 시간이 아니에요 일어나세요",
                ),
            WordBeatLanguage.JAPANESE to
                listOf(
                    "おはようございます 起きる時間です",
                    "今日も一日頑張りましょう",
                    "もう寝る時間ではありません",
                ),
        )
}
