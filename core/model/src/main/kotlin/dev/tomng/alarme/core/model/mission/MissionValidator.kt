package dev.tomng.alarme.core.model.mission

/**
 * Validates a mission chain before it is saved with an alarm. Domain-level invariants only;
 * storage-specific concerns (phrase ids, barcode rows) live in the data layer.
 */
object MissionValidator {

    fun validate(missions: List<Mission>): List<MissionError> {
        val errors = mutableListOf<MissionError>()
        if (missions.size > Mission.MAX_PER_ALARM) errors += MissionError.TooManyMissions
        if (missions.any { !it.hasValidPayload() }) errors += MissionError.InvalidPayload
        return errors
    }

    private fun Mission.hasValidPayload(): Boolean =
        timeLimitValid() &&
            when (this) {
                is Mission.Math ->
                    difficulty in Mission.Math.MIN_DIFFICULTY..Mission.Math.MAX_DIFFICULTY &&
                        rounds in 0..MAX_ROUNDS
                is Mission.Memory ->
                    difficulty in Mission.Memory.MIN_DIFFICULTY..Mission.Memory.MAX_DIFFICULTY &&
                        rounds in 0..MAX_ROUNDS
                is Mission.Shake -> count in 1..MAX_COUNT
                is Mission.Step -> count in 1..MAX_COUNT
                is Mission.Squat -> count in 1..MAX_COUNT
                is Mission.Typing ->
                    rounds in 1..MAX_ROUNDS &&
                        typingSentences.isNotEmpty() &&
                        typingSentences.all { it.sentence.isNotBlank() }
                is Mission.Barcode -> barcodeValue.isNotBlank()
                is Mission.Photo -> photoPath.isNotBlank()
                is Mission.ImageRecognition -> objects.isNotEmpty()
                is Mission.WordBeat -> selectedRoundSetIds.isNotEmpty()
            }
}

enum class MissionError {
    TooManyMissions,
    InvalidPayload,
}

private const val MAX_ROUNDS = 50
private const val MAX_COUNT = 10_000

/** Time limit in seconds; 0 means "no limit". Anything beyond an hour is a config mistake. */
private fun Mission.timeLimitValid(): Boolean = timeLimitSeconds in 0..MAX_TIME_LIMIT_SECONDS

private const val MAX_TIME_LIMIT_SECONDS = 3600
