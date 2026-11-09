package dev.tomng.alarme.core.sensors.step

/**
 * Tracks progress toward the step goal from the cumulative hardware step counter. The first event
 * only captures the baseline; a value below the baseline means the sensor rebooted, so the tracker
 * rebases instead of counting backwards. Single-event jumps beyond [MAX_SINGLE_EVENT_DELTA] are
 * sensor glitches and are clamped.
 */
class StepTracker(private val goal: Int) {

    /** Steps counted toward the goal so far. */
    var count: Int = 0
        private set

    val done: Boolean
        get() = count >= goal

    private var baseline: Long? = null

    /**
     * Feeds one cumulative step count; returns how many new steps this event contributed (0 for the
     * baseline event, clamped for glitches, 0 once done).
     */
    fun onStepCount(steps: Long): Int {
        val base = baseline
        if (base == null || steps < base) {
            baseline = steps
            return 0
        }
        if (done) return 0
        val delta = (steps - base).toInt().coerceAtMost(MAX_SINGLE_EVENT_DELTA)
        val before = count
        count = (count + delta).coerceAtMost(goal)
        baseline = steps
        return count - before
    }

    fun reset() {
        count = 0
        baseline = null
    }

    companion object {
        /** Jumps this large in a single event are sensor glitches, not steps. */
        const val MAX_SINGLE_EVENT_DELTA = 20
    }
}
