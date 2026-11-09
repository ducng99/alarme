package dev.tomng.alarme.core.sensors.squat

/**
 * Counts squat repetitions from the covered signal: one cover -> uncover transition is one rep.
 * Transitions quicker than [minRepIntervalMillis] are implausible (hand flapping over the sensor)
 * and count as a cheat instead.
 */
class SquatRepCounter(
    private val goal: Int,
    private val minRepIntervalMillis: Long = DEFAULT_MIN_REP_INTERVAL_MILLIS,
) {
    enum class Verdict {
        NONE,
        REP,
        CHEAT,
    }

    /** Reps completed so far. */
    var reps: Int = 0
        private set

    val done: Boolean
        get() = reps >= goal

    var lastVerdict: Verdict = Verdict.NONE
        private set

    private var covered = false
    private var coveredSinceMillis = -1L

    /** Feeds the latest covered state; returns the verdict for this transition. */
    fun onCoverChanged(covered: Boolean, timestampMillis: Long): Verdict {
        if (covered == this.covered) return Verdict.NONE
        val verdict =
            if (covered) {
                coveredSinceMillis = timestampMillis
                Verdict.NONE
            } else {
                if (
                    coveredSinceMillis >= 0 &&
                        timestampMillis - coveredSinceMillis >= minRepIntervalMillis
                ) {
                    Verdict.REP
                } else {
                    Verdict.CHEAT
                }
            }
        this.covered = covered
        lastVerdict = verdict
        if (verdict == Verdict.REP && reps < goal) reps++
        return verdict
    }

    companion object {
        const val DEFAULT_MIN_REP_INTERVAL_MILLIS = 500L
    }
}
