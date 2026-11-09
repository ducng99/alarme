package dev.tomng.alarme.core.sensors

import com.google.common.truth.Truth.assertThat
import dev.tomng.alarme.core.sensors.shake.ShakeSensorTracker
import dev.tomng.alarme.core.sensors.source.SensorSampleSink
import dev.tomng.alarme.core.sensors.source.SensorSource
import dev.tomng.alarme.core.sensors.squat.CoverSensorTracker
import dev.tomng.alarme.core.sensors.step.StepSensorTracker
import org.junit.Test

/**
 * The trackers glue a source stream to the pure cores and expose live progress; fakes stand in for
 * the platform sensors.
 */
class SensorTrackerTest {

    private class FakeSource : SensorSource {
        var sink: SensorSampleSink? = null
            private set

        var startCount = 0
            private set

        var stopCount = 0
            private set

        override fun start(sink: SensorSampleSink) {
            startCount++
            this.sink = sink
        }

        override fun stop() {
            stopCount++
            sink = null
        }

        fun emit(vararg values: Float, timestampNanos: Long) {
            sink?.onSample(values.copyOf(), timestampNanos)
        }
    }

    private val g = 9.80665f

    @Test
    fun `the shake tracker counts shakes from the source`() {
        val source = FakeSource()
        val tracker = ShakeSensorTracker(source)
        tracker.start()
        assertThat(source.startCount).isEqualTo(1)

        source.emit(0f, 0f, 3f * g, timestampNanos = 1_000_000_000)
        assertThat(tracker.count.value).isEqualTo(1)

        source.emit(0f, 0f, 3f * g, timestampNanos = 1_000_200_000) // inside debounce
        assertThat(tracker.count.value).isEqualTo(1)

        source.emit(0f, 0f, 3f * g, timestampNanos = 1_400_000_000)
        assertThat(tracker.count.value).isEqualTo(2)
    }

    @Test
    fun `shake tracker start is idempotent and stop detaches`() {
        val source = FakeSource()
        val tracker = ShakeSensorTracker(source)
        tracker.start()
        tracker.start()
        assertThat(source.startCount).isEqualTo(1)

        tracker.stop()
        assertThat(source.stopCount).isEqualTo(1)

        source.emit(0f, 0f, 3f * g, timestampNanos = 5_000_000_000) // detached: no effect
        assertThat(tracker.count.value).isEqualTo(0)
    }

    @Test
    fun `the squat tracker counts reps from cover transitions`() {
        val proximity = FakeSource()
        val tracker = CoverSensorTracker.forTest(proximity = proximity, light = null, goal = 2)
        assertThat(tracker.availability).isEqualTo(CoverSensorTracker.Availability.AVAILABLE)
        tracker.start()

        proximity.emit(0f, timestampNanos = 1_000_000_000) // covered
        assertThat(tracker.progress.value.covered).isTrue()
        proximity.emit(5f, timestampNanos = 1_800_000_000) // uncovered -> rep 1
        assertThat(tracker.progress.value.reps).isEqualTo(1)

        proximity.emit(0f, timestampNanos = 2_000_000_000)
        proximity.emit(5f, timestampNanos = 2_800_000_000) // rep 2
        assertThat(tracker.progress.value.reps).isEqualTo(2)
        assertThat(tracker.progress.value.done).isTrue()
    }

    @Test
    fun `a squat cheat flags the progress`() {
        val proximity = FakeSource()
        val tracker = CoverSensorTracker.forTest(proximity = proximity, light = null, goal = 2)
        tracker.start()

        proximity.emit(0f, timestampNanos = 1_000_000_000)
        proximity.emit(5f, timestampNanos = 1_100_000_000) // too fast: cheat
        val progress = tracker.progress.value
        assertThat(progress.cheat).isTrue()
        assertThat(progress.reps).isEqualTo(0)

        // A clean rep clears the flag.
        proximity.emit(0f, timestampNanos = 2_000_000_000)
        proximity.emit(5f, timestampNanos = 2_700_000_000)
        assertThat(tracker.progress.value.cheat).isFalse()
        assertThat(tracker.progress.value.reps).isEqualTo(1)
    }

    @Test
    fun `without any sensor the squat mission is unavailable and never starts`() {
        val tracker = CoverSensorTracker.forTest(proximity = null, light = null, goal = 2)
        assertThat(tracker.availability).isEqualTo(CoverSensorTracker.Availability.NO_SENSOR)
        tracker.start()
        assertThat(tracker.started).isFalse()
    }

    @Test
    fun `light samples are ignored when proximity exists`() {
        val proximity = FakeSource()
        val light = FakeSource()
        val tracker = CoverSensorTracker.forTest(proximity = proximity, light = light, goal = 2)
        tracker.start()

        light.emit(0.0f, timestampNanos = 1_000_000_000) // would be "covered"
        assertThat(tracker.progress.value.covered).isFalse()

        proximity.emit(0f, timestampNanos = 1_200_000_000)
        assertThat(tracker.progress.value.covered).isTrue()
    }

    @Test
    fun `the step tracker counts the delta from the first event`() {
        val source = FakeSource()
        val tracker = StepSensorTracker.forTest(source, stepCounterAvailable = true, goal = 10)
        assertThat(tracker.stepCounterAvailable).isTrue()
        tracker.start()

        source.emit(5_000f, timestampNanos = 1_000_000_000) // baseline only
        assertThat(tracker.progress.value.count).isEqualTo(0)

        source.emit(5_004f, timestampNanos = 2_000_000_000)
        assertThat(tracker.progress.value.count).isEqualTo(4)

        source.emit(5_010f, timestampNanos = 3_000_000_000)
        assertThat(tracker.progress.value.count).isEqualTo(10)
        assertThat(tracker.progress.value.done).isTrue()
    }

    @Test
    fun `without a step counter the tracker never starts`() {
        val tracker = StepSensorTracker.forTest(null, stepCounterAvailable = false, goal = 10)
        tracker.start()
        assertThat(tracker.started).isFalse()
    }
}
