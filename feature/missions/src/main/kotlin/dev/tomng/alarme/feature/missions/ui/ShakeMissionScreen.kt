package dev.tomng.alarme.feature.missions.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.tomng.alarme.core.designsystem.theme.Spacing
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

    LaunchedEffect(count) { if (count > 0) callbacks.onTimerReset() }

    if (count >= goal) {
        // Fire once from composition: the counter is monotonic until reset.
        LaunchedEffect(tracker) { callbacks.onComplete() }
    }

    MissionStageScaffold(
        title = stringResource(R.string.mission_shake_title),
        testTag = "mission:shake",
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            MissionProgressCard(
                progress = count.toFloat() / goal,
                valueText = stringResource(R.string.mission_shake_progress, count, goal),
                caption = stringResource(R.string.mission_shake_instruction, goal),
                valueTag = "mission:shake:count",
                progressTag = "mission:shake:progress",
            )
            Spacer(Modifier.weight(1f))
        }
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
