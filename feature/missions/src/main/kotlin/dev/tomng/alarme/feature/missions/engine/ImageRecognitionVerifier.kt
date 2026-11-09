package dev.tomng.alarme.feature.missions.engine

import dev.tomng.alarme.core.model.mission.ImageRecognitionObject

/**
 * The image-recognition rule: a configured object must be recognised with confidence >= 0.7. Labels
 * match by name, case-insensitive — the label *index* shifts between on-device model versions, the
 * name does not.
 */
object ImageRecognitionVerifier {

    const val MIN_CONFIDENCE = 0.7f

    data class RecognizedLabel(
        val text: String,
        val confidence: Float,
    )

    /** The first target object recognised above the threshold, or null. */
    fun match(
        labels: List<RecognizedLabel>,
        targets: Set<ImageRecognitionObject>,
    ): ImageRecognitionObject? {
        if (targets.isEmpty()) return null
        for (label in labels) {
            if (label.confidence < MIN_CONFIDENCE) continue
            val target = targets.firstOrNull { label.text.equals(it.mlKitLabel, ignoreCase = true) }
            if (target != null) return target
        }
        return null
    }
}
