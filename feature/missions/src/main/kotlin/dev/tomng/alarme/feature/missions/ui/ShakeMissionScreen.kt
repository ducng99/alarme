package dev.tomng.alarme.feature.missions.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.tomng.alarme.core.model.mission.Mission
import dev.tomng.alarme.core.sensors.shake.ShakeSensorTracker
import dev.tomng.alarme.core.sensors.source.SensorManagerSource
import dev.tomng.alarme.feature.missions.MissionStageCallbacks
import dev.tomng.alarme.feature.missions.R

/**
 * The shake mission: count hard accelerometer samples toward the goal. The tracker lifecycle is
 * bound to composition; production builds the platform sensor source, tests inject a fake.
 */
@Composable
fun ShakeMissionScreen(
    mission: Mission.Shake,
    callbacks: MissionStageCallbacks,
    trackerFactory: (Context) -> ShakeSensorTracker = ::defaultTracker,
) {
    val context = LocalContext.current
    val tracker = remember { trackerFactory(context) }
    val count by tracker.count.collectAsState()
    val goal = mission.count.coerceAtLeast(1)

    DisposableEffect(tracker) {
        tracker.start()
        onDispose { tracker.stop() }
    }

    if (count >= goal) {
        // Fire once from composition: the counter is monotonic until reset.
        androidx.compose.runtime.LaunchedEffect(tracker) { callbacks.onComplete() }
    }

    Column(
        modifier = Modifier.padding(16.dp).testTag("mission:shake"),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.mission_shake_instruction, goal),
            style = MaterialTheme.typography.headlineSmall,
        )
        LinearProgressIndicator(
            progress = { count.toFloat() / goal },
            modifier = Modifier.fillMaxWidth().height(12.dp).testTag("mission:shake:progress"),
        )
        Text(
            text = stringResource(R.string.mission_shake_progress, count, goal),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.testTag("mission:shake:count"),
        )
    }
}

private fun defaultTracker(context: Context): ShakeSensorTracker {
    val manager = context.getSystemService(SensorManager::class.java)
    val source =
        if (manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            SensorManagerSource(manager, Sensor.TYPE_ACCELEROMETER)
        } else {
            null
        }
    return ShakeSensorTracker(source ?: dev.tomng.alarme.core.sensors.ui.NoopSource)
}
