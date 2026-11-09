package dev.tomng.alarme.core.sensors.step

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import dev.tomng.alarme.core.sensors.source.SensorManagerSource
import dev.tomng.alarme.core.sensors.source.SensorSampleSink
import dev.tomng.alarme.core.sensors.source.SensorSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live step progress from the hardware step counter (cumulative since boot; the delta from the
 * first event is what counts). [stepCounterAvailable] gates the mission — without the sensor the UI
 * offers the emergency path.
 */
class StepSensorTracker
private constructor(
    private val source: SensorSource?,
    stepCounterAvailable: Boolean,
    goal: Int,
) {
    data class StepProgress(
        val count: Int,
        val goal: Int,
        val done: Boolean,
    )

    private val tracker = StepTracker(goal = goal)

    private val _progress = MutableStateFlow(StepProgress(count = 0, goal = goal, done = false))
    val progress: StateFlow<StepProgress> = _progress

    val stepCounterAvailable: Boolean = stepCounterAvailable

    private val sink = SensorSampleSink { values, timestampNanos ->
        if (values.isNotEmpty() && tracker.onStepCount(values[0].toLong()) > 0) {
            _progress.value = StepProgress(tracker.count, goal, tracker.done)
        }
    }

    var started: Boolean = false
        private set

    fun start() {
        if (started || !stepCounterAvailable) return
        started = true
        source?.start(sink)
    }

    fun stop() {
        if (!started) return
        started = false
        source?.stop()
    }

    companion object {
        fun fromContext(context: Context, goal: Int): StepSensorTracker {
            val manager = context.getSystemService(SensorManager::class.java)
            val sensor = manager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            return StepSensorTracker(
                source = sensor?.let { SensorManagerSource(manager, Sensor.TYPE_STEP_COUNTER) },
                stepCounterAvailable = sensor != null,
                goal = goal,
            )
        }

        /** Test constructor with an injected source. */
        fun forTest(
            source: SensorSource?,
            stepCounterAvailable: Boolean,
            goal: Int,
        ): StepSensorTracker = StepSensorTracker(source, stepCounterAvailable, goal)
    }
}
