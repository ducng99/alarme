package dev.tomng.alarme.core.sensors.squat

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import dev.tomng.alarme.core.sensors.source.SensorManagerSource
import dev.tomng.alarme.core.sensors.source.SensorSampleSink
import dev.tomng.alarme.core.sensors.source.SensorSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Live squat progress: the covered signal (proximity primary, light fallback) feeds the rep
 * counter. [availability] tells the UI up front whether the device can run the mission at all —
 * without a sensor the only path is the emergency one.
 */
class CoverSensorTracker
private constructor(
    private val proximity: SensorSource?,
    private val light: SensorSource?,
    private val detector: CoverDetector,
    goal: Int,
) {
    enum class Availability {
        AVAILABLE,
        NO_SENSOR,
    }

    data class SquatProgress(
        val reps: Int,
        val covered: Boolean,
        val done: Boolean,
        val cheat: Boolean,
    )

    private val counter = SquatRepCounter(goal = goal)
    private var lastCovered = false

    private val _progress =
        MutableStateFlow(SquatProgress(reps = 0, covered = false, done = false, cheat = false))
    val progress: StateFlow<SquatProgress> = _progress

    val availability: Availability =
        if (detector.isAvailable) Availability.AVAILABLE else Availability.NO_SENSOR

    private val proximitySink = SensorSampleSink { values, timestampNanos ->
        if (values.isNotEmpty()) {
            onCovered(
                detector.onProximitySample(values[0]) == CoverDetector.Covered.YES,
                timestampNanos,
            )
        }
    }

    /** With a proximity sensor aboard, light samples are ignored (see [CoverDetector]). */
    private val lightSink = SensorSampleSink { values, timestampNanos ->
        if (values.isNotEmpty() && proximity == null) {
            onCovered(
                detector.onLightSample(values[0]) == CoverDetector.Covered.YES,
                timestampNanos,
            )
        }
    }

    var started: Boolean = false
        private set

    fun start() {
        if (started || availability == Availability.NO_SENSOR) return
        started = true
        proximity?.start(proximitySink)
        light?.start(lightSink)
    }

    fun stop() {
        if (!started) return
        started = false
        proximity?.stop()
        light?.stop()
    }

    private fun onCovered(covered: Boolean, timestampNanos: Long) {
        if (covered == lastCovered) return
        lastCovered = covered
        val verdict = counter.onCoverChanged(covered, timestampNanos / 1_000_000)
        _progress.value =
            SquatProgress(
                reps = counter.reps,
                covered = covered,
                done = counter.done,
                cheat = verdict == SquatRepCounter.Verdict.CHEAT,
            )
    }

    companion object {
        /**
         * Builds the production tracker from the device's sensors; missing sensors come back null.
         */
        fun fromContext(context: Context, goal: Int): CoverSensorTracker {
            val manager =
                context.getSystemService(SensorManager::class.java)
                    ?: return CoverSensorTracker(null, null, CoverDetector(false, false), goal)

            val proximity: SensorManagerSource? =
                manager.getDefaultSensor(Sensor.TYPE_PROXIMITY)?.let {
                    SensorManagerSource(manager, Sensor.TYPE_PROXIMITY)
                }
            val light: SensorManagerSource? =
                manager.getDefaultSensor(Sensor.TYPE_LIGHT)?.let {
                    SensorManagerSource(manager, Sensor.TYPE_LIGHT)
                }
            return CoverSensorTracker(
                proximity = proximity,
                light = light,
                detector =
                    CoverDetector(
                        lightSensorPresent = light != null,
                        proximitySensorPresent = proximity != null,
                    ),
                goal = goal,
            )
        }

        /** Test constructor with injected sources. */
        fun forTest(
            proximity: SensorSource?,
            light: SensorSource?,
            goal: Int,
        ): CoverSensorTracker =
            CoverSensorTracker(
                proximity = proximity,
                light = light,
                detector =
                    CoverDetector(
                        lightSensorPresent = light != null,
                        proximitySensorPresent = proximity != null,
                    ),
                goal = goal,
            )
    }
}
