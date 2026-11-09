package dev.tomng.alarme.core.sensors.step

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The step rule: the hardware step counter is cumulative since boot; the tracker captures a
 * baseline at mission start and counts the delta toward the goal, rebasing on sensor resets.
 */
class StepTrackerTest {

    private val tracker = StepTracker(goal = 10)

    @Test
    fun `the first event only captures the baseline`() {
        assertThat(tracker.onStepCount(steps = 5_000)).isEqualTo(0)
        assertThat(tracker.count).isEqualTo(0)
    }

    @Test
    fun `the delta from the baseline counts toward the goal`() {
        tracker.onStepCount(steps = 5_000)
        assertThat(tracker.onStepCount(steps = 5_003)).isEqualTo(3)
        assertThat(tracker.onStepCount(steps = 5_010)).isEqualTo(7)
        assertThat(tracker.count).isEqualTo(10)
        assertThat(tracker.done).isTrue()
    }

    @Test
    fun `a counter reset rebases instead of counting backwards`() {
        tracker.onStepCount(steps = 5_000)
        assertThat(tracker.onStepCount(steps = 2)).isEqualTo(0) // sensor rebooted mid-mission
        assertThat(tracker.onStepCount(steps = 6)).isEqualTo(4)
        assertThat(tracker.count).isEqualTo(4)
    }

    @Test
    fun `an implausible burst in one event is clamped`() {
        val roomy = StepTracker(goal = 100)
        roomy.onStepCount(steps = 100)
        assertThat(roomy.onStepCount(steps = 100 + 500))
            .isEqualTo(StepTracker.MAX_SINGLE_EVENT_DELTA)
        assertThat(roomy.count).isEqualTo(StepTracker.MAX_SINGLE_EVENT_DELTA)
    }

    @Test
    fun `steps after the goal are ignored`() {
        tracker.onStepCount(steps = 0)
        tracker.onStepCount(steps = 10)
        assertThat(tracker.done).isTrue()
        assertThat(tracker.onStepCount(steps = 20)).isEqualTo(0)
        assertThat(tracker.count).isEqualTo(10)
    }

    @Test
    fun `reset clears the baseline`() {
        tracker.onStepCount(steps = 5_000)
        tracker.reset()
        assertThat(tracker.count).isEqualTo(0)
        assertThat(tracker.onStepCount(steps = 5_002)).isEqualTo(0) // new baseline again
        assertThat(tracker.onStepCount(steps = 5_003)).isEqualTo(1)
    }
}
