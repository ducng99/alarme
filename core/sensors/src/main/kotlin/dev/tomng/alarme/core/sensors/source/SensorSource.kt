package dev.tomng.alarme.core.sensors.source

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * A stream of raw sensor samples, so trackers stay testable: production wiring registers a real
 * [SensorEventListener] via [SensorManagerSource]; tests drive a fake.
 */
fun interface SensorSampleSink {
    fun onSample(values: FloatArray, timestampNanos: Long)
}

/** Start/stop lifecycle for a sample stream. [start] and [stop] are idempotent. */
interface SensorSource {
    fun start(sink: SensorSampleSink)

    fun stop()
}

/**
 * The production source: one sensor type from the platform [SensorManager]. [sensorAvailable]
 * reflects whether the device has the sensor at all — callers show the no-sensor escape hatch when
 * it does not.
 */
class SensorManagerSource(
    private val sensorManager: SensorManager,
    private val sensorType: Int,
    private val samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI,
) : SensorSource {
    private val listener =
        object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                currentSink?.onSample(event.values.clone(), event.timestamp)
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

    private var currentSink: SensorSampleSink? = null
    private var registered = false

    val sensorAvailable: Boolean
        get() = sensorManager.getDefaultSensor(sensorType) != null

    override fun start(sink: SensorSampleSink) {
        val sensor = sensorManager.getDefaultSensor(sensorType) ?: return
        currentSink = sink
        if (!registered) {
            sensorManager.registerListener(listener, sensor, samplingPeriodUs)
            registered = true
        }
    }

    override fun stop() {
        if (registered) {
            sensorManager.unregisterListener(listener)
            registered = false
        }
        currentSink = null
    }
}
