package dev.tomng.alarme.core.model.mission

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A wake-up mission. Every type in the chain must be completed before the alarm can be dismissed.
 */
@Serializable
sealed class Mission {
    abstract val timeLimitSeconds: Int

    abstract fun duplicatedWithDefaults(): Mission

    /** Answer generated arithmetic questions. Difficulty 0 is multiple choice, 1-6 use a keypad. */
    @Serializable
    data class Math(
        override val timeLimitSeconds: Int = 0,
        val rounds: Int = DEFAULT_ROUNDS,
        val difficulty: Int = DEFAULT_DIFFICULTY,
    ) : Mission() {
        override fun duplicatedWithDefaults(): Math = Math()

        companion object {
            const val DEFAULT_ROUNDS = 1
            const val DEFAULT_DIFFICULTY = 0
            const val MIN_DIFFICULTY = 0
            const val MAX_DIFFICULTY = 6
        }
    }

    /** Reproduce highlighted tiles on a difficulty² grid (difficulty 3..7 -> 9..49 tiles). */
    @Serializable
    data class Memory(
        override val timeLimitSeconds: Int = 0,
        val rounds: Int = DEFAULT_ROUNDS,
        val difficulty: Int = DEFAULT_DIFFICULTY,
    ) : Mission() {
        override fun duplicatedWithDefaults(): Memory = Memory()

        companion object {
            const val DEFAULT_ROUNDS = 1
            const val DEFAULT_DIFFICULTY = 3
            const val MIN_DIFFICULTY = 3
            const val MAX_DIFFICULTY = 7
        }
    }

    /** Shake the device [count] times. */
    @Serializable
    data class Shake(
        override val timeLimitSeconds: Int = 0,
        val count: Int = DEFAULT_COUNT,
    ) : Mission() {
        override fun duplicatedWithDefaults(): Shake = Shake()

        companion object {
            const val DEFAULT_COUNT = 10
        }
    }

    /** Walk [count] steps, tracked via the hardware step counter. */
    @Serializable
    data class Step(
        override val timeLimitSeconds: Int = 0,
        val count: Int = DEFAULT_COUNT,
    ) : Mission() {
        override fun duplicatedWithDefaults(): Step = Step()

        companion object {
            const val DEFAULT_COUNT = 10
        }
    }

    /** Do [count] squats, verified with the light sensor (cover/uncover). */
    @Serializable
    data class Squat(
        override val timeLimitSeconds: Int = 0,
        val count: Int = DEFAULT_COUNT,
    ) : Mission() {
        override fun duplicatedWithDefaults(): Squat = Squat()

        companion object {
            const val DEFAULT_COUNT = 10
        }
    }

    /** Type the shown sentences exactly (character-by-character prefix match). */
    @Serializable
    data class Typing(
        override val timeLimitSeconds: Int = 0,
        val rounds: Int = DEFAULT_ROUNDS,
        val typingSentences: List<TypingSentence> = emptyList(),
    ) : Mission() {
        override fun duplicatedWithDefaults(): Typing = Typing()

        companion object {
            const val DEFAULT_ROUNDS = 1
        }
    }

    /** Scan the registered barcode (exact match) to dismiss. */
    @Serializable
    data class Barcode(
        override val timeLimitSeconds: Int = 0,
        val barcodeId: Long = INVALID_BARCODE_ID,
        val barcodeValue: String = "",
    ) : Mission() {
        override fun duplicatedWithDefaults(): Barcode = Barcode()

        companion object {
            const val INVALID_BARCODE_ID = 0L
        }
    }

    /** Take a photo (honor-system capture, no content verification). */
    @Serializable
    data class Photo(
        override val timeLimitSeconds: Int = 0,
        val photoPath: String = "",
    ) : Mission() {
        override fun duplicatedWithDefaults(): Photo = Photo()
    }

    /** On-device ML Kit labelling must recognise one of [objects] with confidence >= 0.7. */
    @Serializable
    data class ImageRecognition(
        override val timeLimitSeconds: Int = 0,
        val objects: List<ImageRecognitionObject> = emptyList(),
    ) : Mission() {
        override fun duplicatedWithDefaults(): ImageRecognition = ImageRecognition()
    }

    /** Read the shown words aloud; on-device speech recognition checks >= 25 % token overlap. */
    @Serializable
    data class WordBeat(
        override val timeLimitSeconds: Int = 0,
        val rounds: Int = DEFAULT_ROUNDS,
        val language: WordBeatLanguage = WordBeatLanguage.ENGLISH,
        val selectedRoundSetIds: List<String> = emptyList(),
        val volumeLevel: WordBeatVolumeLevel = WordBeatVolumeLevel.MEDIUM,
    ) : Mission() {
        override fun duplicatedWithDefaults(): WordBeat = WordBeat()

        companion object {
            const val DEFAULT_ROUNDS = 1
        }
    }

    companion object {
        const val INVALID_ID = 0

        /** Hard cap on the mission chain length. */
        const val MAX_PER_ALARM = 5
    }
}

@Serializable
data class TypingSentence(
    val type: TypingSentenceType,
    val sentence: String,
)

@Serializable
enum class TypingSentenceType {
    @SerialName("short") SHORT,
    @SerialName("motivational") MOTIVATIONAL,
    @SerialName("affirmation") AFFIRMATION,
    @SerialName("love") LOVE,
    @SerialName("study") STUDY,
    @SerialName("difficult") DIFFICULT,
    @SerialName("custom") CUSTOM,
}

/**
 * Curated image targets. [mlKitLabel] is the on-device labeller's label string (matched
 * case-insensitively — label indices shift between model versions, names do not).
 */
@Serializable
enum class ImageRecognitionObject(val mlKitLabel: String) {
    SINK("sink"),
    BEARD("beard"),
    CUSHION("cushion"),
    SUNGLASSES("sunglasses"),
    BICYCLE("bicycle"),
    SMILE("smile"),
    BUS("bus"),
    SKY("sky"),
    TOE("toe"),
    TOILET("toilet"),
    STAIRS("stairs"),
    CHAIR("chair"),
    POSTER("poster"),
    CABINETRY("cabinetry"),
    CLOCK("clock"),
    DESK("desk"),
    CAT("cat"),
    JUICE("juice"),
    STUFFED_TOY("stuffed toy"),
    TILE("tile"),
    NAIL("nail"),
    COLA("cola"),
    CUTLERY("cutlery"),
    DANCE("dance"),
    MOUTH("mouth"),
    FIRE("fire"),
    BEDROOM("bedroom"),
    COUCH("couch"),
    HAT("hat"),
    COFFEE("coffee"),
    FOOD("food"),
    STANDING("standing"),
    FOOT("foot"),
    PILLOW("pillow"),
    EAR("ear"),
    EYELASH("eyelash"),
    HAIR("hair"),
    CURTAIN("curtain"),
    HEART("heart"),
    MUSCLE("muscle"),
    MUSICAL_INSTRUMENT("musical instrument"),
    MOBILE_PHONE("mobile phone"),
    PIANO("piano"),
    PLANT("plant"),
    PAPER("paper"),
    PET("pet"),
    LIPSTICK("lipstick"),
    SHOE("shoe"),
    TATTOO("tattoo"),
    CUP("cup"),
    HANDBAG("handbag"),
    BREAD("bread"),
    WALL("wall"),
    COUNTERTOP("countertop"),
    DOG("dog"),
    FLOWER("flower"),
    KITCHEN("kitchen"),
    VEGETABLE("vegetable"),
    JEANS("jeans"),
    FLOWERPOT("flowerpot"),
    DRAWER("drawer"),
    SHELF("shelf"),
    BANGS("bangs"),
    DENIM("denim"),
    UMBRELLA("umbrella"),
    BABY("baby"),
    GLASSES("glasses"),
    CAR("car"),
    HAND("hand"),
    MEAL("meal"),
    BAG("bag"),
}

/** WordBeat speech language with its STT locale and tokenisation mode. */
@Serializable
enum class WordBeatLanguage(val sttLocale: String, val tokenMode: TokenMode) {
    KOREAN("ko-KR", TokenMode.CHARACTER),
    ENGLISH("en-US", TokenMode.WORD),
    JAPANESE("ja-JP", TokenMode.CHARACTER),
}

@Serializable
enum class TokenMode {
    CHARACTER,
    WORD,
}

@Serializable
enum class WordBeatVolumeLevel {
    LOW,
    MEDIUM,
    HIGH,
}
