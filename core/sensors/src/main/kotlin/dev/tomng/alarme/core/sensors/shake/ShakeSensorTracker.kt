package dev.tomng.alarme.core.sensors.shake

import dev.tomng.alarme.core.sensors.source.SensorSampleSink
import dev.tomng.alarme.core.sensors.source.SensorSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Wires an accelerometer stream into [ShakeDetectorCore] and exposes the live shake count. The UI
 * completes the mission when [count] reaches its goal.
 */
class ShakeSensorTracker(
    private val source: SensorSource,
    private val core: ShakeDetectorCore = ShakeDetectorCore(),
) {
    private val _count = MutableStateFlow(0)
    val count: StateFlow<Int> = _count

    private val sink = SensorSampleSink { values, timestampNanos ->
        if (
            values.size >= 3 &&
                core.onSample(values[0], values[1], values[2], timestampNanos / 1_000_000)
        ) {
            _count.value = core.count
        }
    }

    var started: Boolean = false
        private set

    fun start() {
        if (started) return
        started = true
        source.start(sink)
    }

    fun stop() {
        if (!started) return
        started = false
        source.stop()
    }

    /** Back to zero, e.g. when the stage restarts. */
    fun reset() {
        core.reset()
        _count.value = 0
    }
}
