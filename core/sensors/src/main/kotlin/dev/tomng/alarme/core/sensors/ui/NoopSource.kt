package dev.tomng.alarme.core.sensors.ui

import dev.tomng.alarme.core.sensors.source.SensorSampleSink
import dev.tomng.alarme.core.sensors.source.SensorSource

/**
 * A source that never produces samples — the stand-in when the device lacks the sensor, so the
 * tracker can still run (and never complete) instead of crashing.
 */
object NoopSource : SensorSource {
    override fun start(sink: SensorSampleSink) = Unit

    override fun stop() = Unit
}
