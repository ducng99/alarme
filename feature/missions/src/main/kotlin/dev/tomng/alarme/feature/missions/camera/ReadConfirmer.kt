package dev.tomng.alarme.feature.missions.camera

/**
 * Accepts a barcode value only after [requiredReads] consecutive frames decode to it. A single
 * noisy frame can decode to a wrong value, but the same wrong value on several frames in a row is
 * very unlikely.
 */
class ReadConfirmer(private val requiredReads: Int = 3) {
    private var candidate: String? = null
    private var count = 0

    fun offer(value: String?): String? {
        if (value == null) return null
        if (value == candidate) {
            count++
        } else {
            candidate = value
            count = 1
        }
        return if (count >= requiredReads) value else null
    }
}
